package com.example.widgetly.dday

sealed interface DdayEvent {
    object Refresh : DdayEvent
    data class Upsert(val entry: DdayEntry) : DdayEvent
    data class Remove(val id: String) : DdayEvent
}
