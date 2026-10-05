package com.example.widgetly.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.dday.DdayEntry
import com.example.widgetly.dday.calculateDday
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val DDAY_ENTRY_ID_KEY = stringPreferencesKey("dday_entry_id")

class DdayGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[DDAY_ENTRY_ID_KEY]
        val entry = entryId?.let { selectedId ->
            WidgetlyApplication.ddayRepository.list().find { it.id == selectedId }
        }

        provideContent {
            DdayWidgetContent(entry)
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DdayWidgetContent(entry: DdayEntry?) {
    if (entry == null) {
        Text("삭제됨")
        return
    }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val result = calculateDday(entry, today)
    Text("${entry.title}: ${result.label}")
}
