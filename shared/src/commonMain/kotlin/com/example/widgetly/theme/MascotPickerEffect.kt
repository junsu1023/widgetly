package com.example.widgetly.theme

sealed interface MascotPickerEffect {
    data class ShowError(val message: String) : MascotPickerEffect
}
