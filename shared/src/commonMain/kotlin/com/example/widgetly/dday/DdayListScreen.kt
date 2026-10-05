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
@Composable
fun DdayListScreen(viewModel: DdayViewModel) {
    val entries by viewModel.entries.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Button(onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.upsert(
                            DdayEntry(
                                id = Random.nextLong().toString(),
                                title = newTitle,
                                targetDate = today,
                            ),
                        )
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
