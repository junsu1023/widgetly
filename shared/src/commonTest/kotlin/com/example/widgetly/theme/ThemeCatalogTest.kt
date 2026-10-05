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
}
