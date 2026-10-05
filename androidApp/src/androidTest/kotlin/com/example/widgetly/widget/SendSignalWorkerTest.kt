package com.example.widgetly.widget

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.widgetly.connection.ConnectionType
import com.example.widgetly.connection.SendSignalResult
import com.example.widgetly.connection.WidgetlyFunctions
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
    var shouldThrowNetworkError: Boolean = false

    override suspend fun registerFcmToken(token: String, platform: String) = error("not used")
    override suspend fun createPairingCode(type: ConnectionType): String = error("not used")
    override suspend fun redeemPairingCode(code: String): String = error("not used")
    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult {
        if (shouldThrowNetworkError) throw IOException("network down")
        return nextSendSignalResult
    }
}

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

    // Instrumented tests run through D8/R8 dexing, which (unlike the JVM-only
    // shared:testAndroidHostTest suite) rejects spaces in method names below DEX
    // version 040 - see DdayWidgetConfigurationActivityTest for the same
    // camelCase convention already established for this reason.
    @Test
    fun returnsSuccessWithoutRetryWhenTheBackendReportsRateLimited() {
        val fake = FakeWidgetlyFunctions().apply { nextSendSignalResult = SendSignalResult(rateLimited = true) }
        val worker = TestListenableWorkerBuilder<SendSignalWorker>(context)
            .setWorkerFactory(fakeFactory(fake))
            .setInputData(
                androidx.work.workDataOf(
                    SendSignalWorker.KEY_CONNECTION_ID to "conn-1",
                    SendSignalWorker.KEY_PRESET to "heart",
                    SendSignalWorker.KEY_SIGNAL_ID to "sig-1",
                ),
            )
            .build()
        val result = worker.startWork().get()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun retriesOnANetworkFailure() {
        val fake = FakeWidgetlyFunctions().apply { shouldThrowNetworkError = true }
        val worker = TestListenableWorkerBuilder<SendSignalWorker>(context)
            .setWorkerFactory(fakeFactory(fake))
            .setInputData(
                androidx.work.workDataOf(
                    SendSignalWorker.KEY_CONNECTION_ID to "conn-1",
                    SendSignalWorker.KEY_PRESET to "heart",
                    SendSignalWorker.KEY_SIGNAL_ID to "sig-1",
                ),
            )
            .build()
        val result = worker.startWork().get()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
