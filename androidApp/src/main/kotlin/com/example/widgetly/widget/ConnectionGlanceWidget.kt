package com.example.widgetly.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.connection.Connection
import com.example.widgetly.theme.ThemeCatalog
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

val CONNECTION_ID_KEY = stringPreferencesKey("connection_id")

// Same refresh-token forcing mechanism as DDAY_REFRESH_TOKEN_KEY (see
// DdayGlanceWidget.kt): Glance's stored Preferences use structural equality, so
// re-writing the same connectionId wouldn't re-trigger currentState() readers on
// its own. WidgetlyFirebaseMessagingService bumps this after a partner's signal
// push arrives so the widget actually recomposes and re-reads the connection.
val CONNECTION_REFRESH_TOKEN_KEY = longPreferencesKey("connection_refresh_token")

// Set by SendSignalAction the instant the button is tapped (so the cooldown shows
// proactively, matching the server's own window instead of waiting for a round
// trip to reject the tap) and re-set by SendSignalWorker when the backend reports
// rateLimited=true. Read as an elapsed-time window, not mere presence - see
// isWithinFeedbackWindow.
val SIGNAL_RATE_LIMITED_AT_KEY = longPreferencesKey("signal_rate_limited_at")

// Set by SendSignalWorker when sendSignal fails non-transiently, so the failure
// isn't silently swallowed. Mirrors iOS's sendFailed flag in
// iosApp/DdayWidgetExtension/ConnectionWidget.swift.
val SIGNAL_SEND_FAILED_AT_KEY = longPreferencesKey("signal_send_failed_at")

// Must match sendSignal's RATE_LIMIT_SECONDS (functions/src/sendSignal.ts) - the
// plan's Global Constraints require the client cooldown UI to use the same window
// as the server. Also reused as the display window for the send-failed message.
internal const val SIGNAL_FEEDBACK_WINDOW_MS = 5_000L

private const val DEFAULT_PRESET = "heart"

private val ConnectionIdKey = ActionParameters.Key<String>("connectionId")
private val PresetKey = ActionParameters.Key<String>("preset")

private object ConnectionLoading

class ConnectionGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // currentState() must be read reactively inside the composable (same
            // lesson already learned once for DdayGlanceWidget - see its doc
            // comment): update()/updateAll() reuse the running Glance session and
            // only recompose this content lambda, they never re-invoke
            // provideGlance(). Keeping these reads here (rather than inside
            // ConnectionWidgetContent itself) is what lets that function stay a
            // pure, parameter-driven composable that a unit test can call directly
            // with fixed inputs instead of needing a live ConnectionRepository/
            // Firestore (see ConnectionGlanceWidgetTest).
            val connectionId = currentState(CONNECTION_ID_KEY)
            val refreshToken = currentState(CONNECTION_REFRESH_TOKEN_KEY)
            val rateLimitedAt = currentState(SIGNAL_RATE_LIMITED_AT_KEY)
            val sendFailedAt = currentState(SIGNAL_SEND_FAILED_AT_KEY)
            val themeId = currentState(THEME_ID_KEY)
            val connectionState by produceState<Any?>(initialValue = ConnectionLoading, key1 = connectionId, key2 = refreshToken) {
                value = connectionId?.let { id2 ->
                    // One-shot read off ConnectionRepository's Flow: this reflects
                    // whatever Firestore's local persistence cache last synced (the
                    // live app, or WidgetlyFirebaseMessagingService's warm-up read)
                    // - not a live subscription. A Glance content composition is too
                    // short-lived to keep a Flow collector open, so each
                    // provideGlance()/update() just needs a fresh snapshot rather
                    // than an ongoing stream.
                    WidgetlyApplication.connectionRepository.observeConnection(id2).first()
                }
            }
            ConnectionWidgetContent(
                connectionId,
                connectionState,
                rateLimitedAt,
                sendFailedAt,
                themeId,
                WidgetlyApplication.currentUid,
            )
        }
    }
}

/**
 * Both signal-feedback markers are timestamps read as a window, not booleans:
 * Glance has no live timer, so a marker that only meant "present" would stick on
 * screen until some unrelated event forced a redraw. [scheduleSignalFeedbackClear]
 * is what forces that redraw once the window has passed.
 */
private fun isWithinFeedbackWindow(markedAt: Long?): Boolean =
    markedAt != null && System.currentTimeMillis() - markedAt < SIGNAL_FEEDBACK_WINDOW_MS

