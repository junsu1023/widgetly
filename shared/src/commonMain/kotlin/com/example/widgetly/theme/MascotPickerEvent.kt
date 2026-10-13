package com.example.widgetly.theme

sealed interface MascotPickerEvent {
    // connectionId travels on the event itself (not held by the ViewModel) so an
    // unpaired selection (null) can be told apart from a paired one without this
    // ViewModel tracking pairing state of its own.
    data class Select(val mascotId: String, val connectionId: String?) : MascotPickerEvent
}
