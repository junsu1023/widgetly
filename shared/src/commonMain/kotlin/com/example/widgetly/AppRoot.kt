package com.example.widgetly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.widgetly.connection.PairingScreen
import com.example.widgetly.connection.PairingUiState
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.theme.MascotBackground
import com.example.widgetly.theme.MascotPickerScreen
import com.example.widgetly.theme.MascotPickerViewModel
import com.example.widgetly.theme.WidgetlyButtonShape
import com.example.widgetly.theme.WidgetlyTheme

private enum class AppScreen { DDAY_LIST, PAIRING, MASCOT_PICKER }

/**
 * Minimal screen-switching container - no navigation library. The app starts
 * on the personal D-day list (the existing MVP entry point); two buttons
 * toggle over to the couple/friend pairing screen and the mascot picker and
 * back. Local `var currentScreen` state is all this task calls for.
 */
@Composable
fun AppRoot(
    ddayViewModel: DdayViewModel,
    pairingViewModel: PairingViewModel,
    mascotPickerViewModel: MascotPickerViewModel,
) {
    var currentScreen by remember { mutableStateOf(AppScreen.DDAY_LIST) }
    val pairingState by pairingViewModel.state.collectAsState()
    val currentConnectionId = (pairingState as? PairingUiState.Connected)?.connectionId

    WidgetlyTheme {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            currentScreen = if (currentScreen == AppScreen.DDAY_LIST) AppScreen.PAIRING else AppScreen.DDAY_LIST
                        },
                        shape = WidgetlyButtonShape,
                    ) {
                        Text(if (currentScreen == AppScreen.DDAY_LIST) "커플/친구 연결하기" else "디데이 목록으로")
                    }
                    Button(
                        onClick = { currentScreen = AppScreen.MASCOT_PICKER },
                        shape = WidgetlyButtonShape,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("마스코트 선택")
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                // On top, not behind: DdayListScreen/PairingScreen/MascotPickerScreen
                // each wrap their content in a Scaffold with an opaque background,
                // which would otherwise cover anything drawn underneath in this Box.
                // A plain Image with no click handler doesn't intercept touches, so
                // sitting on top at low alpha in an empty corner is safe.
                when (currentScreen) {
                    AppScreen.DDAY_LIST -> DdayListScreen(ddayViewModel)
                    AppScreen.PAIRING -> PairingScreen(pairingViewModel)
                    AppScreen.MASCOT_PICKER -> MascotPickerScreen(mascotPickerViewModel, currentConnectionId)
                }
                MascotBackground(
                    mascotPickerViewModel.state.collectAsState().value.selectedMascotId,
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}
