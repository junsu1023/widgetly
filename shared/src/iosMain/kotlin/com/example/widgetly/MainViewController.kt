package com.example.widgetly

import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.widgetly.connection.FirebaseWidgetlyFunctions
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.dday.IosDdayStorage
import com.example.widgetly.theme.IosMascotPreferenceStorage
import com.example.widgetly.theme.MascotPickerViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.functions.functions

fun MainViewController() = ComposeUIViewController {
    val ddayViewModel: DdayViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DdayViewModel(DdayRepository(IosDdayStorage())) }
        },
    )
    val pairingViewModel: PairingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PairingViewModel(FirebaseWidgetlyFunctions(Firebase.functions(Firebase.app))) }
        },
    )
    val mascotPickerViewModel: MascotPickerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MascotPickerViewModel(IosMascotPreferenceStorage(), FirebaseWidgetlyFunctions(Firebase.functions(Firebase.app)))
            }
        },
    )
    AppRoot(ddayViewModel, pairingViewModel, mascotPickerViewModel)
}
