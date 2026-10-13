package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import org.junit.Test

/**
 * Unlike MascotCornerDecoration (1-3 icons by size), ConnectionMascotCornerDecoration
 * always shows exactly 2 icons - one per connection member - at every size bucket.
 * Only the icon's dp size scales with the widget's resize bucket.
 */
class ConnectionMascotCornerDecorationTest {
    @Test
    fun showsBothMascotsExactlyOnceAtTheSmallestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable { ConnectionMascotCornerDecoration("cat", "bear") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("bear_corner")).assertCountEquals(1)
    }

    @Test
    fun showsBothMascotsExactlyOnceAtTheMediumSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_MEDIUM)
        provideComposable { ConnectionMascotCornerDecoration("cat", "bear") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("bear_corner")).assertCountEquals(1)
    }

    @Test
    fun showsBothMascotsExactlyOnceAtTheLargestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_LARGE)
        provideComposable { ConnectionMascotCornerDecoration("cat", "bear") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("bear_corner")).assertCountEquals(1)
    }

    @Test
    fun fallsBackToQuokkaForThePartnerWhenMascotIdsAreTheUnrecognizedFallback() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        // Mirrors the real fallback: partner hasn't picked a character yet, so the
        // caller passes MascotCatalog.DEFAULT_MASCOT_ID ("quokka") for them while
        // "me" has a real pick - both icons must still render (no blank/missing icon).
        provideComposable { ConnectionMascotCornerDecoration("cat", "quokka") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(1)
    }
}
