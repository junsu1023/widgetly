import SwiftUI
import FirebaseCore
import FirebaseAuth
import FirebaseFunctions
import FirebaseMessaging

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

    /// Mirrors WidgetlyApplication.bootstrapFirebase() on Android: ensure an
    /// anonymous Firebase user exists (no-op if already signed in), then forward
    /// this device's FCM token to the backend via the `registerFcmToken` callable
    /// (functions/src/registerFcmToken.ts). Kotlin suspend functions can't be
    /// called as native Swift async/await without extra interop tooling (SKIE,
    /// not in use here) - they surface as completion-handler APIs instead - so
    /// this reimplements the same two steps as ensureSignedIn/syncFcmToken
    /// (shared/src/commonMain/kotlin/com/example/widgetly/{auth,push}) directly
    /// against the native Firebase iOS SDK. The wire shape ("token", "platform")
    /// must stay identical to FirebaseWidgetlyFunctions.registerFcmToken.
    private func bootstrapFirebase() {
        Task {
            if Auth.auth().currentUser == nil {
                try await Auth.auth().signInAnonymously()
            }
            guard let token = try? await Messaging.messaging().token() else { return }
            _ = try await Functions.functions().httpsCallable("registerFcmToken").call([
                "token": token,
                "platform": "ios",
            ])
        }
    }
}
