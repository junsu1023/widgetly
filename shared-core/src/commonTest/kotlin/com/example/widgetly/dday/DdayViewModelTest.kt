package com.example.widgetly.dday

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    fun `Refresh loads entries from the repository`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        val viewModel = DdayViewModel(repo)
        viewModel.onEvent(DdayEvent.Refresh)
        assertEquals(listOf(sampleEntry), viewModel.state.value.entries)
    }

    @Test
    fun `Upsert adds the entry and reflects it in state`() = runTest {
        val viewModel = DdayViewModel(DdayRepository(FakeDdayStorage()))
        viewModel.onEvent(DdayEvent.Upsert(sampleEntry))
        assertEquals(listOf(sampleEntry), viewModel.state.value.entries)
    }

    @Test
    fun `a rejected Upsert does not crash the viewModelScope and leaves state unchanged`() = runTest {
        val viewModel = DdayViewModel(DdayRepository(FakeDdayStorage()))
        viewModel.onEvent(DdayEvent.Upsert(sampleEntry.copy(title = "   ")))
        assertEquals(emptyList(), viewModel.state.value.entries)
    }

    @Test
    fun `a rejected Upsert emits a ShowError effect`() = runTest {
        val viewModel = DdayViewModel(DdayRepository(FakeDdayStorage()))
        viewModel.onEvent(DdayEvent.Upsert(sampleEntry.copy(title = "   ")))
        assertEquals(true, viewModel.effects.first() is DdayEffect.ShowError)
    }

    @Test
    fun `Remove deletes the entry and reflects it in state`() = runTest {
        val repo = DdayRepository(FakeDdayStorage(listOf(sampleEntry)))
        val viewModel = DdayViewModel(repo)
        viewModel.onEvent(DdayEvent.Refresh)
        viewModel.onEvent(DdayEvent.Remove(sampleEntry.id))
        assertEquals(emptyList(), viewModel.state.value.entries)
    }
}
