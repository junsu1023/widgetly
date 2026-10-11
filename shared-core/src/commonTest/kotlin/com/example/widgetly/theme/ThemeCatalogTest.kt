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
}
