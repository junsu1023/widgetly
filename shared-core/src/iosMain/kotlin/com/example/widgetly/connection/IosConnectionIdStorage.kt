package com.example.widgetly.connection

import platform.Foundation.NSUserDefaults

class IosConnectionIdStorage(private val suiteName: String = "group.com.example.widgetly") : ConnectionIdStorage {
    private val activeConnectionIdKey = "active_connection_id"

    override suspend fun read(): String? {
        val defaults = NSUserDefaults(suiteName = suiteName)
        return defaults.stringForKey(activeConnectionIdKey)
    }

    override suspend fun write(connectionId: String) {
        val defaults = NSUserDefaults(suiteName = suiteName)
        defaults.setObject(connectionId, activeConnectionIdKey)
    }
}
