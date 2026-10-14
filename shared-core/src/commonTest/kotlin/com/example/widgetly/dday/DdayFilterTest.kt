package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DdayFilterTest {
    private val coupleEntry = DdayEntry(
        id = "1",
        title = "Our Anniversary",
        targetDate = LocalDate(2026, 12, 25),
        category = DdayCategory.COUPLE,
    )

    private val friendEntry = DdayEntry(
        id = "2",
        title = "Friend's Birthday",
        targetDate = LocalDate(2027, 1, 15),
        category = DdayCategory.FRIEND,
    )

    private val otherEntry = DdayEntry(
        id = "3",
        title = "Other Event",
        targetDate = LocalDate(2027, 2, 28),
        category = DdayCategory.OTHER,
    )

    private val entries = listOf(coupleEntry, friendEntry, otherEntry)

    @Test
    fun `null category returns every entry unchanged`() {
        val result = filterByCategory(entries, null)
        assertEquals(entries, result)
    }

    @Test
    fun `a specific category returns only matching entries`() {
        val result = filterByCategory(entries, DdayCategory.COUPLE)
        assertEquals(listOf(coupleEntry), result)
    }

    @Test
    fun `a category with no matching entries returns an empty list`() {
        val otherOnlyList = listOf(otherEntry)
        val result = filterByCategory(otherOnlyList, DdayCategory.COUPLE)
        assertEquals(emptyList(), result)
    }
}
