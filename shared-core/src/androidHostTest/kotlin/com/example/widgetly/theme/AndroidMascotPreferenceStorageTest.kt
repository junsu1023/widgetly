package com.example.widgetly.theme

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMascotPreferenceStorageTest {
    @Test
    fun `read defaults to quokka then round-trips after a write`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidMascotPreferenceStorage(context)
        assertEquals(MascotCatalog.DEFAULT_MASCOT_ID, storage.read())

        storage.write("cat")
        assertEquals("cat", storage.read())
    }
}
