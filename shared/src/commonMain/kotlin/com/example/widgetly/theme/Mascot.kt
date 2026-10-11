package com.example.widgetly.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import widgetly.shared.generated.resources.Res
import widgetly.shared.generated.resources.quokka

/**
 * The app's original quokka mascot, drawn faint in a screen corner as a brand
 * decoration - not shown at full strength so it never competes with real
 * content. Widget faces render it separately, natively per platform, only on
 * their empty states (see DdayGlanceWidget.kt/ConnectionGlanceWidget.kt and
 * the iOS widget extension).
 */
@Composable
fun MascotBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.quokka),
        contentDescription = null,
        modifier = modifier.size(120.dp).alpha(0.18f),
    )
}
