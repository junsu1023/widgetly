package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import com.example.widgetly.dday.DdayEntry
import kotlinx.datetime.LocalDate
import org.junit.Test

private val sampleEntry = DdayEntry(
    id = "entry-1",
    title = "생일",
    targetDate = LocalDate(2026, 1, 1),
)

/**
 * Renders DdayWidgetContent directly with already-resolved values, same
 * pattern as ConnectionGlanceWidgetTest - isolated composable test, no real
 * WidgetlyApplication.ddayRepository lookup.
 */
class DdayGlanceWidgetTest {
    @Test
    fun showsDdayLabelAndCornerMascotWhenEntryExists() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable {
            DdayWidgetContent(entryId = sampleEntry.id, entryState = sampleEntry, themeId = null)
        }
        onNode(hasText("생일")).assertExists()
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(1)
    }

    @Test
    fun showsSetupNeededWithoutCornerMascotWhenNoEntryIsConfigured() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable {
            DdayWidgetContent(entryId = null, entryState = null, themeId = null)
        }
        onNode(hasText("설정 필요")).assertExists()
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(0)
    }

    @Test
    fun showsDeletedWithoutCornerMascotWhenEntryCannotBeFound() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable {
            DdayWidgetContent(entryId = sampleEntry.id, entryState = null, themeId = null)
        }
        onNode(hasText("삭제됨")).assertExists()
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(0)
    }
}
