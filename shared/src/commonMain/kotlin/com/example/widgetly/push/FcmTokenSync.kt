package com.example.widgetly.push

import com.example.widgetly.connection.WidgetlyFunctions

/**
 * Forwards an FCM registration token to the backend so it can push signals to
 * this device. Goes through [WidgetlyFunctions] (Cloud Functions callable),
 * not a direct Firestore write - see CLAUDE.md's callable-only rule.
 */
suspend fun syncFcmToken(functions: WidgetlyFunctions, token: String, platform: String) {
    functions.registerFcmToken(token, platform)
}
