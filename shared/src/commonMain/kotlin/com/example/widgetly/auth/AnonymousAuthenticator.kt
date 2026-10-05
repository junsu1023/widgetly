package com.example.widgetly.auth

import dev.gitlive.firebase.auth.FirebaseAuth

/**
 * Ensures the device has a signed-in Firebase user, signing in anonymously if
 * needed. No-ops (returns the existing uid) if a user is already signed in -
 * sign-in state persists across app restarts, so this should be cheap to call
 * on every startup.
 */
suspend fun ensureSignedIn(auth: FirebaseAuth): String {
    auth.currentUser?.let { return it.uid }
    val result = auth.signInAnonymously()
    return requireNotNull(result.user) { "signInAnonymously() returned no user" }.uid
}
