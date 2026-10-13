package com.example.widgetly.theme

import platform.Foundation.NSUserDefaults

class IosMascotPreferenceStorage(private val suiteName: String = "group.com.example.widgetly") : MascotPreferenceStorage {
    private val selectedMascotIdKey = "selected_mascot_id"

    override suspend fun read(): String {
        val defaults = NSUserDefaults(suiteName = suiteName)
        return defaults.stringForKey(selectedMascotIdKey) ?: MascotCatalog.DEFAULT_MASCOT_ID
    }

    override suspend fun write(mascotId: String) {
        val defaults = NSUserDefaults(suiteName = suiteName)
        defaults.setObject(mascotId, selectedMascotIdKey)
    }
}
