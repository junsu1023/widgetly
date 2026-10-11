package com.example.widgetly.connection

import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ConnectionMappingTest {
    private fun doc(
        type: String = "couple",
        streak: Int = 7,
        lastSignalByUid: Map<String, SignalDoc> = mapOf(
            "uidA" to SignalDoc("heart", Timestamp(seconds = 1791108000, nanoseconds = 0)),
        ),
    ) = ConnectionDoc(
        memberUids = listOf("uidA", "uidB"),
        type = type,
        status = "active",
        anniversaryDate = "2025-01-01",
        streak = streak,
        lastSignalByUid = lastSignalByUid,
    )

    @Test
    fun `maps a Firestore-shaped document into a Connection`() {
        val connection = connectionFromDocument("conn-1", doc())
        assertEquals("conn-1", connection.id)
        assertEquals(listOf("uidA", "uidB"), connection.memberUids)
        assertEquals(ConnectionType.COUPLE, connection.type)
        assertEquals("active", connection.status)
        assertEquals(LocalDate(2025, 1, 1), connection.anniversaryDate)
        assertEquals(7, connection.streak)
        assertEquals("heart", connection.lastSignalByUid["uidA"]?.presetType)
        assertEquals(
            Instant.fromEpochSeconds(1791108000),
            connection.lastSignalByUid["uidA"]?.sentAt,
        )
    }

    @Test
    fun `sentAt keeps sub-second precision`() {
        val connection = connectionFromDocument(
            "conn-1",
            doc(lastSignalByUid = mapOf("uidA" to SignalDoc("heart", Timestamp(1791108000, 500_000_000)))),
        )
        assertEquals(
            Instant.fromEpochSeconds(1791108000, 500_000_000),
            connection.lastSignalByUid["uidA"]?.sentAt,
        )
    }

    @Test
    fun `friend connections map to the FRIEND type`() {
        assertEquals(ConnectionType.FRIEND, connectionFromDocument("c", doc(type = "friend")).type)
    }

    @Test
    fun `an unknown connection type fails loudly instead of silently picking one`() {
        assertFailsWith<IllegalStateException> { connectionFromDocument("c", doc(type = "sibling")) }
    }

    @Test
    fun `missing lastSignalByUid maps to an empty map instead of throwing`() {
        assertEquals(
            emptyMap(),
            connectionFromDocument("conn-1", doc(lastSignalByUid = emptyMap())).lastSignalByUid,
        )
    }
}
