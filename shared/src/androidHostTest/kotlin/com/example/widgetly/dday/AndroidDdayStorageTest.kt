package com.example.widgetly.dday

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDdayStorageTest {
    @Test
    fun `writeAll then readAll round-trips entries`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidDdayStorage(context)
        storage.writeAll(listOf(sampleEntry))
        assertEquals(listOf(sampleEntry), storage.readAll())
    }
}
