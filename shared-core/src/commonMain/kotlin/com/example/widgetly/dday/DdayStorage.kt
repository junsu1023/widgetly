package com.example.widgetly.dday

interface DdayStorage {
    suspend fun readAll(): List<DdayEntry>
    suspend fun writeAll(entries: List<DdayEntry>)
}
