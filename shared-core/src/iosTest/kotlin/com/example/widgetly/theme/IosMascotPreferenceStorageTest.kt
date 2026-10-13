package com.example.widgetly.theme

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IosMascotPreferenceStorageTest {
    @Test
    fun `read defaults to quokka then round-trips after a write`() = runTest {
        val storage = IosMascotPreferenceStorage(suiteName = "group.com.example.widgetly.test")
        assertEquals(MascotCatalog.DEFAULT_MASCOT_ID, storage.read())

        storage.write("cat")
        assertEquals("cat", storage.read())
    }
}
