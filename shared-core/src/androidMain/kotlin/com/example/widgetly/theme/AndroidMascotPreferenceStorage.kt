package com.example.widgetly.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val Context.mascotPrefsDataStore by preferencesDataStore(name = "mascot_prefs")
internal val SELECTED_MASCOT_ID_KEY = stringPreferencesKey("selected_mascot_id")

class AndroidMascotPreferenceStorage(private val context: Context) : MascotPreferenceStorage {
    override suspend fun read(): String {
        val prefs = context.mascotPrefsDataStore.data.first()
        return prefs[SELECTED_MASCOT_ID_KEY] ?: MascotCatalog.DEFAULT_MASCOT_ID
    }

    override suspend fun write(mascotId: String) {
        context.mascotPrefsDataStore.edit { prefs ->
            prefs[SELECTED_MASCOT_ID_KEY] = mascotId
        }
    }
}
