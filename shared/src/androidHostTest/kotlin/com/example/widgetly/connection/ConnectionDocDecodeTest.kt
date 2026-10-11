package com.example.widgetly.connection

import dev.gitlive.firebase.internal.decode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The half of C1's regression test that can't live in `commonTest`: [SignalDoc]'s
 * `sentAt` is GitLive's `Timestamp`, whose decoder expects the *platform's* native
 * timestamp value (`com.google.firebase.Timestamp` here) rather than a number - so
 * the map handed to `decode` has to be built per platform.
 *
 * This goes through `dev.gitlive.firebase.internal.decode`, the public entry point
 * `DocumentSnapshot.data<T>()` itself routes into, against a map shaped the way the
 * SDK hands it over. No network and no Firebase project involved, but unlike a
 * kotlinx Json round-trip it does exercise the real decoder that C1 broke.
 */
class ConnectionDocDecodeTest {

    @Test
    fun `a full connection document decodes through GitLive's own decoder`() {
        val decoded: ConnectionDoc = decode(
            ConnectionDoc.serializer(),
            mapOf(
                "memberUids" to listOf("uid-a", "uid-b"),
                "type" to "couple",
                "status" to "active",
                "anniversaryDate" to "2026-01-01",
                "streak" to 7,
                "lastSignalByUid" to mapOf(
                    "uid-b" to mapOf(
                        "presetType" to "heart",
                        "sentAt" to com.google.firebase.Timestamp(1_770_000_000L, 500),
                    ),
                ),
            ),
        )

        assertEquals(listOf("uid-a", "uid-b"), decoded.memberUids)
        assertEquals("couple", decoded.type)
        assertEquals("active", decoded.status)
        assertEquals(7, decoded.streak)
        assertEquals("heart", decoded.lastSignalByUid["uid-b"]?.presetType)
        assertEquals(1_770_000_000L, decoded.lastSignalByUid["uid-b"]?.sentAt?.seconds)
        assertEquals(500, decoded.lastSignalByUid["uid-b"]?.sentAt?.nanoseconds)
    }

    @Test
    fun `a brand-new connection with no signals decodes`() {
        val decoded: ConnectionDoc = decode(
            ConnectionDoc.serializer(),
            mapOf(
                "memberUids" to listOf("uid-a", "uid-b"),
                "type" to "friend",
                "status" to "active",
                "anniversaryDate" to "2026-01-01",
                "streak" to 0,
            ),
        )

        assertEquals(emptyMap(), decoded.lastSignalByUid)
    }
}
