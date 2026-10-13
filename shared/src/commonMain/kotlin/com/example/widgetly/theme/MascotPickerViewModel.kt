package com.example.widgetly.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.widgetly.connection.WidgetlyFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MascotPickerViewModel(
    private val storage: MascotPreferenceStorage,
    private val functions: WidgetlyFunctions,
) : ViewModel() {
    private val _state = MutableStateFlow(MascotPickerState())
    val state: StateFlow<MascotPickerState> = _state

    // BUFFERED so a ShowError sent before the screen starts collecting isn't dropped.
    private val _effects = Channel<MascotPickerEffect>(Channel.BUFFERED)
    val effects: Flow<MascotPickerEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(selectedMascotId = storage.read()) }
        }
    }

    fun onEvent(event: MascotPickerEvent) {
        when (event) {
            is MascotPickerEvent.Select -> select(event.mascotId, event.connectionId)
        }
    }

    private fun select(mascotId: String, connectionId: String?) {
        viewModelScope.launch {
            // Save locally first so the UI reflects the tap immediately regardless
            // of network outcome; a failed sync below does not revert this.
            storage.write(mascotId)
            _state.update { it.copy(selectedMascotId = mascotId) }

            if (connectionId != null) {
                try {
                    functions.updateMascot(connectionId, mascotId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _effects.send(MascotPickerEffect.ShowError(e.message ?: "마스코트 동기화에 실패했습니다"))
                }
            }
        }
    }
}
