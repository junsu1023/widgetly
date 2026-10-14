package com.example.widgetly.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

private val TextStrong = Color(0xFF342923)
private val TextInactive = Color(0xFF887F7B)
private val TextCaption = Color(0xFF8D8581)
private val PageBackground = Color(0xFFFFFEFC)
private val CardBackground = Color(0xFFFFFFFF)
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

    // Tab filter for which entries to VIEW - independent of the creation form's
    // selectedNewEntryCategory below. Picking a tab must never change what
    // category the next created entry gets.
    var selectedCategory by remember { mutableStateOf<DdayCategory?>(null) }

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

    Scaffold(
        containerColor = PageBackground,
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
                    color = TextStrong,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { addEntryFromForm() }) {
                    Icon(Icons.Filled.Add, contentDescription = "추가", tint = TextStrong)
                }
                // TODO: wire to a real settings/더보기 destination once that screen is designed
                // (out of scope per docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md).
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = "설정", tint = TextStrong)
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
                        color = if (isActive) TextStrong else TextInactive,
                        modifier = Modifier.clickable { selectedCategory = category },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("제목") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = newYear,
                    onValueChange = { newYear = it },
                    label = { Text("년") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = newMonth,
                    onValueChange = { newMonth = it },
                    label = { Text("월") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = newDay,
                    onValueChange = { newDay = it },
                    label = { Text("일") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("기준일을 1일로 세기")
                Switch(checked = countStartAtOne, onCheckedChange = { countStartAtOne = it })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("매년 반복")
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
                                if (isSelected) it.border(2.dp, TextStrong, CircleShape) else it
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
                        color = if (isSelected) TextStrong else TextInactive,
                        modifier = Modifier
                            .clip(WidgetlyButtonShape)
                            .let { if (isSelected) it.background(Color(0xFFFFE2E7)) else it }
                            .clickable { selectedNewEntryCategory = category }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
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
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextStrong,
                                    )
                                    Text(
                                        text = result.label,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextStrong,
                                    )
                                    Text(
                                        text = "${entry.targetDate.year}.${entry.targetDate.monthNumber}.${entry.targetDate.dayOfMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextCaption,
                                    )
                                }
                                mascotIcon(Modifier.size(54.dp))
                            }
                            // Small, subtle delete affordance tucked in the corner so it doesn't
                            // compete with the Figma card layout. Deletes immediately on tap,
                            // matching the old row's "삭제" IconButton which also had no confirmation step.
                            IconButton(
                                onClick = { viewModel.onEvent(DdayEvent.Remove(entry.id)) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "삭제",
                                    tint = TextInactive,
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
