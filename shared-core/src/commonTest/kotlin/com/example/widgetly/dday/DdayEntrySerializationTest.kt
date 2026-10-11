package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DdayEntrySerializationTest {
    @Test
    fun `round-trips through JSON`() {
        val entry = DdayEntry(
            id = "abc-123",
            title = "수능",
            targetDate = LocalDate(2026, 11, 12),
            countMode = CountMode.START_AT_ONE,
            repeat = RepeatMode.NONE,
        )
        val json = Json.encodeToString(entry)
        val decoded = Json.decodeFromString<DdayEntry>(json)
        assertEquals(entry, decoded)
    }
}
