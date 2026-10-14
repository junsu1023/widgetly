package com.example.widgetly

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.widgetly.connection.PairingScreen
import com.example.widgetly.connection.PairingUiState
import com.example.widgetly.connection.PairingViewModel
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayViewModel
import com.example.widgetly.theme.MascotBackground
import com.example.widgetly.theme.MascotPickerScreen
import com.example.widgetly.theme.MascotPickerViewModel
import com.example.widgetly.theme.WidgetlyTheme
import com.example.widgetly.theme.mascotDrawable
import org.jetbrains.compose.resources.painterResource

private enum class AppScreen { HOME, WIDGET_GALLERY, THEME, MORE }

private data class BottomNavItem(val screen: AppScreen, val icon: ImageVector, val label: String)

private val bottomNavItems = listOf(
    BottomNavItem(AppScreen.HOME, Icons.Filled.Home, "홈"),
    BottomNavItem(AppScreen.WIDGET_GALLERY, Icons.Filled.GridView, "위젯갤러리"),
    BottomNavItem(AppScreen.THEME, Icons.Filled.Palette, "테마"),
    BottomNavItem(AppScreen.MORE, Icons.Filled.MoreHoriz, "더보기"),
)

/**
 * 4-tab bottom navigation container - no navigation library, just local
 * `var currentScreen` state switching between the four top-level
 * destinations named in the home-screen redesign spec.
 */
@Composable
fun AppRoot(
    ddayViewModel: DdayViewModel,
    pairingViewModel: PairingViewModel,
    mascotPickerViewModel: MascotPickerViewModel,
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val pairingState by pairingViewModel.state.collectAsState()
    val currentConnectionId = (pairingState as? PairingUiState.Connected)?.connectionId

    // Defaults to MascotCatalog.DEFAULT_MASCOT_ID (quokka) until the async
    // read resolves, per MascotPickerState's existing default - the mascot
    // icon below never has a missing/blank state to render.
    val myMascotId = mascotPickerViewModel.state.collectAsState().value.selectedMascotId

    @Composable
    fun mascotIcon(modifier: Modifier) {
        Image(
            painter = painterResource(mascotDrawable(myMascotId)),
            contentDescription = null,
            modifier = modifier,
        )
    }

    WidgetlyTheme {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    AppScreen.HOME -> DdayListScreen(ddayViewModel, mascotIcon = ::mascotIcon)
                    AppScreen.WIDGET_GALLERY -> WidgetGalleryScreen()
                    AppScreen.THEME -> MascotPickerScreen(mascotPickerViewModel, currentConnectionId)
                    AppScreen.MORE -> PairingScreen(pairingViewModel)
                }
                if (currentScreen != AppScreen.HOME) {
                    MascotBackground(myMascotId, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                }
            }
            // Explicit surface - Material3's default NavigationBar container is
            // surfaceContainer, a slot WidgetlyTheme never overrides, so it
            // would render as an off-theme lavender-gray under the warm palette.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomNavItems.forEach { item ->
                    val isActive = currentScreen == item.screen
                    NavigationBarItem(
                        selected = isActive,
                        onClick = { currentScreen = item.screen },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}
