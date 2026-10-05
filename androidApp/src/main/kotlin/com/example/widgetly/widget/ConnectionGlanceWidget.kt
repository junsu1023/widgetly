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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.connection.Connection
import com.example.widgetly.theme.ThemeCatalog
import kotlinx.coroutines.flow.first
import java.util.UUID

val CONNECTION_ID_KEY = stringPreferencesKey("connection_id")

// Same refresh-token forcing mechanism as DDAY_REFRESH_TOKEN_KEY (see
// DdayGlanceWidget.kt): Glance's stored Preferences use structural equality, so
// re-writing the same connectionId wouldn't re-trigger currentState() readers on
// its own. WidgetlyFirebaseMessagingService bumps this after a partner's signal
// push arrives so the widget actually recomposes and re-reads the connection.
val CONNECTION_REFRESH_TOKEN_KEY = longPreferencesKey("connection_refresh_token")

// Set by SendSignalWorker when the backend reports rateLimited=true, so the
// "잠시 후 다시 보내주세요" message survives past the worker finishing (which runs
// detached from any UI). Cleared optimistically the next time the signal button
// is tapped, rather than on a timer - no periodic recomposition mechanism exists
// to expire it on its own.
val SIGNAL_RATE_LIMITED_AT_KEY = longPreferencesKey("signal_rate_limited_at")

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
            ConnectionWidgetContent(connectionId, connectionState, rateLimitedAt, themeId)
        }
    }
}

@Composable
internal fun ConnectionWidgetContent(
    connectionId: String?,
    connectionState: Any?,
    rateLimitedAt: Long?,
    themeId: String?,
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
                        if (rateLimitedAt != null) {
                            Text("잠시 후 다시 보내주세요", style = textStyle)
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

        // Optimistic clear: don't let a stale rate-limit message from a previous tap
        // linger once the user tries sending again.
        updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(SIGNAL_RATE_LIMITED_AT_KEY) }
        ConnectionGlanceWidget().update(context, glanceId)

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
