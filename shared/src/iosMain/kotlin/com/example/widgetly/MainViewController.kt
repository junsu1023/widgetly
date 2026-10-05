package com.example.widgetly

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.dday.IosDdayStorage

fun MainViewController() = ComposeUIViewController {
    DdayRoot()
}

@Composable
private fun DdayRoot() {
    val viewModel: DdayViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DdayViewModel(DdayRepository(IosDdayStorage())) }
        },
    )
    DdayListScreen(viewModel)
}
