import SwiftUI
import FirebaseCore
import FirebaseMessaging
import Shared

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        bootstrapFirebase()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
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
            guard let token = try? await Messaging.messaging().token() else { return }
            try await IosBootstrapKt.bootstrapFirebaseIos(token: token, platform: "ios")
        }
    }
}
