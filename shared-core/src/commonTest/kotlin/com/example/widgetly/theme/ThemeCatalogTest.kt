package com.example.widgetly.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeCatalogTest {
    @Test
    fun `catalog contains the default theme`() {
        assertTrue(ThemeCatalog.all.any { it.id == ThemeCatalog.DEFAULT_THEME_ID })
    }

    @Test
    fun `all theme ids are unique`() {
        val ids = ThemeCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `find falls back to the default theme for an unknown id`() {
        val result = ThemeCatalog.find("does-not-exist")
        assertEquals(ThemeCatalog.DEFAULT_THEME_ID, result.id)
    }

    @Test
    fun `find returns the matching theme when the id exists`() {
        val known = ThemeCatalog.all.first { it.id != ThemeCatalog.DEFAULT_THEME_ID }
        assertEquals(known, ThemeCatalog.find(known.id))
    }

    @Test
    fun `catalog has exactly the four v2 themes with the right default`() {
        assertEquals("peach", ThemeCatalog.DEFAULT_THEME_ID)
        assertEquals(setOf("peach", "lemon", "skyblue", "cream"), ThemeCatalog.all.map { it.id }.toSet())
    }

    @Test
    fun `an old pre-redesign theme id falls back to the default instead of crashing`() {
        // lavender/mint/sunset were removed entirely - this must resolve like any
        // other unknown id, not throw.
        assertEquals(ThemeCatalog.DEFAULT_THEME_ID, ThemeCatalog.find("lavender").id)
        assertEquals(ThemeCatalog.DEFAULT_THEME_ID, ThemeCatalog.find("sunset").id)
    }

    // Pins the exact hex values from the design spec - the earlier tests only check
    // ids/uniqueness/fallback, so a mistyped color digit would ship silently without
    // these.
    @Test
    fun `peach theme has the spec's exact colors`() {
        val peach = ThemeCatalog.find("peach")
        assertEquals(0xFFFFF0E5L, peach.backgroundColorArgb)
        assertEquals(0xFF7A3B12L, peach.foregroundColorArgb)
    }

    @Test
    fun `lemon theme has the spec's exact colors`() {
        val lemon = ThemeCatalog.find("lemon")
        assertEquals(0xFFFFFBE0L, lemon.backgroundColorArgb)
        assertEquals(0xFF6B5800L, lemon.foregroundColorArgb)
    }

    @Test
    fun `skyblue theme has the spec's exact colors`() {
        val skyblue = ThemeCatalog.find("skyblue")
        assertEquals(0xFFE6F4FFL, skyblue.backgroundColorArgb)
        assertEquals(0xFF1B4B73L, skyblue.foregroundColorArgb)
    }

    @Test
    fun `cream theme has the spec's exact colors`() {
        val cream = ThemeCatalog.find("cream")
        assertEquals(0xFFFFF8EEL, cream.backgroundColorArgb)
        assertEquals(0xFF5C4A33L, cream.foregroundColorArgb)
    }
}
