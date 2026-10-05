package com.example.widgetly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
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
            Column {
                entries.forEach { entry ->
                    Text(
                        text = entry.title,
                        modifier = androidx.compose.ui.Modifier.clickable { confirmSelection(entry.id) },
                    )
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
