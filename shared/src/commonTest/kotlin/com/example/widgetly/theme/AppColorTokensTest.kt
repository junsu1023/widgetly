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
}
