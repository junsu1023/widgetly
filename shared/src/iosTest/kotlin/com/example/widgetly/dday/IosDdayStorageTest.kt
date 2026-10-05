package com.example.widgetly.dday

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
}
