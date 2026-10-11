package com.example.widgetly.theme

/**
 * App-shell (not widget) color tokens - light/dark variants for the screens
 * AppRoot hosts (D-day list, pairing, widget configuration). Widget faces
 * keep their own user-chosen [Theme] regardless of system dark mode; these
 * tokens are a separate axis.
 */
data class AppColorPalette(
    val background: Long,
    val surface: Long,
    val primary: Long,
    val onPrimary: Long,
    val onBackground: Long,
)

object AppColorTokens {
    val light = AppColorPalette(
        background = 0xFFFFF4E8L,
        surface = 0xFFFFE7CCL,
        primary = 0xFFFFA45CL,
        onPrimary = 0xFF4A2F14L,
        onBackground = 0xFF4A2F14L,
    )
    val dark = AppColorPalette(
        background = 0xFF2E2015L,
        surface = 0xFF3F2C18L,
        primary = 0xFFFFA45CL,
        onPrimary = 0xFF2E2015L,
        onBackground = 0xFFFFE6C7L,
    )
}
