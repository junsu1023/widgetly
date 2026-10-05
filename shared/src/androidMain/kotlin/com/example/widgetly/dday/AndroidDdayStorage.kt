package com.example.widgetly.dday

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.ddayDataStore by preferencesDataStore(name = "dday_entries")
private val ENTRIES_KEY = stringPreferencesKey("entries_json")

class AndroidDdayStorage(private val context: Context) : DdayStorage {
    override suspend fun readAll(): List<DdayEntry> {
        val prefs: Preferences = context.ddayDataStore.data.first()
        val json = prefs[ENTRIES_KEY] ?: return emptyList()
        return Json.decodeFromString(json)
    }

    override suspend fun writeAll(entries: List<DdayEntry>) {
        context.ddayDataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = Json.encodeToString(entries)
        }
    }
}
