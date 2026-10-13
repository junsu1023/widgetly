package com.example.widgetly.theme

import com.example.widgetly.connection.ConnectionType
import com.example.widgetly.connection.SendSignalResult
import com.example.widgetly.connection.WidgetlyFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryMascotPreferenceStorage(initial: String = MascotCatalog.DEFAULT_MASCOT_ID) : MascotPreferenceStorage {
    var stored: String = initial
    override suspend fun read(): String = stored
    override suspend fun write(mascotId: String) {
        stored = mascotId
    }
}

private class FakeWidgetlyFunctions : WidgetlyFunctions {
    val updateMascotCalls = mutableListOf<Pair<String, String>>()
    var shouldThrow: Boolean = false

    override suspend fun registerFcmToken(token: String, platform: String) = error("not used")
    override suspend fun createPairingCode(type: ConnectionType): String = error("not used")
    override suspend fun redeemPairingCode(code: String): String = error("not used")
    override suspend fun sendSignal(connectionId: String, preset: String, signalId: String): SendSignalResult = error("not used")
    override suspend fun updateMascot(connectionId: String, mascotId: String) {
        if (shouldThrow) error("updateMascot failed")
        updateMascotCalls += connectionId to mascotId
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MascotPickerViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state reflects the storage's current value on init`() = runTest {
        val storage = InMemoryMascotPreferenceStorage(initial = "cat")
        val viewModel = MascotPickerViewModel(storage, FakeWidgetlyFunctions())
        assertEquals("cat", viewModel.state.value.selectedMascotId)
    }

    @Test
    fun `Select with connectionId null saves locally and never calls updateMascot`() = runTest {
        val storage = InMemoryMascotPreferenceStorage()
        val functions = FakeWidgetlyFunctions()
        val viewModel = MascotPickerViewModel(storage, functions)

        viewModel.onEvent(MascotPickerEvent.Select("bear", connectionId = null))

        assertEquals("bear", viewModel.state.value.selectedMascotId)
        assertEquals("bear", storage.stored)
        assertTrue(functions.updateMascotCalls.isEmpty())
    }

    @Test
    fun `Select with a connectionId saves locally and calls updateMascot with the same id`() = runTest {
        val storage = InMemoryMascotPreferenceStorage()
        val functions = FakeWidgetlyFunctions()
        val viewModel = MascotPickerViewModel(storage, functions)

        viewModel.onEvent(MascotPickerEvent.Select("rabbit", connectionId = "conn-1"))

        assertEquals("rabbit", viewModel.state.value.selectedMascotId)
        assertEquals(listOf("conn-1" to "rabbit"), functions.updateMascotCalls)
    }

    @Test
    fun `a failed updateMascot emits ShowError but the local selection still sticks`() = runTest {
        val storage = InMemoryMascotPreferenceStorage()
        val functions = FakeWidgetlyFunctions().apply { shouldThrow = true }
        val viewModel = MascotPickerViewModel(storage, functions)

        viewModel.onEvent(MascotPickerEvent.Select("hamster", connectionId = "conn-1"))

        assertEquals("hamster", viewModel.state.value.selectedMascotId)
        assertTrue(viewModel.effects.first() is MascotPickerEffect.ShowError)
    }
}
