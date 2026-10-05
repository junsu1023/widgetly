package com.example.widgetly.dday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DdayViewModel(private val repository: DdayRepository) : ViewModel() {
    private val _entries = MutableStateFlow<List<DdayEntry>>(emptyList())
    val entries: StateFlow<List<DdayEntry>> = _entries

    fun refresh() {
        viewModelScope.launch {
            _entries.value = repository.list()
        }
    }

    fun upsert(entry: DdayEntry) {
        viewModelScope.launch {
            runCatching { repository.upsert(entry) }
            _entries.value = repository.list()
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _entries.value = repository.list()
        }
    }
}
