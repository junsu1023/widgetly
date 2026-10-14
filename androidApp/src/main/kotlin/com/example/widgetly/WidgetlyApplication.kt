package com.example.widgetly

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.widgetly.auth.ensureSignedIn
import com.example.widgetly.connection.AndroidConnectionIdStorage
import com.example.widgetly.connection.ConnectionIdStorage
import com.example.widgetly.connection.ConnectionRepository
import com.example.widgetly.connection.FirebaseWidgetlyFunctions
import com.example.widgetly.connection.FirestoreConnectionRepository
import com.example.widgetly.connection.WidgetlyFunctions
import com.example.widgetly.dday.AndroidDdayStorage
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.push.syncFcmToken
import com.example.widgetly.theme.AndroidMascotPreferenceStorage
import com.example.widgetly.theme.MascotPreferenceStorage
import com.example.widgetly.widget.CONNECTION_REFRESH_TOKEN_KEY
import com.example.widgetly.widget.ConnectionGlanceWidget
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

    // Lifted out of bootstrapFirebase() so the signed-in uid is reachable from the
    // widget too: ConnectionGlanceWidget needs it to tell which half of
    // Connection.lastSignalByUid is the partner's.
    private val auth by lazy { Firebase.auth(Firebase.app) }

    // Named-database Firestore instance - see ConnectionRepository's doc comment:
    // this client has no `(default)` database to fall back to.
    private val connectionRepo: ConnectionRepository by lazy {
        FirestoreConnectionRepository(Firebase.firestore(Firebase.app, "widgetly"))
    }

    // Wrapped, not handed out raw: both Glance widgets render the selected mascot,
    // and neither has a reactive state key for it (it lives in its own DataStore,
    // not in Glance's per-widget Preferences). So a write has to push the widgets
    // the same way DdayRepository's onChanged above does - bump each widget's
    // refresh token (Glance's stored Preferences compare structurally, so a changing
    // value is what actually re-triggers currentState() readers) and updateAll().
    // Keeping this here rather than in MascotPickerViewModel is deliberate: every
    // writer (picker screen today, anything later) gets the refresh for free.
    private val mascotPrefs: MascotPreferenceStorage by lazy {
        val storage = AndroidMascotPreferenceStorage(this)
        object : MascotPreferenceStorage {
            override suspend fun read(): String = storage.read()

            override suspend fun write(mascotId: String) {
                storage.write(mascotId)
                refreshMascotWidgets()
            }
        }
    }

    private val connectionIds: ConnectionIdStorage by lazy { AndroidConnectionIdStorage(this) }

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

    private suspend fun refreshMascotWidgets() {
        val manager = GlanceAppWidgetManager(this)
        val now = System.currentTimeMillis()
        for (glanceId in manager.getGlanceIds(DdayGlanceWidget::class.java)) {
            updateAppWidgetState(this, glanceId) { prefs -> prefs[DDAY_REFRESH_TOKEN_KEY] = now }
        }
        for (glanceId in manager.getGlanceIds(ConnectionGlanceWidget::class.java)) {
            updateAppWidgetState(this, glanceId) { prefs -> prefs[CONNECTION_REFRESH_TOKEN_KEY] = now }
        }
        DdayGlanceWidget().updateAll(this)
        ConnectionGlanceWidget().updateAll(this)
    }

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
        val mascotPreferenceStorage: MascotPreferenceStorage get() = instance.mascotPrefs
        val connectionIdStorage: ConnectionIdStorage get() = instance.connectionIds

        /**
         * Null until anonymous sign-in completes (it runs off [applicationScope] on
         * launch), and after a sign-out. Callers must handle null rather than assume
         * a uid is available - the widget just omits the partner's signal line.
         */
        val currentUid: String? get() = instance.auth.currentUser?.uid
    }
}
