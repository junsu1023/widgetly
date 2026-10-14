package com.example.widgetly.connection

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosConnectionIdStorageTest {
    @Test
    fun `read is null before any write then round-trips after one`() = runTest {
        val storage = IosConnectionIdStorage(suiteName = "group.com.example.widgetly.test")
        assertNull(storage.read())

        storage.write("conn-1")
        assertEquals("conn-1", storage.read())
    }
}
