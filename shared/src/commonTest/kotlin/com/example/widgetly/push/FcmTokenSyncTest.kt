package com.example.widgetly.push

import com.example.widgetly.connection.ConnectionType
import com.example.widgetly.connection.SendSignalResult
import com.example.widgetly.connection.WidgetlyFunctions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeWidgetlyFunctions : WidgetlyFunctions {
    val registeredTokens = mutableListOf<Pair<String, String>>()
    override suspend fun registerFcmToken(token: String, platform: String) { registeredTokens += token to platform }
    override suspend fun createPairingCode(type: ConnectionType) = error("not used")
    override suspend fun redeemPairingCode(code: String) = error("not used")
    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult = error("not used")
}

class FcmTokenSyncTest {
    @Test
    fun `forwards the token and platform to the backend`() = runTest {
        val fake = FakeWidgetlyFunctions()
        syncFcmToken(fake, "tok-1", "android")
        assertEquals(listOf("tok-1" to "android"), fake.registeredTokens)
    }
}
