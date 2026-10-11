import SwiftUI
import FirebaseCore
import FirebaseAuth
import FirebaseMessaging
import Shared

private let appGroupSuiteName = "group.com.example.widgetly"
private let authTokenKey = "firebase_id_token"
private let ownUidKey = "firebase_uid"

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        FirebaseApp.configure()
        bootstrapFirebase()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        // ID tokens expire after roughly an hour, and the widget extension has no
        // Auth session of its own to refresh with — so refresh the cached copy
        // every time the app comes back to the foreground, on top of the launch
        // path above.
        //
        // ponytail: no proactive refresh while backgrounded. A background task
        // that re-mints the token on a schedule is a real feature (BGTaskScheduler
        // registration, budget negotiation, its own failure modes), not a few
        // lines. Ceiling: a signal sent from the widget more than ~1 hour after the
        // app was last foregrounded can fail with an expired token — the widget
        // shows "전송 실패, 다시 시도해주세요" and opening the app fixes it. Upgrade
        // path if that proves common: BGAppRefreshTask doing just this call.
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await cacheAuthCredentialsForWidget() }
            }
        }
    }

    /// Fetches the FCM token (platform API, no Kotlin equivalent), then hands
    /// off to shared Kotlin (IosBootstrap.kt) for everything else: anonymous
    /// sign-in and forwarding the token to the `registerFcmToken` callable.
    /// Mirrors WidgetlyApplication.bootstrapFirebase() on Android - both
    /// platforms call the same ensureSignedIn/syncFcmToken, so the wire
    /// format lives in exactly one place (FirebaseWidgetlyFunctions.kt), not
    /// duplicated here.
    private func bootstrapFirebase() {
        Task {
            // Deliberately not a `guard ... else { return }` any more: a failed FCM
            // token fetch must not also skip the credential caching below, which is
            // what makes the widget's signal button work at all.
            if let token = try? await Messaging.messaging().token() {
                try? await IosBootstrapKt.bootstrapFirebaseIos(token: token, platform: "ios")
            }
            await cacheAuthCredentialsForWidget()
        }
    }
}

/// Publishes this device's Firebase ID token and uid into the App Group so the
/// widget extension can use them: the token as `Authorization: Bearer` for the
/// `sendSignal`/`getConnection` callables it calls over plain HTTPS, and the uid
/// to tell the partner's signal from its own. That extension carries no Firebase
/// SDK (memory-constrained; see docs/widget-development.md), so this is the only
/// place either value can come from.
///
/// No-ops before anonymous sign-in has ever completed on this device (the widget
/// then shows a send failure rather than silently doing nothing). Firebase Auth
/// persists the session across launches, so this only really bites on a first run
/// that also fails to reach the network.
private func cacheAuthCredentialsForWidget() async {
    guard let user = Auth.auth().currentUser,
          let defaults = UserDefaults(suiteName: appGroupSuiteName) else {
        return
    }
    // getIDToken() returns the cached token while it's still valid and refreshes
    // it transparently once it isn't, so there's no expiry bookkeeping here.
    guard let token = try? await user.getIDToken() else { return }
    defaults.set(token, forKey: authTokenKey)
    defaults.set(user.uid, forKey: ownUidKey)
}
