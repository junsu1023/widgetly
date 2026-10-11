package com.example.widgetly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.widgetly.theme.Theme
import com.example.widgetly.theme.ThemeCatalog
import com.example.widgetly.theme.WidgetlyTheme
import kotlinx.coroutines.launch

/**
 * Picks which connection this widget instance shows. Unlike the personal D-day
 * widget's config activity, there's no local "list my connections" source to pick
 * from yet - ConnectionRepository only supports observing a single known id
 * (Task 1), and pairing doesn't persist the connectionId anywhere client-side
 * (Task 3's PairingScreen just displays it as text). Building that listing is out
 * of scope for this leaf task, so the user pastes the connection id shown on the
 * pairing screen after connecting.
 */
class ConnectionWidgetConfigurationActivity : ComponentActivity() {
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
            var connectionIdInput by remember { mutableStateOf("") }
            var confirmedConnectionId by remember { mutableStateOf<String?>(null) }
            WidgetlyTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(if (confirmedConnectionId == null) "연결 ID를 입력하세요" else "테마를 선택하세요")
                            },
                        )
                    },
                ) { padding ->
                    when (confirmedConnectionId) {
                        null -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("앱의 커플/친구 연결 화면에서 연결 후 표시된 연결 ID를 입력하세요.")
                                OutlinedTextField(
                                    value = connectionIdInput,
                                    onValueChange = { connectionIdInput = it },
                                    label = { Text("연결 ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        if (connectionIdInput.isNotBlank()) confirmedConnectionId = connectionIdInput
                                    },
                                ) {
                                    Text("다음")
                                }
                                Button(onClick = { finish() }) {
                                    Text("취소")
                                }
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                                items(ThemeCatalog.all, key = { it.id }) { theme ->
                                    Text(
                                        text = theme.name,
                                        color = Color(theme.foregroundColorArgb),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(theme.backgroundColorArgb))
                                            .clickable { confirmSelection(confirmedConnectionId!!, theme) }
                                            .padding(horizontal = 24.dp, vertical = 20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirmSelection(connectionId: String, theme: Theme) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        lifecycleScope.launch {
            saveSelectedConnection(this@ConnectionWidgetConfigurationActivity, glanceId, connectionId)
            saveSelectedTheme(this@ConnectionWidgetConfigurationActivity, glanceId, theme.id)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    companion object {
        suspend fun saveSelectedConnection(context: Context, glanceId: GlanceId, connectionId: String) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[CONNECTION_ID_KEY] = connectionId
            }
            ConnectionGlanceWidget().update(context, glanceId)
        }

        suspend fun saveSelectedTheme(context: Context, glanceId: GlanceId, themeId: String) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[THEME_ID_KEY] = themeId
            }
            ConnectionGlanceWidget().update(context, glanceId)
        }
    }
}
