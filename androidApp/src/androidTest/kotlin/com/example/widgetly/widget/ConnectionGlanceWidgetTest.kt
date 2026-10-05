package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.example.widgetly.connection.Connection
import com.example.widgetly.connection.ConnectionType
import kotlinx.datetime.LocalDate
import org.junit.Test

private val sampleConnection = Connection(
    id = "conn-1",
    memberUids = listOf("uidA", "uidB"),
    type = ConnectionType.COUPLE,
    status = "active",
    anniversaryDate = LocalDate(2026, 1, 1),
    streak = 5,
    lastSignalByUid = emptyMap(),
)

/**
 * Renders ConnectionWidgetContent directly with already-resolved values (instead
 * of going through ConnectionGlanceWidget.provideGlance()'s real
 * WidgetlyApplication.connectionRepository/Firestore lookup) - a true isolated
 * composable test, not an integration test against a live backend.
 */
class ConnectionGlanceWidgetTest {
    @Test
    fun showsStreakWhenConnectionIsActive() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("연속 5일")).assertExists()
    }

    @Test
    fun showsDisconnectedWhenConnectionStatusIsDisconnected() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection.copy(status = "disconnected"),
                rateLimitedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("연결 끊김")).assertExists()
    }

    @Test
    fun showsDisconnectedWhenConnectionCannotBeFound() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = null,
                rateLimitedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("연결 끊김")).assertExists()
    }

    @Test
    fun showsSetupNeededWhenNoConnectionIsConfigured() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = null,
                connectionState = null,
                rateLimitedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("설정 필요")).assertExists()
    }

    @Test
    fun showsRateLimitedMessageWhenSignalWasRateLimited() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = System.currentTimeMillis(),
                themeId = null,
            )
        }
        onNode(hasText("잠시 후 다시 보내주세요")).assertExists()
    }
}
