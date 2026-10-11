package com.example.widgetly.dday

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

class FakeDdayStorage(initial: List<DdayEntry> = emptyList()) : DdayStorage {
    private var entries = initial
    override suspend fun readAll() = entries
    override suspend fun writeAll(entries: List<DdayEntry>) { this.entries = entries }
}

class DdayRepositoryTest {
    @Test
    fun `upsert replaces an existing entry with the same id instead of duplicating it`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry.copy(title = "old"))))
        repo.upsert(sampleEntry.copy(title = "new"))
        val result = repo.list()
        assertEquals(1, result.size)
        assertEquals("new", result.single().title)
    }

    @Test
    fun `upsert rejects a blank title`() = runTest {
        val repo = DdayRepository(FakeDdayStorage())
        assertFailsWith<IllegalArgumentException> { repo.upsert(sampleEntry.copy(title = "   ")) }
    }

    @Test
    fun `delete removes the entry with the given id`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        repo.delete(sampleEntry.id)
        assertEquals(emptyList(), repo.list())
    }

    @Test
    fun `upsert notifies the onChanged callback`() = runTest {
        var notifiedCount = 0
        val repo = DdayRepository(FakeDdayStorage()) { notifiedCount++ }
        repo.upsert(sampleEntry)
        assertEquals(1, notifiedCount)
    }

    @Test
    fun `delete notifies the onChanged callback`() = runTest {
        var notifiedCount = 0
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry))) { notifiedCount++ }
        repo.delete(sampleEntry.id)
        assertEquals(1, notifiedCount)
    }

    @Test
    fun `a rejected upsert does not notify onChanged`() = runTest {
        var notifiedCount = 0
        val repo = DdayRepository(FakeDdayStorage()) { notifiedCount++ }
        runCatching { repo.upsert(sampleEntry.copy(title = "   ")) }
        assertEquals(0, notifiedCount)
    }
}
