package com.example.widgetly.connection

import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
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
    val mascotByUid: Map<String, String>,
)

/**
 * Wire shape of `connections/{id}` as the Firestore SDK hands it back.
 *
 * This has to be a concrete `@Serializable` class, not `Map<String, Any?>`: GitLive's
 * `DocumentSnapshot.data<T>()` / `HttpsCallableResult.data<T>()` decode through
 * kotlinx.serialization, and there is no serializer for `Any` - `data<Map<String, Any?>>()`
 * compiles but throws `SerializationException` on every real snapshot. (The raw
 * accessor `encodedData()` is `internal` to the library, so it is not an option.)
 *
 * Field names must match the backend's `ConnectionDoc` in `functions/src/lib/types.ts`
 * exactly. That doc also carries `createdAt` / `anniversaryUpdatedBy` /
 * `streakUpdatedDate`, which the client never reads; they are deliberately absent here -
 * GitLive's class decoder walks this descriptor's fields and ignores any extra keys in
 * the document, so unused backend fields cost nothing.
 *
 * `lastSignalByUid` is optional because a freshly created connection has no signals yet.
 * `mascotByUid` is optional because connections created before this field existed have none.
 */
@Serializable
data class ConnectionDoc(
    val memberUids: List<String>,
    val type: String,
    val status: String,
    val anniversaryDate: String, // "YYYY-MM-DD"
    val streak: Int,
    val lastSignalByUid: Map<String, SignalDoc> = emptyMap(),
    val mascotByUid: Map<String, String> = emptyMap(),
)

/** Entry of [ConnectionDoc.lastSignalByUid]. [Timestamp] is GitLive's serializable Firestore timestamp. */
@Serializable
data class SignalDoc(val presetType: String, val sentAt: Timestamp)

/** Maps the decoded Firestore document into the domain [Connection]. */
fun connectionFromDocument(id: String, doc: ConnectionDoc): Connection = Connection(
    id = id,
    memberUids = doc.memberUids,
    type = when (doc.type) {
        "couple" -> ConnectionType.COUPLE
        "friend" -> ConnectionType.FRIEND
        else -> error("Unknown connection type: ${doc.type}")
    },
    status = doc.status,
    anniversaryDate = LocalDate.parse(doc.anniversaryDate),
    streak = doc.streak,
    lastSignalByUid = doc.lastSignalByUid.mapValues { (_, signal) ->
        SignalSummary(
            presetType = signal.presetType,
            sentAt = Instant.fromEpochSeconds(signal.sentAt.seconds, signal.sentAt.nanoseconds),
        )
    },
    mascotByUid = doc.mascotByUid,
)
