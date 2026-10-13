package com.example.widgetly.theme

interface MascotPreferenceStorage {
    suspend fun read(): String
    suspend fun write(mascotId: String)
}
