package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DdayEntryFormTest {
    @Test
    fun `builds an entry from valid form input`() {
        val entry = buildDdayEntry(
            id = "1",
            title = "수능",
            year = 2026,
            month = 11,
            day = 12,
            countStartAtOne = true,
            repeatYearly = false,
        )
        assertEquals(
            DdayEntry("1", "수능", LocalDate(2026, 11, 12), CountMode.START_AT_ONE, RepeatMode.NONE),
            entry,
        )
    }

    @Test
    fun `repeatYearly true maps to RepeatMode YEARLY`() {
        val entry = buildDdayEntry(
            id = "1",
            title = "생일",
            year = 2026,
            month = 3,
            day = 1,
            countStartAtOne = false,
            repeatYearly = true,
        )
        assertEquals(RepeatMode.YEARLY, entry?.repeat)
    }

    @Test
    fun `blank title returns null`() {
        val entry = buildDdayEntry(
            id = "1",
            title = "   ",
            year = 2026,
            month = 1,
            day = 1,
            countStartAtOne = false,
            repeatYearly = false,
        )
        assertNull(entry)
    }

    @Test
    fun `missing date field returns null`() {
        val entry = buildDdayEntry(
            id = "1",
            title = "t",
            year = 2026,
            month = null,
            day = 1,
            countStartAtOne = false,
            repeatYearly = false,
        )
        assertNull(entry)
    }

    @Test
    fun `invalid calendar date returns null`() {
        val entry = buildDdayEntry(
            id = "1",
            title = "t",
            year = 2026,
            month = 2,
            day = 30,
            countStartAtOne = false,
            repeatYearly = false,
        )
        assertNull(entry)
    }
}
