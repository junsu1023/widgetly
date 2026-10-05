package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DdayCalculatorTest {
    @Test
    fun `today is D-DAY`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 4), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D-DAY", 0), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `future date with START_AT_ZERO`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 7), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D-3", 3), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `future date with START_AT_ONE adds one to the displayed number`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 10, 7), CountMode.START_AT_ONE, RepeatMode.NONE)
        assertEquals(DdayResult("D-4", 3), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `past date counts up as D+n`() {
        val entry = DdayEntry("1", "t", LocalDate(2026, 9, 29), CountMode.START_AT_ZERO, RepeatMode.NONE)
        assertEquals(DdayResult("D+5", -5), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `yearly repeat rolls over to next year once this year's date has passed`() {
        val entry = DdayEntry("1", "bday", LocalDate(1999, 10, 1), CountMode.START_AT_ZERO, RepeatMode.YEARLY)
        // 2026-10-01은 이미 지났으므로 2027-10-01까지 계산
        assertEquals(DdayResult("D-362", 362), calculateDday(entry, LocalDate(2026, 10, 4)))
    }

    @Test
    fun `yearly repeat on a leap-day birthday falls back to Feb 28 in a non-leap year`() {
        val entry = DdayEntry("1", "leap", LocalDate(2000, 2, 29), CountMode.START_AT_ZERO, RepeatMode.YEARLY)
        // 2027은 윤년이 아님 -> 2/28로 대체, today=2027-02-28 -> D-DAY
        assertEquals(DdayResult("D-DAY", 0), calculateDday(entry, LocalDate(2027, 2, 28)))
    }
}
