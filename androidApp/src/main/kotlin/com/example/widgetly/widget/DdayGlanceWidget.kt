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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.widgetly.WidgetlyApplication
import com.example.widgetly.dday.DdayEntry
import com.example.widgetly.dday.calculateDday
import com.example.widgetly.theme.MascotCatalog
import com.example.widgetly.theme.ThemeCatalog
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val DDAY_ENTRY_ID_KEY = stringPreferencesKey("dday_entry_id")

// Generic name/type (not D-day specific) so the couple/friend shared widget plan can
// reuse the same state key.
val THEME_ID_KEY = stringPreferencesKey("theme_id")

// Glance's stored Preferences uses structural equality: writing the same entryId again
// does not invalidate currentState() readers, so a repository change that doesn't touch
// DDAY_ENTRY_ID_KEY (edit/delete of the currently-selected entry) would otherwise never
// re-trigger the entry lookup below. Bumping this on every repository change forces it to.
val DDAY_REFRESH_TOKEN_KEY = longPreferencesKey("dday_refresh_token")

private object Loading

class DdayGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(MASCOT_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
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
            val themeId = currentState(THEME_ID_KEY)
            // One-shot read, same as entryState above: mascotPreferenceStorage.read()
            // is a suspend DataStore call, and there's no reactive currentState() key
            // for it the way DDAY_ENTRY_ID_KEY works for the entry.
            val mascotId by produceState(initialValue = MascotCatalog.DEFAULT_MASCOT_ID) {
                value = WidgetlyApplication.mascotPreferenceStorage.read()
            }
            DdayWidgetContent(entryId, entryState, themeId, mascotId)
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun DdayWidgetContent(entryId: String?, entryState: Any?, themeId: String?, mascotId: String) {
    val theme = ThemeCatalog.find(themeId ?: ThemeCatalog.DEFAULT_THEME_ID)
    val backgroundColor = androidx.compose.ui.graphics.Color(theme.backgroundColorArgb)
    val foregroundColor = androidx.compose.ui.graphics.Color(theme.foregroundColorArgb)

    // No custom fontFamily here (color/corner-radius theming still applies): Glance's
    // TextStyle.fontFamily is forwarded as a plain String into a platform
    // android.text.style.TypefaceSpan, which resolves only against the system font
    // family map (confirmed by decompiling glance-appwidget 1.1.1's TextTranslator) -
    // and that Span is parcelled into RemoteViews and rendered in the launcher's
    // process, which has no access to this APK's res/font at all. A bundled TTF can
    // never load this way, so Do Hyeon/Dongle apply to the app shell and the iOS
    // widget only (see docs/cross-platform-verification.md row 16).
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = backgroundColor, night = backgroundColor))
            .cornerRadius(20.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            entryId == null -> EmptyStateWithMascot(
                mascotId,
                "설정 필요",
                TextStyle(fontSize = 14.sp, color = ColorProvider(day = foregroundColor, night = foregroundColor)),
            )
            entryState === Loading -> {}
            entryState == null -> EmptyStateWithMascot(
                mascotId,
                "삭제됨",
                TextStyle(fontSize = 14.sp, color = ColorProvider(day = foregroundColor, night = foregroundColor)),
            )
            else -> {
                val entry = entryState as DdayEntry
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val result = calculateDday(entry, today)
                Text(
                    "${entry.title}\n${result.label}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorProvider(day = foregroundColor, night = foregroundColor)),
                )
                MascotCornerDecoration(mascotId)
            }
        }
    }
}

/**
 * The quokka mascot above a message, for a widget's empty states only - never
 * shown once real data (D-day label, streak) is on screen, so it doesn't
 * compete with the thing the widget actually exists to show. Shared by
 * DdayGlanceWidget and ConnectionGlanceWidget (same package).
 */
@Composable
internal fun EmptyStateWithMascot(mascotId: String, message: String, style: TextStyle) {
    // 28.dp, not 48.dp: the widget's declared minHeight (40dp) plus the
    // surrounding 12dp padding on each side leaves little room before the
    // mascot + text overflow at the smallest size a user can resize the
    // widget down to.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            provider = ImageProvider(mascotDrawableRes(mascotId)),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
        )
        Text(message, style = style)
    }
}