@Composable
internal fun ConnectionWidgetContent(
    connectionId: String?,
    connectionState: Any?,
    rateLimitedAt: Long?,
    sendFailedAt: Long?,
    themeId: String?,
    myUid: String? = null,
) {
    val theme = ThemeCatalog.find(themeId ?: ThemeCatalog.DEFAULT_THEME_ID)
    val backgroundColor = Color(theme.backgroundColorArgb)
    val foregroundColor = Color(theme.foregroundColorArgb)
    val textStyle = TextStyle(fontSize = 14.sp, color = ColorProvider(day = foregroundColor, night = foregroundColor))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = backgroundColor, night = backgroundColor))
            .cornerRadius(16.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            connectionId == null -> Text("설정 필요", style = textStyle)
            connectionState === ConnectionLoading -> {}
            connectionState == null -> Text("연결 끊김", style = textStyle)
            else -> {
                val connection = connectionState as Connection
                if (connection.status == "disconnected") {
                    Text("연결 끊김", style = textStyle)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "연속 ${connection.streak}일",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(day = foregroundColor, night = foregroundColor),
                            ),
                        )
                        // 상대의 최신 신호 (the plan's other half of its own goal, next
                        // to the streak above). Needs this device's uid to tell which
                        // lastSignalByUid entry is the partner's; when sign-in hasn't
                        // finished yet (myUid == null) the line is simply omitted
                        // rather than guessed at. Plain text, no emoji/preset display
                        // mapping - none exists anywhere in the codebase to reuse.
                        val partnerSignal = myUid
                            ?.let { uid -> connection.memberUids.firstOrNull { it != uid } }
                            ?.let { connection.lastSignalByUid[it] }
                        if (partnerSignal != null) {
                            Text("상대 신호: ${partnerSignal.presetType}", style = textStyle)
                        }
                        if (isWithinFeedbackWindow(rateLimitedAt)) {
                            // No button at all while cooling down: tapping again
                            // inside the server's own 5s window can only be
                            // rejected, so don't offer it.
                            Text("잠시 후 다시 보내주세요", style = textStyle)
                        } else {
                            // Unlike a cooldown, a failed send is worth retrying
                            // right away - keep the button and just say so.
                            if (isWithinFeedbackWindow(sendFailedAt)) {
                                Text("전송 실패, 다시 시도해주세요", style = textStyle)
                            }
                            Box(
                                modifier = GlanceModifier
                                    .padding(top = 8.dp)
                                    .clickable(
                                        actionRunCallback<SendSignalAction>(
                                            actionParametersOf(
                                                ConnectionIdKey to connection.id,
                                                PresetKey to DEFAULT_PRESET,
                                            ),
                                        ),
                                    ),
                            ) {
                                Text("신호 보내기", style = textStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Glance action callbacks run under the same background-execution limits as a
 * BroadcastReceiver, so no network I/O happens here - this just enqueues
 * SendSignalWorker, which owns the network constraint and retry/backoff policy.
 */
class SendSignalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val connectionId = parameters[ConnectionIdKey] ?: return
        val preset = parameters[PresetKey] ?: return
        val signalId = UUID.randomUUID().toString() // WorkManager retry idempotency key

        // Proactive cooldown (plan's Global Constraints: the client cooldown UI must
        // use the server's own 5s window). Set the marker on the tap itself instead
        // of waiting for sendSignal to come back with rateLimited=true - otherwise
        // the button stays live and invites taps the server can only reject. The
        // previous tap's send-failure message is cleared here: the user is retrying,
        // so last attempt's outcome is no longer what they need to see.
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SIGNAL_RATE_LIMITED_AT_KEY] = System.currentTimeMillis()
            prefs.remove(SIGNAL_SEND_FAILED_AT_KEY)
        }
        ConnectionGlanceWidget().update(context, glanceId)
        scheduleSignalFeedbackClear(context)

        val workRequest = OneTimeWorkRequestBuilder<SendSignalWorker>()
            .setInputData(
                workDataOf(
                    SendSignalWorker.KEY_CONNECTION_ID to connectionId,
                    SendSignalWorker.KEY_PRESET to preset,
                    SendSignalWorker.KEY_SIGNAL_ID to signalId,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

/**
 * Redraws the widget once the signal-feedback window has elapsed, so
 * [isWithinFeedbackWindow] re-evaluates to false and the cooldown/failure message
 * clears itself. Glance offers no periodic recomposition for this widget and a
 * foreground timer can't survive the widget host, so a delayed one-shot job is the
 * cheapest thing that can flip the UI back on its own.
 *
 * ponytail: WorkManager honours the initial delay as a *minimum* - under Doze or
 * aggressive battery optimisation the redraw can land late, leaving the message up
 * a few seconds longer than the window. Upgrade path if that ever matters: an
 * AlarmManager exact alarm, which costs a permission and a wakeup budget.
 */
internal fun scheduleSignalFeedbackClear(context: Context) {
    val request = OneTimeWorkRequestBuilder<ClearSignalFeedbackWorker>()
        .setInitialDelay(SIGNAL_FEEDBACK_WINDOW_MS, TimeUnit.MILLISECONDS)
        .build()
    // Unique work: rapid taps would otherwise pile up one redraw job each, and the
    // latest tap is the only one whose window matters.
    WorkManager.getInstance(context).enqueueUniqueWork(
        CLEAR_SIGNAL_FEEDBACK_WORK,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

private const val CLEAR_SIGNAL_FEEDBACK_WORK = "clear_signal_feedback"

class ClearSignalFeedbackWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // updateAll, not update(glanceId): the marker is per widget instance but
        // redrawing every instance of this widget is both correct and cheaper than
        // threading a GlanceId through WorkManager's string-keyed input data.
        ConnectionGlanceWidget().updateAll(applicationContext)
        return Result.success()
    }
}
