package com.example.widgetly.widget

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.widgetly.connection.ConnectionType
import com.example.widgetly.connection.SendSignalResult
import com.example.widgetly.connection.WidgetlyFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

/**
 * androidApp has no access to shared's commonTest FakeWidgetlyFunctions (test
 * sources aren't shared across modules), so this is a separate, smaller fake
 * scoped to what SendSignalWorker actually calls.
 */
private class FakeWidgetlyFunctions : WidgetlyFunctions {
    var nextSendSignalResult: SendSignalResult = SendSignalResult(rateLimited = false)
    var exceptionToThrow: Exception? = null

    override suspend fun registerFcmToken(token: String, platform: String) = error("not used")
    override suspend fun createPairingCode(type: ConnectionType): String = error("not used")
    override suspend fun redeemPairingCode(code: String): String = error("not used")
    override suspend fun updateMascot(connectionId: String, mascotId: String) = error("not used")
    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult {
        exceptionToThrow?.let { throw it }
        return nextSendSignalResult
    }
}

/**
 * FirebaseFunctionsException's (String, Code, Any?) constructor is `public` at
 * the bytecode level (confirmed via javap) but `internal` per Kotlin's own
 * cross-module visibility rules, so the Kotlin compiler refuses a direct call
 * from this module. Reflection isn't subject to that compile-time-only check,
 * so this is the only way to construct a real instance of what the SDK
 * actually throws, to prove SendSignalWorker handles it correctly.
 */
private fun firebaseFunctionsException(code: FirebaseFunctionsException.Code): FirebaseFunctionsException =
    FirebaseFunctionsException::class.java
        .getConstructor(String::class.java, FirebaseFunctionsException.Code::class.java, Any::class.java)
        .newInstance(code.name, code, null)

private fun fakeFactory(fake: WidgetlyFunctions) = object : WorkerFactory() {
    override fun createWorker(
        appContext: android.content.Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SendSignalWorker::class.java.name) {
            SendSignalWorker(appContext, workerParameters, fake)
        } else {
            null
        }
}

class SendSignalWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun buildWorker(fake: FakeWidgetlyFunctions) =
        TestListenableWorkerBuilder<SendSignalWorker>(context)
            .setWorkerFactory(fakeFactory(fake))
            .setInputData(
                androidx.work.workDataOf(
                    SendSignalWorker.KEY_CONNECTION_ID to "conn-1",
                    SendSignalWorker.KEY_PRESET to "heart",
                    SendSignalWorker.KEY_SIGNAL_ID to "sig-1",
                ),
            )
            .build()

    // Instrumented tests run through D8/R8 dexing, which (unlike the JVM-only
    // shared:testAndroidHostTest suite) rejects spaces in method names below DEX
    // version 040 - see DdayWidgetConfigurationActivityTest for the same
    // camelCase convention already established for this reason.
    @Test
    fun returnsSuccessWithoutRetryWhenTheBackendReportsRateLimited() {
        val fake = FakeWidgetlyFunctions().apply { nextSendSignalResult = SendSignalResult(rateLimited = true) }
        val result = buildWorker(fake).startWork().get()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun retriesOnATransportLevelNetworkFailure() {
        val fake = FakeWidgetlyFunctions().apply { exceptionToThrow = IOException("network down") }
        val result = buildWorker(fake).startWork().get()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // This is what the real SDK actually throws for a callable failure (Fix
    // Round 1 - see task-4-report.md): the gitlive wrapper awaits the native
    // Android Task unwrapped, so a transient server/network condition surfaces
    // as FirebaseFunctionsException, not IOException. The IOException case above
    // is kept for the (rarer) transport-level failure that never reaches the
    // callable protocol at all.
    @Test
    fun retriesOnATransientFirebaseFunctionsException() {
        val fake = FakeWidgetlyFunctions().apply {
            exceptionToThrow = firebaseFunctionsException(FirebaseFunctionsException.Code.UNAVAILABLE)
        }
        val result = buildWorker(fake).startWork().get()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun failsWithoutRetryingOnANonTransientFirebaseFunctionsException() {
        val fake = FakeWidgetlyFunctions().apply {
            exceptionToThrow = firebaseFunctionsException(FirebaseFunctionsException.Code.INVALID_ARGUMENT)
        }
        val result = buildWorker(fake).startWork().get()
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
