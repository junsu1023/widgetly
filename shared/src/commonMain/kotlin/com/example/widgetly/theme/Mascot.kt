package com.example.widgetly.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import widgetly.shared.generated.resources.Res
import widgetly.shared.generated.resources.bear
import widgetly.shared.generated.resources.cat
import widgetly.shared.generated.resources.hamster
import widgetly.shared.generated.resources.quokka
import widgetly.shared.generated.resources.rabbit

/**
 * Maps a `MascotCatalog` id to its generated drawable resource. Compose
 * Multiplatform resource accessors (Res.drawable.*) are compile-time
 * generated vals, not resolvable by a dynamic string, so this `when` is the
 * one place that bridges the two - same fallback discipline as
 * `MascotCatalog.find`: an unrecognized id never crashes, it falls back to
 * the original quokka.
 */
fun mascotDrawable(mascotId: String): DrawableResource = when (mascotId) {
    "cat" -> Res.drawable.cat
    "bear" -> Res.drawable.bear
    "rabbit" -> Res.drawable.rabbit
    "hamster" -> Res.drawable.hamster
    else -> Res.drawable.quokka
}

/**
 * The app's mascot, drawn faint in a screen corner as a brand decoration -
 * not shown at full strength so it never competes with real content. Widget
 * faces render it separately, natively per platform, only on their empty
 * states (see DdayGlanceWidget.kt/ConnectionGlanceWidget.kt and the iOS
 * widget extension).
 */
@Composable
fun MascotBackground(mascotId: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(mascotDrawable(mascotId)),
        contentDescription = null,
        modifier = modifier.size(120.dp).alpha(0.18f),
    )
}
