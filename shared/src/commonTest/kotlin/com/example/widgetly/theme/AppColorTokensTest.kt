package com.example.widgetly.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppColorTokensTest {
    @Test
    fun `primary accent is identical across light and dark`() {
        assertEquals(AppColorTokens.light.primary, AppColorTokens.dark.primary)
    }

    @Test
    fun `background is distinct between light and dark`() {
        assertNotEquals(AppColorTokens.light.background, AppColorTokens.dark.background)
    }

    // Pins the exact hex values from the design spec, per platform - the two tests
    // above would pass for any arbitrary pair of wrong palettes.
    @Test
    fun `light palette has the spec's exact colors`() {
        assertEquals(0xFFFFF4E8L, AppColorTokens.light.background)
        assertEquals(0xFFFFE7CCL, AppColorTokens.light.surface)
        assertEquals(0xFFFFA45CL, AppColorTokens.light.primary)
        assertEquals(0xFF4A2F14L, AppColorTokens.light.onPrimary)
        assertEquals(0xFF4A2F14L, AppColorTokens.light.onBackground)
    }

    @Test
    fun `dark palette has the spec's exact colors`() {
        assertEquals(0xFF2E2015L, AppColorTokens.dark.background)
        assertEquals(0xFF3F2C18L, AppColorTokens.dark.surface)
        assertEquals(0xFFFFA45CL, AppColorTokens.dark.primary)
        assertEquals(0xFF2E2015L, AppColorTokens.dark.onPrimary)
        assertEquals(0xFFFFE6C7L, AppColorTokens.dark.onBackground)
    }
}
