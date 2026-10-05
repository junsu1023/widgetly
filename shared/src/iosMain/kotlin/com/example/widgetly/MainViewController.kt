package com.example.widgetly

import androidx.compose.ui.window.ComposeUIViewController
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.dday.IosDdayStorage

fun MainViewController() = ComposeUIViewController {
    DdayListScreen(DdayViewModel(DdayRepository(IosDdayStorage())))
}