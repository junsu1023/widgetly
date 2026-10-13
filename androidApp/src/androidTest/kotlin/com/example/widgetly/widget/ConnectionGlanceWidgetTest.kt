package com.example.widgetly.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import com.example.widgetly.connection.Connection
import com.example.widgetly.connection.ConnectionType
import com.example.widgetly.connection.SignalSummary
import kotlin.time.Instant
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
    mascotByUid = emptyMap(),
)

/**
 * Renders ConnectionWidgetContent directly with already-resolved values (instead
 * of going through ConnectionGlanceWidget.provideGlance()'s real
 * WidgetlyApplication.connectionRepository/Firestore lookup) - a true isolated
 * composable test, not an integration test against a live backend.
 */
class ConnectionGlanceWidgetTest {
    @Test
    fun showsStreakAndCornerMascotWhenConnectionIsActive() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = null,
                sendFailedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("연속 5일")).assertExists()
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(1)
    }

    @Test
    fun showsDisconnectedWithoutCornerMascotWhenConnectionStatusIsDisconnected() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(MASCOT_SIZE_SMALL)
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection.copy(status = "disconnected"),
                rateLimitedAt = null,
                sendFailedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("연결 끊김")).assertExists()
        onAllNodes(hasContentDescriptionEqualTo("quokka_corner")).assertCountEquals(0)
    }

    @Test
    fun showsDisconnectedWhenConnectionCannotBeFound() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = null,
                rateLimitedAt = null,
                sendFailedAt = null,
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
                sendFailedAt = null,
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
                sendFailedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("잠시 후 다시 보내주세요")).assertExists()
    }

    @Test
    fun showsThePartnersLatestSignal() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection.copy(
                    lastSignalByUid = mapOf(
                        "uidA" to SignalSummary("star", Instant.fromEpochSeconds(1)),
                        "uidB" to SignalSummary("heart", Instant.fromEpochSeconds(2)),
                    ),
                ),
                rateLimitedAt = null,
                sendFailedAt = null,
                themeId = null,
                myUid = "uidA",
            )
        }
        onNode(hasText("상대 신호: heart")).assertExists()
    }

    @Test
    fun omitsThePartnersSignalWhenThisDeviceHasNoUidYet() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection.copy(
                    lastSignalByUid = mapOf("uidB" to SignalSummary("heart", Instant.fromEpochSeconds(2))),
                ),
                rateLimitedAt = null,
                sendFailedAt = null,
                themeId = null,
                myUid = null,
            )
        }
        onNode(hasText("상대 신호: heart")).assertDoesNotExist()
    }

    @Test
    fun hidesTheSignalButtonWhileCoolingDown() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = System.currentTimeMillis(),
                sendFailedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("신호 보내기")).assertDoesNotExist()
    }

    @Test
    fun clearsTheCooldownOnceTheWindowHasElapsed() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = System.currentTimeMillis() - SIGNAL_FEEDBACK_WINDOW_MS - 1,
                sendFailedAt = null,
                themeId = null,
            )
        }
        onNode(hasText("잠시 후 다시 보내주세요")).assertDoesNotExist()
        onNode(hasText("신호 보내기")).assertExists()
    }

    @Test
    fun showsSendFailedMessageAndKeepsTheButton() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = null,
                sendFailedAt = System.currentTimeMillis(),
                themeId = null,
            )
        }
        onNode(hasText("전송 실패, 다시 시도해주세요")).assertExists()
        onNode(hasText("신호 보내기")).assertExists()
    }

    @Test
    fun clearsTheSendFailedMessageOnceTheWindowHasElapsed() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ConnectionWidgetContent(
                connectionId = sampleConnection.id,
                connectionState = sampleConnection,
                rateLimitedAt = null,
                sendFailedAt = System.currentTimeMillis() - SIGNAL_FEEDBACK_WINDOW_MS - 1,
                themeId = null,
            )
        }
        onNode(hasText("전송 실패, 다시 시도해주세요")).assertDoesNotExist()
    }
}
