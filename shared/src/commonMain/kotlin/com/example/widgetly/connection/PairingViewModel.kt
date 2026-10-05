package com.example.widgetly.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    object Idle : PairingUiState
    data class CodeCreated(val code: String) : PairingUiState
    data class Connected(val connectionId: String) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

/**
 * Drives the pairing code creation/redemption screen. Both `functions` calls go
 * through the server's callable Cloud Functions (see WidgetlyFunctions' doc
 * comment) - this ViewModel just turns their result/failure into UI state.
 */
class PairingViewModel(private val functions: WidgetlyFunctions) : ViewModel() {
    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state

    fun createCode(type: ConnectionType) {
        viewModelScope.launch {
            _state.value = runCatching { functions.createPairingCode(type) }
                .fold(
                    onSuccess = { PairingUiState.CodeCreated(it) },
                    onFailure = { PairingUiState.Error(it.message ?: "코드 생성에 실패했습니다") },
                )
        }
    }

    fun redeem(code: String) {
        viewModelScope.launch {
            _state.value = runCatching { functions.redeemPairingCode(code) }
                .fold(
                    onSuccess = { PairingUiState.Connected(it) },
                    onFailure = { PairingUiState.Error(it.message ?: "코드 연결에 실패했습니다") },
                )
        }
    }
}
