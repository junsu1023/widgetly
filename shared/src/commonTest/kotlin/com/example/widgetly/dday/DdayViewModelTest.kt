package com.example.widgetly.dday

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val sampleEntry = DdayEntry(
    id = "sample-1",
    title = "sample",
    targetDate = LocalDate(2026, 12, 25),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DdayViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh loads entries from the repository`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        val viewModel = DdayViewModel(repo)
        viewModel.refresh()
        assertEquals(listOf(sampleEntry), viewModel.entries.value)
    }

    @Test
    fun `upsert adds the entry and reflects it in entries`() = runTest {
        val viewModel = DdayViewModel(DdayRepository(FakeDdayStorage()))
        viewModel.upsert(sampleEntry)
        assertEquals(listOf(sampleEntry), viewModel.entries.value)
    }
}
