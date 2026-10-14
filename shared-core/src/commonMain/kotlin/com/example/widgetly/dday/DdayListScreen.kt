package com.example.widgetly.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.widgetly.theme.WidgetlyButtonShape
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * This screen's own light/dark color set. The light values are the Figma
 * pixel-samples from the home-screen redesign spec, which deliberately
 * diverge from `shared`'s `AppColorTokens` (warmer, nearly white) and have no
 * dark variant in the design file - the dark values below are derived the same
 * way `AppColorTokens.light`/`dark` relate to each other (warm near-white
 * backgrounds become warm near-black, dark text becomes cream, hue preserved).
 *
 * Kept local rather than pulled from `AppColorTokens` because this file lives
 * in `shared-core`, which has zero dependency on `shared` by design.
 * Everything this screen draws must resolve its color from here, never from
 * the ambient MaterialTheme - the ambient scheme flips with system dark mode
 * and would land light text on this screen's near-white background.
 */
private class ScreenColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val textStrong: Color,
    val textInactive: Color,
    val textCaption: Color,
)

private val LightScreenColors = ScreenColors(
    pageBackground = Color(0xFFFFFEFC),
    cardBackground = Color(0xFFFFFFFF),
    textStrong = Color(0xFF342923),
    textInactive = Color(0xFF887F7B),
    textCaption = Color(0xFF8D8581),
)

private val DarkScreenColors = ScreenColors(
    pageBackground = Color(0xFF2B2420),
    cardBackground = Color(0xFF3A2E22),
    textStrong = Color(0xFFFFE6C7),
    textInactive = Color(0xFFB8A99C),
    textCaption = Color(0xFFB0A69C),
)

private val CardShape = RoundedCornerShape(20.dp)

private fun DdayIcon.toImageVector(): ImageVector = when (this) {
    DdayIcon.HEART -> Icons.Filled.Favorite
    DdayIcon.BOOK -> Icons.Filled.MenuBook
    DdayIcon.CAKE -> Icons.Filled.Cake
    DdayIcon.PLANE -> Icons.Filled.Flight
}

private fun DdayCategory.label(): String = when (this) {
    DdayCategory.COUPLE -> "커플"
    DdayCategory.FRIEND -> "친구"
    DdayCategory.OTHER -> "기타"
}

private val categoryTabs: List<Pair<String, DdayCategory?>> = listOf(
    "전체" to null,
    "커플" to DdayCategory.COUPLE,
    "친구" to DdayCategory.FRIEND,
    "기타" to DdayCategory.OTHER,
)

