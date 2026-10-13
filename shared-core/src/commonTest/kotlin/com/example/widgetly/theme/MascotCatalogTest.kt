package com.example.widgetly.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MascotCatalogTest {
    @Test
    fun `catalog contains the default mascot`() {
        assertTrue(MascotCatalog.all.any { it.id == MascotCatalog.DEFAULT_MASCOT_ID })
    }

    @Test
    fun `all mascot ids are unique`() {
        val ids = MascotCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `find falls back to the default mascot for an unknown id`() {
        val result = MascotCatalog.find("does-not-exist")
        assertEquals(MascotCatalog.DEFAULT_MASCOT_ID, result.id)
    }

    @Test
    fun `find returns the matching mascot when the id exists`() {
        val known = MascotCatalog.all.first { it.id != MascotCatalog.DEFAULT_MASCOT_ID }
        assertEquals(known, MascotCatalog.find(known.id))
    }

    @Test
    fun `catalog has exactly the five mascots with the right names and default`() {
        assertEquals("quokka", MascotCatalog.DEFAULT_MASCOT_ID)
        assertEquals(
            mapOf("quokka" to "쿼카", "cat" to "고양이", "bear" to "곰", "rabbit" to "토끼", "hamster" to "햄스터"),
            MascotCatalog.all.associate { it.id to it.name },
        )
    }
}
