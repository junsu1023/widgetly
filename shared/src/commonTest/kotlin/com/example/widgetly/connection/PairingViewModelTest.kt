package com.example.widgetly.connection

import androidx.lifecycle.viewModelScope
import com.example.widgetly.push.FakeWidgetlyFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createCode exposes the returned code as CodeCreated`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextCode = "123456" }
        val viewModel = PairingViewModel(fake)
        viewModel.createCode(ConnectionType.FRIEND)
        assertEquals(PairingUiState.CodeCreated("123456"), viewModel.state.value)
    }

    @Test
    fun `redeem exposes the returned connectionId as Connected`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextConnectionId = "conn-1" }
        val viewModel = PairingViewModel(fake)
        viewModel.redeem("123456")
        assertEquals(PairingUiState.Connected("conn-1"), viewModel.state.value)
    }

    @Test
    fun `a failed redeem exposes an Error state instead of throwing`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { shouldThrow = true }
        val viewModel = PairingViewModel(fake)
        viewModel.redeem("000000")
        assertTrue(viewModel.state.value is PairingUiState.Error)
    }

    @Test
    fun `cancelling viewModelScope mid-createCode propagates cancellation instead of becoming Error`() = runTest {
        // delayMillis gives cancel() a suspension point to interrupt - without it the
        // fake call completes synchronously and there's nothing to cancel mid-flight.
        val fake = FakeWidgetlyFunctions().apply { nextCode = "123456"; delayMillis = 1_000 }
        val viewModel = PairingViewModel(fake)

        viewModel.createCode(ConnectionType.FRIEND)
        val job = viewModel.viewModelScope.coroutineContext[Job]!!
        viewModel.viewModelScope.cancel()

        assertTrue(job.isCancelled)
        assertEquals(PairingUiState.Idle, viewModel.state.value)
    }
}
