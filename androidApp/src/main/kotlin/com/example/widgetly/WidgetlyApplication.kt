package com.example.widgetly

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.widgetly.auth.ensureSignedIn
import com.example.widgetly.connection.ConnectionRepository
import com.example.widgetly.connection.FirebaseWidgetlyFunctions
import com.example.widgetly.connection.FirestoreConnectionRepository
import com.example.widgetly.connection.WidgetlyFunctions
import com.example.widgetly.dday.AndroidDdayStorage
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.push.syncFcmToken
import com.example.widgetly.widget.DDAY_REFRESH_TOKEN_KEY
import com.example.widgetly.widget.DdayGlanceWidget
import com.google.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetlyApplication : Application() {
    private val repository: DdayRepository by lazy {
        DdayRepository(AndroidDdayStorage(this)) {
            val glanceIds = GlanceAppWidgetManager(this@WidgetlyApplication)
                .getGlanceIds(DdayGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                updateAppWidgetState(this@WidgetlyApplication, glanceId) { prefs ->
                    prefs[DDAY_REFRESH_TOKEN_KEY] = System.currentTimeMillis()
                }
            }
            DdayGlanceWidget().updateAll(this@WidgetlyApplication)
        }
    }

    private val functions: FirebaseWidgetlyFunctions by lazy {
        FirebaseWidgetlyFunctions(Firebase.functions(Firebase.app))
    }

    // Named-database Firestore instance - see ConnectionRepository's doc comment:
    // this client has no `(default)` database to fall back to.
    private val connectionRepo: ConnectionRepository by lazy {
        FirestoreConnectionRepository(Firebase.firestore(Firebase.app, "widgetly"))
    }

    // SupervisorJob + CoroutineExceptionHandler, not a bare CoroutineScope: without
    // them an exception in any coroutine launched here (a Firebase call failing
    // offline, a decode error, ...) reaches the default uncaught-exception handler and
    // kills the whole process, and cancels every sibling coroutine on the way out.
    // Background bootstrap work failing must not take the app down - log and move on.
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                Log.e("WidgetlyApplication", "Background work failed", throwable)
            },
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        bootstrapFirebase()
    }

    /**
     * Signs the device in anonymously (no-op if already signed in) and keeps the
     * backend's copy of this device's FCM token up to date, so Cloud Functions can
     * push partner signals to it. Auth/Functions instances aren't tied to a named
     * Firestore database the way `FirebaseFirestore` is (see ConnectionRepository's
     * doc comment on the `widgetly` named database), but are still constructed
     * app-scoped via `Firebase.app` for consistency rather than any bare accessor.
     */
    private fun bootstrapFirebase() {
        val auth = Firebase.auth(Firebase.app)
        applicationScope.launch {
            ensureSignedIn(auth)
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                applicationScope.launch { syncFcmToken(functions, token, "android") }
            }
        }
    }

    companion object {
        private lateinit var instance: WidgetlyApplication
        val ddayRepository: DdayRepository get() = instance.repository
        val widgetlyFunctions: WidgetlyFunctions get() = instance.functions
        val connectionRepository: ConnectionRepository get() = instance.connectionRepo
    }
}
