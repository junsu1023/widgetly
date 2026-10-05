package com.example.widgetly.dday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Suppress("DEPRECATION")
@Composable
fun DdayListScreen(viewModel: DdayViewModel) {
    val entries by viewModel.entries.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var newTitle by remember { mutableStateOf("") }
    var newYear by remember { mutableStateOf(today.year.toString()) }
    var newMonth by remember { mutableStateOf(today.monthNumber.toString()) }
    var newDay by remember { mutableStateOf(today.dayOfMonth.toString()) }
    var countStartAtOne by remember { mutableStateOf(false) }
    var repeatYearly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                )
                OutlinedTextField(
                    value = newMonth,
                    onValueChange = { newMonth = it },
                    label = { Text("월") },
                )
                OutlinedTextField(
                    value = newDay,
                    onValueChange = { newDay = it },
                    label = { Text("일") },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("기준일을 1일로 세기")
                Switch(checked = countStartAtOne, onCheckedChange = { countStartAtOne = it })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("매년 반복")
                Switch(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = {
                    val entry = buildDdayEntry(
                        id = Random.nextLong().toString(),
                        title = newTitle,
                        year = newYear.toIntOrNull(),
                        month = newMonth.toIntOrNull(),
                        day = newDay.toIntOrNull(),
                        countStartAtOne = countStartAtOne,
                        repeatYearly = repeatYearly,
                    )
                    if (entry != null) {
                        viewModel.upsert(entry)
                        newTitle = ""
                    }
                }) {
                    Text("추가")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    val result = calculateDday(entry, today)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${entry.title}: ${result.label}")
                        IconButton(onClick = { viewModel.remove(entry.id) }) {
                            Text("삭제")
                        }
                    }
                }
            }
        }
    }
}
