package com.example.widgetly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.widgetly.MainActivity
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.dday.DdayEntry
import kotlinx.coroutines.launch

class DdayWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            var entries by remember { mutableStateOf<List<DdayEntry>>(emptyList()) }
            LaunchedEffect(Unit) {
                entries = WidgetlyApplication.ddayRepository.list()
            }
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("위젯에 표시할 디데이를 선택하세요") }) },
                ) { padding ->
                    if (entries.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("아직 만든 디데이가 없어요.")
                            Button(
                                onClick = { startActivity(Intent(this@DdayWidgetConfigurationActivity, MainActivity::class.java)) },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text("앱에서 디데이 만들기")
                            }
                            Button(
                                onClick = { finish() },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text("취소")
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                            items(entries, key = { it.id }) { entry ->
                                Text(
                                    text = entry.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { confirmSelection(entry.id) }
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirmSelection(entryId: String) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        lifecycleScope.launch {
            saveSelectedEntry(this@DdayWidgetConfigurationActivity, glanceId, entryId)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    companion object {
        suspend fun saveSelectedEntry(context: Context, glanceId: GlanceId, entryId: String) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[DDAY_ENTRY_ID_KEY] = entryId
            }
            DdayGlanceWidget().update(context, glanceId)
        }
    }
}