@OptIn(ExperimentalTime::class)
@Suppress("DEPRECATION")
@Composable
fun DdayListScreen(viewModel: DdayViewModel, mascotIcon: @Composable (Modifier) -> Unit) {
    val uiState by viewModel.state.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val colors = if (isSystemInDarkTheme()) DarkScreenColors else LightScreenColors

    // Tab filter for which entries to VIEW - independent of the creation form's
    // selectedNewEntryCategory below. Picking a tab must never change what
    // category the next created entry gets.
    var selectedCategory by remember { mutableStateOf<DdayCategory?>(null) }

    // The creation form is collapsed by default - expanded it takes roughly
    // 500dp, which on a typical phone would leave almost nothing for the card
    // list this screen exists to show. The top-bar + toggles it.
    var showForm by remember { mutableStateOf(false) }

    var newTitle by remember { mutableStateOf("") }
    var newYear by remember { mutableStateOf(today.year.toString()) }
    var newMonth by remember { mutableStateOf(today.monthNumber.toString()) }
    var newDay by remember { mutableStateOf(today.dayOfMonth.toString()) }
    var countStartAtOne by remember { mutableStateOf(false) }
    var repeatYearly by remember { mutableStateOf(false) }
    var selectedIcon by remember { mutableStateOf(DdayIcon.HEART) }
    var selectedNewEntryCategory by remember { mutableStateOf(DdayCategory.OTHER) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.onEvent(DdayEvent.Refresh)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DdayEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    fun addEntryFromForm() {
        val entry = buildDdayEntry(
            id = Random.nextLong().toString(),
            title = newTitle,
            year = newYear.toIntOrNull(),
            month = newMonth.toIntOrNull(),
            day = newDay.toIntOrNull(),
            countStartAtOne = countStartAtOne,
            repeatYearly = repeatYearly,
            category = selectedNewEntryCategory,
            icon = selectedIcon,
        )
        if (entry != null) {
            viewModel.onEvent(DdayEvent.Upsert(entry))
            newTitle = ""
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textStrong,
        unfocusedTextColor = colors.textStrong,
        focusedLabelColor = colors.textStrong,
        unfocusedLabelColor = colors.textInactive,
        cursorColor = colors.textStrong,
        focusedBorderColor = colors.textStrong,
        unfocusedBorderColor = colors.textInactive,
    )

    Scaffold(
        containerColor = colors.pageBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "D-day!",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textStrong,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showForm = !showForm }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = if (showForm) "디데이 추가 폼 닫기" else "디데이 추가",
                        tint = colors.textStrong,
                    )
                }
                // TODO: wire to a real settings/더보기 destination once that screen is designed
                // (out of scope per docs/superpowers/specs/2026-10-14-home-screen-redesign.md).
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = "설정", tint = colors.textStrong)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                categoryTabs.forEach { (label, category) ->
                    val isActive = selectedCategory == category
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) colors.textStrong else colors.textInactive,
                        modifier = Modifier.clickable { selectedCategory = category },
                    )
                }
            }

            if (showForm) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("제목") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = newYear,
                        onValueChange = { newYear = it },
                        label = { Text("년") },
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = newMonth,
                        onValueChange = { newMonth = it },
                        label = { Text("월") },
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = newDay,
                        onValueChange = { newDay = it },
                        label = { Text("일") },
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("기준일을 1일로 세기", color = colors.textStrong)
                    Switch(checked = countStartAtOne, onCheckedChange = { countStartAtOne = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("매년 반복", color = colors.textStrong)
                    Switch(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DdayIcon.entries.forEach { icon ->
                        val isSelected = icon == selectedIcon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(icon.badgeColorArgb))
                                .let {
                                    if (isSelected) it.border(2.dp, colors.textStrong, CircleShape) else it
                                }
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon.toImageVector(), contentDescription = null, tint = Color(icon.glyphColorArgb))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DdayCategory.entries.forEach { category ->
                        val isSelected = category == selectedNewEntryCategory
                        Text(
                            text = category.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.textStrong else colors.textInactive,
                            modifier = Modifier
                                .clip(WidgetlyButtonShape)
                                .let { if (isSelected) it.background(Color(0xFFFFE2E7)) else it }
                                .clickable { selectedNewEntryCategory = category }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                // Submit sits with the fields it submits, not up in the top bar.
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Button(onClick = { addEntryFromForm() }) {
                        Text("추가")
                    }
                }
            }

            val visibleEntries = filterByCategory(uiState.entries, selectedCategory)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(visibleEntries, key = { it.id }) { entry ->
                    val result = calculateDday(entry, today)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(entry.icon.badgeColorArgb)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    entry.icon.toImageVector(),
                                    contentDescription = null,
                                    tint = Color(entry.icon.glyphColorArgb),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textStrong,
                                )
                                Text(
                                    text = result.label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textStrong,
                                )
                                Text(
                                    text = "${entry.targetDate.year}.${entry.targetDate.monthNumber}.${entry.targetDate.dayOfMonth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textCaption,
                                )
                            }
                            mascotIcon(Modifier.size(54.dp))
                            // Small, subtle delete affordance at the end of the row so Row's
                            // left-to-right layout rules out any overlap with mascotIcon.
                            // The glyph stays small (16dp) but the IconButton keeps its
                            // default ~48dp touch target - this is the only way to delete
                            // an entry in the app, so it must not be under the minimum tap
                            // size. Deletes immediately on tap, matching the old row's
                            // "삭제" IconButton which also had no confirmation step.
                            IconButton(onClick = { viewModel.onEvent(DdayEvent.Remove(entry.id)) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "삭제",
                                    tint = colors.textInactive,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
