package com.example.widgetly.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import com.example.widgetly.R

// Matches dday_widget_info.xml / connection_widget_info.xml's declared
// minWidth/minHeight and maxResizeWidth/maxResizeHeight, with one size in
// between - the three buckets SizeMode.Responsive offers these widgets.
internal val MASCOT_SIZE_SMALL = DpSize(110.dp, 40.dp)
internal val MASCOT_SIZE_MEDIUM = DpSize(180.dp, 110.dp)
internal val MASCOT_SIZE_LARGE = DpSize(250.dp, 180.dp)
internal val MASCOT_RESPONSIVE_SIZES = setOf(MASCOT_SIZE_SMALL, MASCOT_SIZE_MEDIUM, MASCOT_SIZE_LARGE)

internal fun mascotCountForSize(size: DpSize): Int = when (size) {
    MASCOT_SIZE_LARGE -> 3
    MASCOT_SIZE_MEDIUM -> 2
    else -> 1
}

/**
 * Maps a MascotCatalog id to its non-faint drawable, falling back to quokka
 * for any unrecognized id (stale selection, bad data) - same fallback
 * discipline as MascotCatalog.find.
 */
internal fun mascotDrawableRes(id: String): Int = when (id) {
    "cat" -> R.drawable.cat
    "bear" -> R.drawable.bear
    "rabbit" -> R.drawable.rabbit
    "hamster" -> R.drawable.hamster
    "quokka" -> R.drawable.quokka
    else -> R.drawable.quokka
}

/** Faint-variant counterpart of [mascotDrawableRes], same fallback. */
internal fun mascotFaintDrawableRes(id: String): Int = when (id) {
    "cat" -> R.drawable.cat_faint
    "bear" -> R.drawable.bear_faint
    "rabbit" -> R.drawable.rabbit_faint
    "hamster" -> R.drawable.hamster_faint
    "quokka" -> R.drawable.quokka_faint
    else -> R.drawable.quokka_faint
}

/**
 * A row of faint mascots pinned to the bottom-end corner, scaling 1-3 with
 * the widget's current size (requires SizeMode.Responsive(MASCOT_RESPONSIVE_SIZES)
 * on the host GlanceAppWidget so LocalSize.current reflects the real resize).
 * Shown only once real data (D-day label, streak) is on screen - the empty
 * states already have their own prominent mascot via EmptyStateWithMascot.
 *
 * The faint variant (not the plain one): Glance's Image has no runtime alpha/
 * opacity modifier (only a tint ColorFilter), so the faded look is baked into a
 * separate asset ahead of time (see the drawable-nodpi "_faint" variants).
 */
@Composable
internal fun MascotCornerDecoration(mascotId: String) {
    val count = mascotCountForSize(LocalSize.current)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Row {
            repeat(count) {
                Image(
                    provider = ImageProvider(mascotFaintDrawableRes(mascotId)),
                    contentDescription = "${mascotId}_corner",
                    modifier = GlanceModifier.size(20.dp),
                )
            }
        }
    }
}
