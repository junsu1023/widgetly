package com.example.widgetly.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidConnectionIdStorageTest {
    @Test
    fun `read is null before any write then round-trips after one`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidConnectionIdStorage(context)
        assertNull(storage.read())

        storage.write("conn-1")
        assertEquals("conn-1", storage.read())
    }
}
