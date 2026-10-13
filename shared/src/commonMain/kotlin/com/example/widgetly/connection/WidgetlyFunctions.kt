package com.example.widgetly.connection

data class SendSignalResult(val rateLimited: Boolean)

/**
 * Client-side contract for the backend's callable Cloud Functions, defined under
 * the `functions/src` directory. All writes to server-owned state (pairing codes,
 * signals, connection creation) go through here rather than direct Firestore
 * writes - see CLAUDE.md's "신호 전송/연결 생성은 반드시 Cloud Functions callable 경유" rule.
 */
interface WidgetlyFunctions {
    suspend fun registerFcmToken(token: String, platform: String)
    suspend fun createPairingCode(type: ConnectionType): String
    suspend fun redeemPairingCode(code: String): String // connectionId
    suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult
    suspend fun updateMascot(connectionId: String, mascotId: String)
}
