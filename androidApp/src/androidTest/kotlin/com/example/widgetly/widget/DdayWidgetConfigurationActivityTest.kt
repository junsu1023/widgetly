package com.example.widgetly.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.platform.app.InstrumentationRegistry
import com.example.widgetly.dday.DdayEntry
import com.example.widgetly.theme.ThemeCatalog
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

class DdayWidgetConfigurationActivityTest {
    @Test
    fun selectingAnEntryStoresItsIdInTheWidgetStateForThatGlanceId() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glanceId = AppWidgetId(123456)
        DdayWidgetConfigurationActivity.saveSelectedEntry(context, glanceId, sampleEntry.id)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        assertEquals(sampleEntry.id, state[stringPreferencesKey("dday_entry_id")])
    }

    @Test
    fun selectingAThemeStoresItsIdInTheWidgetStateForThatGlanceId() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glanceId = AppWidgetId(123457)
        val theme = ThemeCatalog.all.first { it.id != ThemeCatalog.DEFAULT_THEME_ID }
        DdayWidgetConfigurationActivity.saveSelectedTheme(context, glanceId, theme.id)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        assertEquals(theme.id, state[stringPreferencesKey("theme_id")])
    }
}
