package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DdayCategoryIconDecodeTest {
    @Test
    fun `decodes a pre-feature entry JSON with no category or icon keys to the defaults`() {
        val legacyJson = """{"id":"1","title":"수능","targetDate":"2026-11-12","countMode":"START_AT_ZERO","repeat":"NONE"}"""
        val decoded = Json.decodeFromString<DdayEntry>(legacyJson)
        assertEquals(DdayCategory.OTHER, decoded.category)
        assertEquals(DdayIcon.HEART, decoded.icon)
    }

    @Test
    fun `round-trips category and icon through JSON`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 1, 1), category = DdayCategory.FRIEND, icon = DdayIcon.PLANE)
        assertEquals(entry, Json.decodeFromString<DdayEntry>(Json.encodeToString(entry)))
    }
}
