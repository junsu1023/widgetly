package com.example.widgetly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val ddayViewModel: DdayViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DdayViewModel(WidgetlyApplication.ddayRepository) }
                },
            )
            val pairingViewModel: PairingViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PairingViewModel(WidgetlyApplication.widgetlyFunctions) }
                },
            )
            AppRoot(ddayViewModel, pairingViewModel)
        }
    }
}