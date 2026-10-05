package com.example.widgetly.connection

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class ConnectionType { COUPLE, FRIEND }

data class SignalSummary(val presetType: String, val sentAt: Instant)

data class Connection(
    val id: String,
    val memberUids: List<String>,
    val type: ConnectionType,
    val status: String, // "active" | "disconnected"
    val anniversaryDate: LocalDate,
    val streak: Int,
    val lastSignalByUid: Map<String, SignalSummary>,
)

/**
 * Maps a raw Firestore document (as read off `ConnectionDoc` in the backend's
 * `functions/src/lib/types.ts`) into a [Connection]. Field names below must match
 * `ConnectionDoc` exactly. `anniversaryUpdatedBy` / `createdAt` / `streakUpdatedDate`
 * are part of that backend doc but unused on the client, so they're ignored here.
 */
fun connectionFromDocument(id: String, data: Map<String, Any?>): Connection {
    val type = when (data["type"] as String) {
        "couple" -> ConnectionType.COUPLE
        "friend" -> ConnectionType.FRIEND
        else -> error("Unknown connection type: ${data["type"]}")
    }
    @Suppress("UNCHECKED_CAST")
    val memberUids = (data["memberUids"] as List<Any?>).map { it as String }
    @Suppress("UNCHECKED_CAST")
    val lastSignalByUid = (data["lastSignalByUid"] as? Map<String, Any?>)
        ?.mapValues { (_, value) ->
            val signal = value as Map<String, Any?>
            SignalSummary(
                presetType = signal["presetType"] as String,
                sentAt = signal["sentAt"] as Instant,
            )
        }
        ?: emptyMap()

    return Connection(
        id = id,
        memberUids = memberUids,
        type = type,
        status = data["status"] as String,
        anniversaryDate = LocalDate.parse(data["anniversaryDate"] as String),
        streak = (data["streak"] as Number).toInt(),
        lastSignalByUid = lastSignalByUid,
    )
}
