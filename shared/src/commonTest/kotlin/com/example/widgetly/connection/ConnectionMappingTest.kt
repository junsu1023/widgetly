package com.example.widgetly.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ConnectionMappingTest {
    @Test
    fun `maps a Firestore-shaped document into a Connection`() {
        val data = mapOf(
            "memberUids" to listOf("uidA", "uidB"),
            "type" to "couple",
            "status" to "active",
            "anniversaryDate" to "2025-01-01",
            "streak" to 7,
            "lastSignalByUid" to mapOf(
                "uidA" to mapOf("presetType" to "heart", "sentAt" to Instant.parse("2026-10-04T10:00:00Z")),
            ),
        )
        val connection = connectionFromDocument("conn-1", data)
        assertEquals(ConnectionType.COUPLE, connection.type)
        assertEquals(7, connection.streak)
        assertEquals("heart", connection.lastSignalByUid["uidA"]?.presetType)
    }

    @Test
    fun `missing lastSignalByUid maps to an empty map instead of throwing`() {
        val data = mapOf(
            "memberUids" to listOf("uidA", "uidB"), "type" to "friend", "status" to "active",
            "anniversaryDate" to "2025-01-01", "streak" to 0,
        )
        assertEquals(emptyMap(), connectionFromDocument("conn-1", data).lastSignalByUid)
    }
}
