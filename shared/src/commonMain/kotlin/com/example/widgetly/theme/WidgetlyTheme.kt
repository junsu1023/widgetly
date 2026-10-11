package com.example.widgetly.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import widgetly.shared.generated.resources.Res
import widgetly.shared.generated.resources.do_hyeon_regular
import widgetly.shared.generated.resources.dongle_regular

/**
 * App-shell MaterialTheme wrapper - wraps AppRoot so the pastel/cute design
 * (docs/superpowers/specs/2026-10-11-visual-design-system-design.md) applies
 * uniformly to every screen hosted there (D-day list, pairing, widget
 * configuration activities), on both Android and iOS since AppRoot is shared
 * Compose Multiplatform UI. Widget faces are NOT covered here - they render
 * natively per platform and keep [ThemeCatalog]'s own day/night-independent
 * colors.
 */
@Composable
fun WidgetlyTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) AppColorTokens.dark else AppColorTokens.light
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        background = Color(palette.background),
        surface = Color(palette.surface),
        primary = Color(palette.primary),
        onPrimary = Color(palette.onPrimary),
        onBackground = Color(palette.onBackground),
        onSurface = Color(palette.onBackground),
    )

    val titleFont = FontFamily(Font(Res.font.do_hyeon_regular))
    val bodyFont = FontFamily(Font(Res.font.dongle_regular))
    fun TextStyle.withFont(family: FontFamily) = copy(fontFamily = family)
    // Dongle's x-height is ~0.28 of its em-box (a display-script face, not a text
    // face) versus ~0.53 for the default Roboto this replaces - at the same point
    // size its glyphs read roughly half as large. Scaling font size and line
    // height together keeps it legible at normal reading distance instead of
    // shipping every body/list/label string in the app at an effective ~8sp.
    val dongleBodyScale = 1.75f
    fun TextStyle.withDongleBody() = copy(
        fontFamily = bodyFont,
        fontSize = fontSize * dongleBodyScale,
        lineHeight = lineHeight * dongleBodyScale,
    )
    val defaultTypography = Typography()
    val typography = defaultTypography.copy(
        displayLarge = defaultTypography.displayLarge.withFont(titleFont),
        displayMedium = defaultTypography.displayMedium.withFont(titleFont),
        displaySmall = defaultTypography.displaySmall.withFont(titleFont),
        headlineLarge = defaultTypography.headlineLarge.withFont(titleFont),
        headlineMedium = defaultTypography.headlineMedium.withFont(titleFont),
        headlineSmall = defaultTypography.headlineSmall.withFont(titleFont),
        titleLarge = defaultTypography.titleLarge.withFont(titleFont),
        titleMedium = defaultTypography.titleMedium.withFont(titleFont),
        titleSmall = defaultTypography.titleSmall.withFont(titleFont),
        labelLarge = defaultTypography.labelLarge.withFont(titleFont),
        labelMedium = defaultTypography.labelMedium.withFont(titleFont),
        labelSmall = defaultTypography.labelSmall.withFont(titleFont),
        bodyLarge = defaultTypography.bodyLarge.withDongleBody(),
        bodyMedium = defaultTypography.bodyMedium.withDongleBody(),
        bodySmall = defaultTypography.bodySmall.withDongleBody(),
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = WidgetlyButtonShape,
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
}
