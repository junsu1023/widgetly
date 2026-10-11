package com.example.widgetly.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.connection.WidgetlyFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Runs off the Glance action callback (see [SendSignalAction] in
 * ConnectionGlanceWidget.kt) - `ActionCallback.onAction()` has the same
 * background-execution limits as any other BroadcastReceiver-ish callback, so the
 * actual `sendSignal` network call happens here instead, where WorkManager's own
 * network constraint + retry/backoff policy apply.
 *
 * The `functions` default lets WorkManager's default reflection-based
 * WorkerFactory construct this with just (Context, WorkerParameters) in
 * production; tests supply a fake via the 3-arg constructor through a custom
 * WorkerFactory (see SendSignalWorkerTest).
 */
class SendSignalWorker @JvmOverloads constructor(
    appContext: Context,
    params: WorkerParameters,
    private val functions: WidgetlyFunctions = WidgetlyApplication.widgetlyFunctions,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val connectionId = inputData.getString(KEY_CONNECTION_ID) ?: return Result.failure()
        val preset = inputData.getString(KEY_PRESET) ?: return Result.failure()
        val signalId = inputData.getString(KEY_SIGNAL_ID) ?: return Result.failure()

        val signalResult = try {
            functions.sendSignal(connectionId, preset, signalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Offline / timeout / DNS failure at the transport layer (doesn't go
            // through the callable protocol at all). WorkManager already retries
            // with its own backoff policy, so no custom delay logic is needed here.
            return Result.retry()
        } catch (e: FirebaseFunctionsException) {
            // What the real SDK actually throws for a callable failure: the
            // gitlive KMP wrapper passes Android's FirebaseFunctionsException
            // through unwrapped (it just awaits the underlying Task). Only its
            // transient/network-class codes should be retried - a genuine
            // client error (e.g. invalid-argument) would just fail the same way
            // again and again, wasting WorkManager's retry budget forever.
            if (e.code in TRANSIENT_FUNCTIONS_CODES) return Result.retry()
            // Terminal failure (a retry branch above isn't terminal yet, so it must
            // not show anything): leave a marker so the widget says "전송 실패" instead
            // of looking like the signal went through. Mirrors iOS's markSendFailed.
            markSignalFeedback(applicationContext, connectionId, SIGNAL_SEND_FAILED_AT_KEY)
            return Result.failure()
        }

        if (signalResult.rateLimited) {
            // Review focus: a rate-limited response is NOT silent success from the
            // user's point of view - leave a marker in the matching widget's Glance
            // state so it can show "잠시 후 다시 보내주세요" instead of looking like the
            // signal just went through. SendSignalAction already set this marker on
            // the tap itself; re-setting it here restarts the window from when the
            // server actually rejected the call.
            markSignalFeedback(applicationContext, connectionId, SIGNAL_RATE_LIMITED_AT_KEY)
        }
        return Result.success()
    }

    companion object {
        const val KEY_CONNECTION_ID = "connectionId"
        const val KEY_PRESET = "preset"
        const val KEY_SIGNAL_ID = "signalId"

        // Codes that represent a transient/network-class failure rather than a
        // permanent one (bad input, auth, rate limit is handled separately via
        // the rateLimited result field, not an exception).
        private val TRANSIENT_FUNCTIONS_CODES = setOf(
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.UNKNOWN,
            FirebaseFunctionsException.Code.ABORTED,
        )

        /**
         * Stamps one of the signal-feedback markers (rate-limited / send-failed) on
         * every widget instance configured for this connection and redraws it. Both
         * markers are read as an elapsed-time window, so a delayed redraw is also
         * scheduled to clear the message once that window passes.
         */
        private suspend fun markSignalFeedback(
            context: Context,
            connectionId: String,
            key: Preferences.Key<Long>,
        ) {
            val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(ConnectionGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                if (state[CONNECTION_ID_KEY] != connectionId) continue
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[key] = System.currentTimeMillis()
                }
                ConnectionGlanceWidget().update(context, glanceId)
            }
            scheduleSignalFeedbackClear(context)
        }
    }
}
