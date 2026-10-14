package com.example.widgetly.connection

/**
 * Remembers the connection this device is paired through, across launches.
 *
 * Pairing itself only ever learned the connectionId in memory (PairingViewModel's
 * redeem() result, or the id typed into ConnectionWidgetConfigurationActivity), so
 * anything needing it later - sharing the selected mascot through the
 * `updateMascot` callable, for instance - silently did nothing after a restart,
 * and never worked at all on the inviting device. Persisting it here is what makes
 * those paths fire.
 *
 * [read] returns null when nothing has been stored yet: unlike
 * MascotPreferenceStorage there is no sensible default connectionId, and "not
 * paired (as far as this device knows)" is exactly what callers must handle.
 */
interface ConnectionIdStorage {
    suspend fun read(): String?
    suspend fun write(connectionId: String)
}
