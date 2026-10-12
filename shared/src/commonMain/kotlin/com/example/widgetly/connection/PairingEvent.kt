package com.example.widgetly.connection

sealed interface PairingEvent {
    data class CreateCode(val type: ConnectionType) : PairingEvent
    data class Redeem(val code: String) : PairingEvent
}
