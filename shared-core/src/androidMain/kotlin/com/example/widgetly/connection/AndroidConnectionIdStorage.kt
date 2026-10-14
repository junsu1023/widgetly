package com.example.widgetly.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val Context.connectionIdPrefsDataStore by preferencesDataStore(name = "connection_id_prefs")
internal val ACTIVE_CONNECTION_ID_KEY = stringPreferencesKey("active_connection_id")

class AndroidConnectionIdStorage(private val context: Context) : ConnectionIdStorage {
    override suspend fun read(): String? {
        val prefs = context.connectionIdPrefsDataStore.data.first()
        return prefs[ACTIVE_CONNECTION_ID_KEY]
    }

    override suspend fun write(connectionId: String) {
        context.connectionIdPrefsDataStore.edit { prefs ->
            prefs[ACTIVE_CONNECTION_ID_KEY] = connectionId
        }
    }
}
