package com.example.widgetly.dday

import kotlinx.datetime.LocalDate

fun buildDdayEntry(
    id: String,
    title: String,
    year: Int?,
    month: Int?,
    day: Int?,
    countStartAtOne: Boolean,
    repeatYearly: Boolean,
    category: DdayCategory = DdayCategory.OTHER,
    icon: DdayIcon = DdayIcon.HEART,
): DdayEntry? {
    if (title.isBlank()) return null
    val y = year ?: return null
    val m = month ?: return null
    val d = day ?: return null
    val targetDate = try {
        LocalDate(y, m, d)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return DdayEntry(
        id = id,
        title = title,
        targetDate = targetDate,
        countMode = if (countStartAtOne) CountMode.START_AT_ONE else CountMode.START_AT_ZERO,
        repeat = if (repeatYearly) RepeatMode.YEARLY else RepeatMode.NONE,
        category = category,
        icon = icon,
    )
}
