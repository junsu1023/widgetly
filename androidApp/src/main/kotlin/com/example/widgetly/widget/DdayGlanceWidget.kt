package com.example.widgetly.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.dday.DdayEntry
import com.example.widgetly.dday.calculateDday
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val DDAY_ENTRY_ID_KEY = stringPreferencesKey("dday_entry_id")

// Glance's stored Preferences uses structural equality: writing the same entryId again
// does not invalidate currentState() readers, so a repository change that doesn't touch
// DDAY_ENTRY_ID_KEY (edit/delete of the currently-selected entry) would otherwise never
// re-trigger the entry lookup below. Bumping this on every repository change forces it to.
val DDAY_REFRESH_TOKEN_KEY = longPreferencesKey("dday_refresh_token")

private object Loading

class DdayGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            DdayWidgetContent()
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DdayWidgetContent() {
    // currentState() reactively observes the session's Glance state. Resolving the
    // entry inside provideGlance() instead (one-shot, before provideContent) would
    // capture a stale value forever: update()/updateAll() recompose this content but
    // never re-invoke provideGlance() while a session is already running.
    val entryId = currentState(DDAY_ENTRY_ID_KEY)
    val refreshToken = currentState(DDAY_REFRESH_TOKEN_KEY)
    val entryState by produceState<Any?>(initialValue = Loading, key1 = entryId, key2 = refreshToken) {
        value = entryId?.let { selectedId ->
            WidgetlyApplication.ddayRepository.list().find { it.id == selectedId }
        }
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White))
            .cornerRadius(16.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            entryId == null -> Text("설정 필요", style = TextStyle(fontSize = 14.sp))
            entryState === Loading -> {}
            entryState == null -> Text("삭제됨", style = TextStyle(fontSize = 14.sp))
            else -> {
                val entry = entryState as DdayEntry
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val result = calculateDday(entry, today)
                Text(
                    "${entry.title}\n${result.label}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
