package com.example.widgetly

import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.widgetly.connection.FirebaseWidgetlyFunctions
import com.example.widgetly.connection.IosConnectionIdStorage
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.dday.IosDdayStorage
import com.example.widgetly.theme.IosMascotPreferenceStorage
import com.example.widgetly.theme.MascotPickerViewModel
import com.example.widgetly.theme.MascotPreferenceStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.functions.functions

/**
 * [onMascotChanged] is called after this device's mascot selection is saved, so
 * the caller can ask WidgetKit to re-run both widgets' timelines
 * (`WidgetCenter.shared.reloadAllTimelines()`) - the iOS counterpart of
 * WidgetlyApplication.refreshMascotWidgets() on Android. It has to come in from
 * Swift: Kotlin/Native ships no WidgetKit platform library, so there is no
 * WidgetCenter binding reachable from here.
 */
fun MainViewController(onMascotChanged: () -> Unit) = ComposeUIViewController {
    val ddayViewModel: DdayViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DdayViewModel(DdayRepository(IosDdayStorage())) }
        },
    )
    val pairingViewModel: PairingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PairingViewModel(
                    FirebaseWidgetlyFunctions(Firebase.functions(Firebase.app)),
                    IosConnectionIdStorage(),
                )
            }
        },
    )
    val mascotPickerViewModel: MascotPickerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MascotPickerViewModel(
                    // Wrapped at the wiring site, exactly like
                    // WidgetlyApplication.mascotPreferenceStorage on Android: the
                    // ViewModel stays unaware that a write also pokes the widgets.
                    object : MascotPreferenceStorage {
                        private val storage = IosMascotPreferenceStorage()
                        override suspend fun read(): String = storage.read()
                        override suspend fun write(mascotId: String) {
                            storage.write(mascotId)
                            onMascotChanged()
                        }
                    },
                    FirebaseWidgetlyFunctions(Firebase.functions(Firebase.app)),
                )
            }
        },
    )
    AppRoot(ddayViewModel, pairingViewModel, mascotPickerViewModel)
}
