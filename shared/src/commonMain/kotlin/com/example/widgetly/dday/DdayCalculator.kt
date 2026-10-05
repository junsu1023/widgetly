package com.example.widgetly.dday

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.until

data class DdayResult(val label: String, val calendarDiffDays: Int)

fun calculateDday(entry: DdayEntry, today: LocalDate): DdayResult {
    val effectiveTarget = effectiveTargetDate(entry, today)
    val calendarDiffDays = today.until(effectiveTarget, DateTimeUnit.DAY)

    val oneIfStartAtOne = if (entry.countMode == CountMode.START_AT_ONE) 1 else 0
    val label = when {
        calendarDiffDays == 0 -> "D-DAY"
        calendarDiffDays > 0 -> "D-${calendarDiffDays + oneIfStartAtOne}"
        else -> "D+${-calendarDiffDays + oneIfStartAtOne}"
    }
    return DdayResult(label, calendarDiffDays)
}

private fun effectiveTargetDate(entry: DdayEntry, today: LocalDate): LocalDate {
    if (entry.repeat != RepeatMode.YEARLY) return entry.targetDate

    val thisYearOccurrence = occurrenceInYear(entry.targetDate, today.year)
    return if (thisYearOccurrence >= today) {
        thisYearOccurrence
    } else {
        occurrenceInYear(entry.targetDate, today.year + 1)
    }
}

private fun occurrenceInYear(targetDate: LocalDate, year: Int): LocalDate {
    val day = if (targetDate.monthNumber == 2 && targetDate.dayOfMonth == 29 && !isLeapYear(year)) {
        28
    } else {
        targetDate.dayOfMonth
    }
    return LocalDate(year, targetDate.monthNumber, day)
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
