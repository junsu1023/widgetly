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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryConnectionIdStorage(var stored: String? = null) : ConnectionIdStorage {
    override suspend fun read(): String? = stored
    override suspend fun write(connectionId: String) {
        stored = connectionId
    }
}

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
    fun `CreateCode exposes the returned code as CodeCreated`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextCode = "123456" }
        val viewModel = PairingViewModel(fake, InMemoryConnectionIdStorage())
        viewModel.onEvent(PairingEvent.CreateCode(ConnectionType.FRIEND))
        assertEquals(PairingUiState.CodeCreated("123456"), viewModel.state.value)
    }

    @Test
    fun `Redeem exposes the returned connectionId as Connected`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextConnectionId = "conn-1" }
        val viewModel = PairingViewModel(fake, InMemoryConnectionIdStorage())
        viewModel.onEvent(PairingEvent.Redeem("123456"))
        assertEquals(PairingUiState.Connected("conn-1"), viewModel.state.value)
    }

    @Test
    fun `a failed Redeem exposes an Error state instead of throwing`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { shouldThrow = true }
        val viewModel = PairingViewModel(fake, InMemoryConnectionIdStorage())
        viewModel.onEvent(PairingEvent.Redeem("000000"))
        assertTrue(viewModel.state.value is PairingUiState.Error)
    }

    @Test
    fun `cancelling viewModelScope mid-CreateCode propagates cancellation instead of becoming Error`() = runTest {
        // delayMillis gives cancel() a suspension point to interrupt - without it the
        // fake call completes synchronously and there's nothing to cancel mid-flight.
        val fake = FakeWidgetlyFunctions().apply { nextCode = "123456"; delayMillis = 1_000 }
        val viewModel = PairingViewModel(fake, InMemoryConnectionIdStorage())

        viewModel.onEvent(PairingEvent.CreateCode(ConnectionType.FRIEND))
        val job = viewModel.viewModelScope.coroutineContext[Job]!!
        viewModel.viewModelScope.cancel()

        assertTrue(job.isCancelled)
        assertEquals(PairingUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `init restores Connected from the stored connectionId`() = runTest {
        val viewModel = PairingViewModel(FakeWidgetlyFunctions(), InMemoryConnectionIdStorage("conn-stored"))
        assertEquals(PairingUiState.Connected("conn-stored"), viewModel.state.value)
    }

    @Test
    fun `init stays Idle when no connectionId has been stored`() = runTest {
        val storage = InMemoryConnectionIdStorage()
        val viewModel = PairingViewModel(FakeWidgetlyFunctions(), storage)
        assertEquals(PairingUiState.Idle, viewModel.state.value)
        assertNull(storage.stored)
    }

    @Test
    fun `a successful Redeem persists the connectionId`() = runTest {
        val fake = FakeWidgetlyFunctions().apply { nextConnectionId = "conn-2" }
        val storage = InMemoryConnectionIdStorage()
        val viewModel = PairingViewModel(fake, storage)

        viewModel.onEvent(PairingEvent.Redeem("123456"))

        assertEquals("conn-2", storage.stored)
        assertEquals(PairingUiState.Connected("conn-2"), viewModel.state.value)
    }
}
