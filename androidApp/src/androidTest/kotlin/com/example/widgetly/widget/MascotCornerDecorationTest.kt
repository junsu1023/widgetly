package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import com.example.widgetly.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MascotCornerDecorationTest {
    @Test
    fun showsOneMascotAtTheSmallestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable { MascotCornerDecoration("cat") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(1)
    }

    @Test
    fun showsTwoMascotsAtTheMediumSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_MEDIUM)
        provideComposable { MascotCornerDecoration("cat") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(2)
    }

    @Test
    fun showsThreeMascotsAtTheLargestSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_LARGE)
        provideComposable { MascotCornerDecoration("cat") }
        onAllNodes(hasContentDescriptionEqualTo("cat_corner")).assertCountEquals(3)
    }

    @Test
    fun fallsBackToTheQuokkaDrawableForAnUnrecognizedMascotId() {
        assertEquals(R.drawable.quokka, mascotDrawableRes("not-a-real-mascot"))
        assertEquals(R.drawable.quokka_faint, mascotFaintDrawableRes("not-a-real-mascot"))
    }
}
