package com.example.widgetly.dday

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class CountMode { START_AT_ZERO, START_AT_ONE }

@Serializable
enum class RepeatMode { NONE, YEARLY }

@Serializable
data class DdayEntry(
    val id: String,
    val title: String,
    val targetDate: LocalDate,
    val countMode: CountMode = CountMode.START_AT_ZERO,
    val repeat: RepeatMode = RepeatMode.NONE,
)
