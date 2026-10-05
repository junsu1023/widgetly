package com.example.widgetly.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.connection.WidgetlyFunctions
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
            // Offline / timeout / DNS failure etc. WorkManager already retries with
            // its own backoff policy, so no custom delay logic is needed here.
            return Result.retry()
        }

        if (signalResult.rateLimited) {
            // Review focus: a rate-limited response is NOT silent success from the
            // user's point of view - leave a marker in the matching widget's Glance
            // state so it can show "잠시 후 다시 보내주세요" instead of looking like the
            // signal just went through.
            markRateLimited(applicationContext, connectionId)
        }
        return Result.success()
    }

    companion object {
        const val KEY_CONNECTION_ID = "connectionId"
        const val KEY_PRESET = "preset"
        const val KEY_SIGNAL_ID = "signalId"

        private suspend fun markRateLimited(context: Context, connectionId: String) {
            val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(ConnectionGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                if (state[CONNECTION_ID_KEY] != connectionId) continue
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[SIGNAL_RATE_LIMITED_AT_KEY] = System.currentTimeMillis()
                }
                ConnectionGlanceWidget().update(context, glanceId)
            }
        }
    }
}
