package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class CountMode { START_AT_ZERO, START_AT_ONE }

@Serializable
enum class RepeatMode { NONE, YEARLY }

@Serializable
enum class DdayCategory { COUPLE, FRIEND, OTHER }

@Serializable
enum class DdayIcon(val badgeColorArgb: Long, val glyphColorArgb: Long) {
    HEART(0xFFFFE2E7L, 0xFFF17C92L),
    BOOK(0xFFE3EDFFL, 0xFFF17C92L),
    CAKE(0xFFFFE9DDL, 0xFFF17C92L),
    PLANE(0xFFE4EEFFL, 0xFFF17C92L),
}

@Serializable
data class DdayEntry(
    val id: String,
    val title: String,
    val targetDate: LocalDate,
    val countMode: CountMode = CountMode.START_AT_ZERO,
    val repeat: RepeatMode = RepeatMode.NONE,
    val category: DdayCategory = DdayCategory.OTHER,
    val icon: DdayIcon = DdayIcon.HEART,
)
