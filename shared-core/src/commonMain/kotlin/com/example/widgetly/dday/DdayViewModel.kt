package com.example.widgetly.dday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DdayUiState(val entries: List<DdayEntry> = emptyList())

class DdayViewModel(private val repository: DdayRepository) : ViewModel() {
    private val _state = MutableStateFlow(DdayUiState())
    val state: StateFlow<DdayUiState> = _state

    // BUFFERED (not RENDEZVOUS): an effect sent before a screen has started
    // collecting (e.g. a fast test, or recomposition between events) must not
    // be dropped silently - a one-off "저장 실패" notification the user never
    // sees is worse than one that arrives a beat late.
    private val _effects = Channel<DdayEffect>(Channel.BUFFERED)
    val effects: Flow<DdayEffect> = _effects.receiveAsFlow()

    fun onEvent(event: DdayEvent) {
        when (event) {
            DdayEvent.Refresh -> refresh()
            is DdayEvent.Upsert -> upsert(event.entry)
            is DdayEvent.Remove -> remove(event.id)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(entries = repository.list()) }
        }
    }

    private fun upsert(entry: DdayEntry) {
        viewModelScope.launch {
            val result = runCatching { repository.upsert(entry) }
            _state.update { it.copy(entries = repository.list()) }
            result.onFailure {
                _effects.send(DdayEffect.ShowError(it.message ?: "저장에 실패했습니다"))
            }
        }
    }

    private fun remove(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(entries = repository.list()) }
        }
    }
}
