package com.example.widgetly

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.theme.AndroidMascotPreferenceStorage
import com.example.widgetly.theme.MascotPickerViewModel

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Needed for WidgetlyFirebaseMessagingService's pairing-success
        // notification (Fix Round 1, task-4 review) to actually post on API 33+ -
        // without this, NotificationManagerCompat.notify() silently no-ops.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
            val mascotPickerViewModel: MascotPickerViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MascotPickerViewModel(AndroidMascotPreferenceStorage(applicationContext), WidgetlyApplication.widgetlyFunctions)
                    }
                },
            )
            AppRoot(ddayViewModel, pairingViewModel, mascotPickerViewModel)
        }
    }
}