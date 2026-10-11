package com.example.widgetly.connection

import dev.gitlive.firebase.internal.decode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the serializable response/document shapes that the GitLive `data<T>()` calls
 * decode into. It cannot exercise the SDK itself (constructing a real
 * `HttpsCallableResult`/`DocumentSnapshot` needs a live Firebase project), but it does
 * prove each type has a working generated serializer and that its field names still
 * match the backend's wire contract - the two things C1 got wrong.
 */
class WireShapeTest {
    private val json = Json

    @Test
    fun `createPairingCode response decodes`() {
        assertEquals(
            CreatePairingCodeResponse("123456"),
            json.decodeFromString<CreatePairingCodeResponse>("""{"code":"123456"}"""),
        )
    }

    @Test
    fun `redeemPairingCode response decodes`() {
        assertEquals(
            RedeemPairingCodeResponse("conn-1"),
            json.decodeFromString<RedeemPairingCodeResponse>("""{"connectionId":"conn-1"}"""),
        )
    }

    @Test
    fun `sendSignal response decodes`() {
        assertEquals(
            SendSignalResponse(rateLimited = true),
            json.decodeFromString<SendSignalResponse>("""{"rateLimited":true}"""),
        )
    }

    @Test
    fun `callable responses decode through GitLive's own decoder`() {
        // Not Json, but the very decoder `HttpsCallableResult.data<T>()` routes into -
        // GitLive exposes it publicly, so C1's actual break (no serializer for `Any`)
        // is reachable without a live Firebase project. The input is the native-shaped
        // map the SDK hands the decoder.
        assertEquals(
            CreatePairingCodeResponse("123456"),
            decode(CreatePairingCodeResponse.serializer(), mapOf("code" to "123456")),
        )
        assertEquals(
            RedeemPairingCodeResponse("conn-1"),
            decode(RedeemPairingCodeResponse.serializer(), mapOf("connectionId" to "conn-1")),
        )
        assertEquals(
            SendSignalResponse(rateLimited = true),
            decode(SendSignalResponse.serializer(), mapOf("rateLimited" to true)),
        )
    }

    @Test
    fun `ConnectionDoc field names match the backend ConnectionDoc`() {
        val descriptor = ConnectionDoc.serializer().descriptor
        val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        assertEquals(
            listOf("memberUids", "type", "status", "anniversaryDate", "streak", "lastSignalByUid"),
            names,
        )
        // Only lastSignalByUid may be absent from a real document (a brand-new connection
        // has no signals); everything else is written at creation time.
        assertEquals(
            listOf("lastSignalByUid"),
            names.filterIndexed { index, _ -> descriptor.isElementOptional(index) },
        )
    }

    @Test
    fun `SignalDoc field names match the backend lastSignalByUid entry`() {
        val descriptor = SignalDoc.serializer().descriptor
        val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        assertEquals(listOf("presetType", "sentAt"), names)
        assertTrue(names.none { descriptor.isElementOptional(names.indexOf(it)) })
    }
}
