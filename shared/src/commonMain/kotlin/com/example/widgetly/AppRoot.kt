package com.example.widgetly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.widgetly.connection.PairingScreen
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.theme.WidgetlyButtonShape
import com.example.widgetly.theme.WidgetlyTheme

private enum class AppScreen { DDAY_LIST, PAIRING }

/**
 * Minimal screen-switching container - no navigation library. The app starts
 * on the personal D-day list (the existing MVP entry point); a single button
 * toggles over to the couple/friend pairing screen and back. Local `var
 * currentScreen` state is all this task calls for.
 */
@Composable
fun AppRoot(ddayViewModel: DdayViewModel, pairingViewModel: PairingViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.DDAY_LIST) }

    WidgetlyTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = {
                    currentScreen = if (currentScreen == AppScreen.DDAY_LIST) AppScreen.PAIRING else AppScreen.DDAY_LIST
                },
                shape = WidgetlyButtonShape,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(if (currentScreen == AppScreen.DDAY_LIST) "커플/친구 연결하기" else "디데이 목록으로")
            }
            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    AppScreen.DDAY_LIST -> DdayListScreen(ddayViewModel)
                    AppScreen.PAIRING -> PairingScreen(pairingViewModel)
                }
            }
        }
    }
}
