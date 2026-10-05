package com.example.widgetly.dday

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

class IosDdayStorage(private val suiteName: String = "group.com.example.widgetly") : DdayStorage {
    private val entriesKey = "entries_json"

    override suspend fun readAll(): List<DdayEntry> {
        val defaults = NSUserDefaults(suiteName = suiteName)
        val json = defaults.stringForKey(entriesKey) ?: return emptyList()
        return Json.decodeFromString(json)
    }

    override suspend fun writeAll(entries: List<DdayEntry>) {
        val defaults = NSUserDefaults(suiteName = suiteName)
        defaults.setObject(Json.encodeToString(entries), entriesKey)
    }
}
