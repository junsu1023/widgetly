package com.example.widgetly.push

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.widget.CONNECTION_ID_KEY
import com.example.widgetly.widget.CONNECTION_REFRESH_TOKEN_KEY
import com.example.widgetly.widget.ConnectionGlanceWidget
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the data message `sendSignal` (functions/src/sendSignal.ts) triggers a
 * push for, so a partner's signal shows up on the shared widget without the user
 * having to open the app.
 *
 * Honest limits on "즉시"(immediately): this callback runs under the OS's normal
 * background-execution constraints for FCM - delivery itself can be delayed by
 * Doze/battery optimization or network conditions (anywhere from under a second to
 * several minutes), and this method still has to await a Firestore read before the
 * widget redraws. So this path is "fast when it works", not a guaranteed-instant
 * push - see docs/widget-development.md's "공통 폴백" for what covers the gap when
 * it doesn't (the widget's own periodic re-check against the server).
 */
class WidgetlyFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onMessageReceived(message: RemoteMessage) {
        val connectionId = message.data["connectionId"] ?: return
        scope.launch {
            // Warm Firestore's local persistence cache so ConnectionGlanceWidget's
            // one-shot read (see its provideGlance doc comment) picks up the latest
            // state once we force it to recompose below.
            WidgetlyApplication.connectionRepository.observeConnection(connectionId).first()
            bumpRefreshToken(applicationContext, connectionId)
            ConnectionGlanceWidget().updateAll(applicationContext)
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { syncFcmToken(WidgetlyApplication.widgetlyFunctions, token, "android") }
    }

    companion object {
        private suspend fun bumpRefreshToken(context: Context, connectionId: String) {
            val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(ConnectionGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                if (state[CONNECTION_ID_KEY] != connectionId) continue
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[CONNECTION_REFRESH_TOKEN_KEY] = System.currentTimeMillis()
                }
            }
        }
    }
}
