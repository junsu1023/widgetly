package com.example.widgetly.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * Handles the two data-message shapes `sendPushToPartner` (functions/src/lib/push.ts)
 * sends:
 * - A signal push (`{ connectionId, presetType, senderUid }`, from sendSignal.ts) -
 *   refreshes the shared widget so a partner's signal shows up without the user
 *   having to open the app.
 * - A pairing-success push (`{ connectionId }` only, no `presetType`, from
 *   redeemPairingCode.ts) - sent to the code CREATOR, who otherwise has no path
 *   at all to learn the connectionId that resulted from their code being
 *   redeemed (the redeemer sees it directly in PairingScreen; the creator never
 *   does). Fix Round 1 (task-4 review): surface it via a copyable system
 *   notification instead of silently warming a cache for a widget the creator
 *   has no connectionId to configure yet.
 *
 * Honest limits on "즉시"(immediately): this callback runs under the OS's normal
 * background-execution constraints for FCM - delivery itself can be delayed by
 * Doze/battery optimization or network conditions (anywhere from under a second to
 * several minutes), and the signal-push path still has to await a Firestore read
 * before the widget redraws. So this path is "fast when it works", not a
 * guaranteed-instant push - see docs/widget-development.md's "공통 폴백" for what
 * covers the gap when it doesn't (the widget's own periodic re-check against the
 * server).
 */
class WidgetlyFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onMessageReceived(message: RemoteMessage) {
        val connectionId = message.data["connectionId"] ?: return

        if (message.data["presetType"] == null) {
            showPairingSuccessNotification(connectionId)
            return
        }

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

    /**
     * Minimal fix, not a connections-list feature: this just makes the
     * connectionId visible and selectable somewhere that already exists
     * (the notification shade) instead of a new screen. Long-pressing notification
     * text to select/copy it is a standard platform affordance. Needs
     * POST_NOTIFICATIONS (requested from MainActivity on API 33+) or
     * NotificationManagerCompat.notify() silently does nothing.
     */
    private fun showPairingSuccessNotification(connectionId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PAIRING_CHANNEL_ID,
                "커플/친구 연결",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, PAIRING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("연결 완료")
            .setContentText("연결 ID: $connectionId (길게 눌러 복사)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("연결 ID: $connectionId\n\n길게 눌러 복사한 뒤 위젯 설정 화면에 붙여넣으세요."),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(connectionId.hashCode(), notification)
    }

    companion object {
        private const val PAIRING_CHANNEL_ID = "pairing_success"

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
