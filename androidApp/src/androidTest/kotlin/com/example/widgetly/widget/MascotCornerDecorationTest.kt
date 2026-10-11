package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import org.junit.Test

class MascotCornerDecorationTest {
    @Test
    fun showsOneMascotAtTheSmallestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable { MascotCornerDecoration() }
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(1)
    }

    @Test
    fun showsTwoMascotsAtTheMediumSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_MEDIUM)
        provideComposable { MascotCornerDecoration() }
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(2)
    }

    @Test
    fun showsThreeMascotsAtTheLargestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_LARGE)
        provideComposable { MascotCornerDecoration() }
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(3)
    }
}
