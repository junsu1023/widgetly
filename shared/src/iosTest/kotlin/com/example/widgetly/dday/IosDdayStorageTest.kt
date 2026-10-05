package com.example.widgetly.dday

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

class IosDdayStorageTest {
    @Test
    fun `writeAll then readAll round-trips entries`() = runTest {
        val storage = IosDdayStorage(suiteName = "group.com.example.widgetly.test")
        storage.writeAll(listOf(sampleEntry))
        assertEquals(listOf(sampleEntry), storage.readAll())
    }

    @Test
    fun `readAll tolerates an unknown field added by a future version`() = runTest {
        val suiteName = "group.com.example.widgetly.test"
        NSUserDefaults(suiteName = suiteName).setObject(
            """[{"id":"sample-1","title":"sample","targetDate":"2026-12-25","countMode":"START_AT_ZERO","repeat":"NONE","futureField":"ignored"}]""",
            "entries_json",
        )
        val storage = IosDdayStorage(suiteName = suiteName)
        assertEquals(listOf(sampleEntry), storage.readAll())
    }

    @Test
    fun `readAll returns an empty list instead of throwing on corrupted data`() = runTest {
        val suiteName = "group.com.example.widgetly.test"
        NSUserDefaults(suiteName = suiteName).setObject("not valid json", "entries_json")
        val storage = IosDdayStorage(suiteName = suiteName)
        assertEquals(emptyList(), storage.readAll())
    }
}
