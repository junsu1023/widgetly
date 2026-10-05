package com.example.widgetly.dday

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDdayStorageTest {
    @Test
    fun `writeAll then readAll round-trips entries`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AndroidDdayStorage(context)
        storage.writeAll(listOf(sampleEntry))
        assertEquals(listOf(sampleEntry), storage.readAll())
    }

    @Test
    fun `readAll tolerates an unknown field added by a future version`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.ddayDataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = """[{"id":"sample-1","title":"sample","targetDate":"2026-12-25","countMode":"START_AT_ZERO","repeat":"NONE","futureField":"ignored"}]"""
        }
        val storage = AndroidDdayStorage(context)
        assertEquals(listOf(sampleEntry), storage.readAll())
    }

    @Test
    fun `readAll returns an empty list instead of throwing on corrupted data`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.ddayDataStore.edit { prefs ->
            prefs[ENTRIES_KEY] = "not valid json"
        }
        val storage = AndroidDdayStorage(context)
        assertEquals(emptyList(), storage.readAll())
    }
}
