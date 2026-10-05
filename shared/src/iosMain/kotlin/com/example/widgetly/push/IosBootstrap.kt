package com.example.widgetly.push

import com.example.widgetly.auth.ensureSignedIn
import com.example.widgetly.connection.FirebaseWidgetlyFunctions
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.functions

/**
 * Called from iOSApp.swift's startup. The main app and its iOS widget/intent
 * extension are separate processes with different memory budgets - the plan's
 * "lightweight HTTPS, not the full SDK" constraint applies to that extension,
 * not to this main app target, which shares a process with `shared` the same
 * way the Android app does. So this mirrors
 * WidgetlyApplication.bootstrapFirebase() on Android: sign in anonymously
 * (no-op if already signed in) and forward the FCM token to the backend via
 * [syncFcmToken]/[FirebaseWidgetlyFunctions] - the same Kotlin code Android
 * calls - so the `registerFcmToken` wire format lives in exactly one place
 * instead of being hand-duplicated in Swift. Only primitives ([token],
 * [platform]) cross the Swift/Kotlin boundary; fetching the token itself has
 * no Kotlin equivalent (platform API) and stays in Swift.
 */
suspend fun bootstrapFirebaseIos(token: String, platform: String) {
    val app = Firebase.app
    ensureSignedIn(Firebase.auth(app))
    syncFcmToken(FirebaseWidgetlyFunctions(Firebase.functions(app)), token, platform)
}
