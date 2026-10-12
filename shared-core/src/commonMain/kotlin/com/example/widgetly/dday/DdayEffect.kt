package com.example.widgetly.dday

sealed interface DdayEffect {
    data class ShowError(val message: String) : DdayEffect
}
