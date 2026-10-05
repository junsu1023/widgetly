package com.example.widgetly.connection

import dev.gitlive.firebase.functions.FirebaseFunctions

/**
 * Calls the real deployed backend callables. Request/response field names below
 * must match `functions/src/registerFcmToken.ts`, `createPairingCode.ts`,
 * `redeemPairingCode.ts`, `sendSignal.ts` exactly - this is the actual wire contract,
 * not something re-derived independently on the client.
 */
class FirebaseWidgetlyFunctions(private val functions: FirebaseFunctions) : WidgetlyFunctions {

    override suspend fun registerFcmToken(token: String, platform: String) {
        functions.httpsCallable("registerFcmToken")
            .invoke(mapOf("token" to token, "platform" to platform))
    }

    override suspend fun createPairingCode(type: ConnectionType): String {
        val result = functions.httpsCallable("createPairingCode")
            .invoke(mapOf("type" to type.wireValue))
        return result.data<Map<String, Any?>>()["code"] as String
    }

    override suspend fun redeemPairingCode(code: String): String {
        val result = functions.httpsCallable("redeemPairingCode")
            .invoke(mapOf("code" to code))
        return result.data<Map<String, Any?>>()["connectionId"] as String
    }

    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult {
        val result = functions.httpsCallable("sendSignal")
            .invoke(mapOf("connectionId" to connectionId, "preset" to preset, "signalId" to signalId))
        val rateLimited = result.data<Map<String, Any?>>()["rateLimited"] as Boolean
        return SendSignalResult(rateLimited)
    }
}

private val ConnectionType.wireValue: String
    get() = when (this) {
        ConnectionType.COUPLE -> "couple"
        ConnectionType.FRIEND -> "friend"
    }
