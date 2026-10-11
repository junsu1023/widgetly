//
//  ConnectionWidget.swift
//  DdayWidgetExtension
//

import WidgetKit
import SwiftUI
import AppIntents

private let appGroupSuiteName = "group.com.example.widgetly"
private let rateLimitedAtKeyPrefix = "connection_rate_limited_at_"
private let sendFailedAtKeyPrefix = "connection_send_failed_at_"
private let authTokenKey = "firebase_id_token"
private let ownUidKey = "firebase_uid"
private let rateLimitWindowSeconds: TimeInterval = 5
private let connectionSnapshotKeyPrefix = "connection_snapshot_"
private let lastFetchedAtKeyPrefix = "connection_last_fetched_at_"
private let pushArrivedKeyPrefix = "connection_push_arrived_"

/// What this task can actually know about a connection, from either a push
/// payload (functions/src/lib/push.ts's `data`: `connectionId`/`presetType`/
/// `senderUid` for a signal, or just `connectionId` for a pairing success) or
/// a fallback server fetch (`getConnection`) — just enough to render what
/// ConnectionWidgetEntryView draws (streak, active/disconnected, the partner's
/// latest signal).
///
/// Deliberately NOT Shared's `Connection` type (Task 1), even though this
/// task's brief lists that as a consumed interface: `Connection` also
/// requires `memberUids`/`type`/`anniversaryDate`, which neither the push
/// payload nor this widget's render path needs — and `import Shared` would drag
/// the Firebase-dependent framework into an extension that deliberately links
/// only `SharedCore` (see CLAUDE.md's module-structure note). Documented here
/// per task-6-brief's explicit "use your judgment, document the chosen shape"
/// instruction.
///
/// The two `lastSignal*` fields are optional for two reasons: a brand-new
/// connection has no signals, and snapshots cached by an older build don't carry
/// the keys at all (Codable decodes a missing key into a `nil` optional, so old
/// cached JSON still loads).
struct ConnectionSnapshot: Codable {
    var status: String
    var streak: Int
    var lastSignalPreset: String?
    var lastSignalSenderUid: String?
}

/// Keyed per connectionId, same convention as the rate-limited/send-failed
/// flags below. Written by this extension's own fallback fetch and by
/// SignalNotificationService/NotificationService.swift (a separate Xcode
/// target/module once a human creates it — see that file's matching
/// `ConnectionSnapshot` struct, kept duplicated rather than shared because
/// there's no framework boundary between the two extensions to share it
/// through).
private func loadLocalConnectionSnapshot(connectionId: String) -> ConnectionSnapshot? {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName),
          let json = defaults.string(forKey: connectionSnapshotKeyPrefix + connectionId),
          let data = json.data(using: .utf8) else {
        return nil
    }
    return try? JSONDecoder().decode(ConnectionSnapshot.self, from: data)
}

private func saveLocalConnectionSnapshot(connectionId: String, _ snapshot: ConnectionSnapshot) {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName),
          let data = try? JSONEncoder().encode(snapshot),
          let json = String(data: data, encoding: .utf8) else {
        return
    }
    defaults.set(json, forKey: connectionSnapshotKeyPrefix + connectionId)
}

private func loadLastFetchedAt(connectionId: String) -> Date? {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName) else { return nil }
    let value = defaults.double(forKey: lastFetchedAtKeyPrefix + connectionId)
    return value > 0 ? Date(timeIntervalSince1970: value) : nil
}

private func loadPushArrivedFlag(connectionId: String) -> Bool {
    UserDefaults(suiteName: appGroupSuiteName)?.bool(forKey: pushArrivedKeyPrefix + connectionId) ?? false
}

/// Called whenever a fallback fetch attempt HAPPENS (per the brief: "폴백
/// 조회가 일어나면 lastFetchedAt을 갱신하고 pushArrivedSinceLastFetch를
/// 리셋한다") — on attempt, not only on confirmed success. The brief's policy
/// doesn't distinguish attempted-but-failed from succeeded; adding that
/// distinction (e.g. backing off retries only on real network failure) is a
/// real feature, not a few lines, and not built here.
private func markFetched(connectionId: String, at date: Date) {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName) else { return }
    defaults.set(date.timeIntervalSince1970, forKey: lastFetchedAtKeyPrefix + connectionId)
    defaults.set(false, forKey: pushArrivedKeyPrefix + connectionId)
}

/// The "공통 폴백" fetch (spec 4.3), over the `getConnection` callable rather
/// than the Firebase SDK — this extension carries no SDK, same reason
/// `sendSignal` goes over plain HTTPS from here.
///
/// Returns nil (and the caller keeps whatever is cached) when there's no auth
/// token yet, or the call fails, or the connection is gone — a failed refresh
/// must never blank out a widget that already has data.
private func fetchConnectionSnapshotFromServer(connectionId: String) async -> ConnectionSnapshot? {
    guard let authToken = loadCachedAuthToken(),
          let result = try? await CallableHttpClient().getConnection(
              connectionId: connectionId,
              authToken: authToken
          ) else {
        return nil
    }

    let partner = selectPartnerSignal(from: result.lastSignalByUid, ownUid: loadCachedOwnUid())
    return ConnectionSnapshot(
        status: result.status,
        streak: result.streak,
        lastSignalPreset: partner?.value.presetType,
        lastSignalSenderUid: partner?.key
    )
}

/// Picks the entry that is specifically the PARTNER's, not just the newest
/// overall — picking by recency alone would surface this device's own signal
/// whenever it sent last, hiding the partner's (the bug this function fixes).
/// Falls back to newest-overall only when the own uid isn't cached yet (main
/// app hasn't signed in far enough to write it), matching the same
/// "omit/guess-conservatively rather than assert" convention as
/// `ConnectionWidgetEntryView.partnerSignal(of:)`.
func selectPartnerSignal(
    from lastSignalByUid: [String: GetConnectionResult.Signal],
    ownUid: String?
) -> (key: String, value: GetConnectionResult.Signal)? {
    if let ownUid {
        return lastSignalByUid.first { $0.key != ownUid }
    }
    return lastSignalByUid.max { $0.value.sentAt < $1.value.sentAt }
}

/// The raw marker timestamps, exposed (not just the booleans below) so
/// `timeline(for:)` can schedule an entry at the exact moment a window expires.
private func loadMarkerSetAt(connectionId: String, keyPrefix: String) -> Date? {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName) else { return nil }
    let setAt = defaults.double(forKey: keyPrefix + connectionId)
    return setAt > 0 ? Date(timeIntervalSince1970: setAt) : nil
}

private func loadRateLimitedSetAt(connectionId: String) -> Date? {
    loadMarkerSetAt(connectionId: connectionId, keyPrefix: rateLimitedAtKeyPrefix)
}

private func loadSendFailedSetAt(connectionId: String) -> Date? {
    loadMarkerSetAt(connectionId: connectionId, keyPrefix: sendFailedAtKeyPrefix)
}

/// Evaluated against the ENTRY's date, not `Date()`: WidgetKit renders a
/// timeline entry at its own scheduled time, so "is the window still open" has
/// to be answered for that moment, otherwise a future entry would be built with
/// today's answer baked in. See `timeline(for:)`.
private func isRateLimited(connectionId: String, at date: Date) -> Bool {
    guard let setAt = loadRateLimitedSetAt(connectionId: connectionId) else { return false }
    return date.timeIntervalSince(setAt) < rateLimitWindowSeconds
}

private func markRateLimited(connectionId: String) {
    UserDefaults(suiteName: appGroupSuiteName)?.set(
        Date().timeIntervalSince1970,
        forKey: rateLimitedAtKeyPrefix + connectionId
    )
}

// Mirrors the rate-limited flag above (same App Group read/write/expiry
// pattern) so a failed send isn't silently swallowed — see the `catch` site
// in SendSignalIntent.perform() for what this covers and what it doesn't.
private func isSendFailed(connectionId: String, at date: Date) -> Bool {
    guard let setAt = loadSendFailedSetAt(connectionId: connectionId) else { return false }
    return date.timeIntervalSince(setAt) < rateLimitWindowSeconds
}

private func markSendFailed(connectionId: String) {
    UserDefaults(suiteName: appGroupSuiteName)?.set(
        Date().timeIntervalSince1970,
        forKey: sendFailedAtKeyPrefix + connectionId
    )
}

/// Both written by the main app on launch and on every return to the
/// foreground (iosApp/iosApp/iOSApp.swift) — this extension has no Firebase
/// Auth session of its own.
private func loadCachedAuthToken() -> String? {
    UserDefaults(suiteName: appGroupSuiteName)?.string(forKey: authTokenKey)
}

/// This device's own signed-in uid, needed to tell which half of a connection's
/// signals is the partner's. Nil before the main app has ever run far enough to
/// sign in, in which case the partner's signal line is omitted rather than
/// guessed at.
private func loadCachedOwnUid() -> String? {
    UserDefaults(suiteName: appGroupSuiteName)?.string(forKey: ownUidKey)
}

struct SendSignalIntent: AppIntent {
    static var title: LocalizedStringResource = "신호 보내기"

    @Parameter(title: "연결 ID")
    var connectionId: String

    // AppIntent requires a parameterless init(); AppIntents' synthesized
    // memberwise init also only takes the @Parameter property wrapper's
    // backing storage type (IntentParameter<String>), not the wrapped
    // String — so a plain init assigning through the wrappedValue setter is
    // needed to construct this from a widget's View code.
    init() {
        self.connectionId = ""
    }

    init(connectionId: String) {
        self.connectionId = connectionId
    }

    func perform() async throws -> some IntentResult {
        defer { WidgetCenter.shared.reloadTimelines(ofKind: "ConnectionWidget") }

        guard let authToken = loadCachedAuthToken() else {
            // No cached token means the main app has never completed sign-in on
            // this device, so there is nothing to send with. Surfaced as a send
            // failure rather than silently doing nothing.
            markSendFailed(connectionId: connectionId)
            return .result()
        }

        let client = CallableHttpClient()
        do {
            let result = try await client.sendSignal(
                connectionId: connectionId,
                preset: "heart",
                signalId: UUID().uuidString,
                authToken: authToken
            )
            if result.rateLimited {
                markRateLimited(connectionId: connectionId)
            }
        } catch {
            // ponytail: no automatic retry here — a WorkManager-style retry
            // queue for App Intents run from a widget is a real mechanism
            // (background task scheduling + persistence across process
            // death), not something to improvise in a few lines. This at
            // least stops swallowing the failure: the next timeline render
            // shows "전송 실패" instead of silently looking like nothing
            // happened, matching this file's error-states-are-visible
            // convention (rateLimited, 연결 끊김). Add real retry if dropped
            // signals turn out to matter in practice.
            markSendFailed(connectionId: connectionId)
        }

        return .result()
    }
}

struct ConnectionWidgetEntry: TimelineEntry {
    let date: Date
    let connectionId: String?
    let connection: ConnectionSnapshot?
    let rateLimited: Bool
    let sendFailed: Bool
    /// False until *something* has ever arrived for this connection — a push, or
    /// a fallback fetch attempt. Without it, "configured but still waiting for
    /// the first data" is indistinguishable from "the connection really is
    /// disconnected", and both rendered as 연결 끊김.
    let hasEverLoaded: Bool
    /// This device's own uid, so the view can tell the partner's signal from its
    /// own. Nil → the signal line is omitted.
    let ownUid: String?
}

struct ConnectionWidgetProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> ConnectionWidgetEntry {
        ConnectionWidgetEntry(
            date: Date(),
            connectionId: nil,
            connection: nil,
            rateLimited: false,
            sendFailed: false,
            hasEverLoaded: false,
            ownUid: nil
        )
    }

    func snapshot(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> ConnectionWidgetEntry {
        makeEntry(for: configuration, on: Date())
    }

    func timeline(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> Timeline<ConnectionWidgetEntry> {
        let now = Date()

        // 공통 폴백(docs/widget-development.md): 푸시/NSE 경로가 실패하거나 꺼져
        // 있어도 결국 반영되도록 주기적으로 재조회. Firestore 읽기 할당량 때문에
        // 매 갱신마다 조회하지 않고, TimelineRefreshPolicy가 "푸시 수신 직후"
        // 또는 "마지막 조회 후 30분 경과"일 때만 조회하도록 게이팅한다.
        if let connectionId = configuration.connectionId, !connectionId.isEmpty {
            let shouldFetch = TimelineRefreshPolicy().shouldFetchFromServer(
                now: now,
                lastFetchedAt: loadLastFetchedAt(connectionId: connectionId),
                pushArrivedSinceLastFetch: loadPushArrivedFlag(connectionId: connectionId)
            )
            if shouldFetch {
                // Persisted before markFetched and before makeEntry below, so a
                // successful fetch changes what this very render shows — not just
                // some later one.
                if let fetched = await fetchConnectionSnapshotFromServer(connectionId: connectionId) {
                    saveLocalConnectionSnapshot(connectionId: connectionId, fetched)
                }
                markFetched(connectionId: connectionId, at: now)
            }
        }

        var entries = [makeEntry(for: configuration, on: now)]

        // The 5s rate-limit/send-failed messages expire on wall-clock time, but a
        // single-entry timeline renders unchanged until the next scheduled refresh
        // (30 min) or an external reloadTimelines — so the message would sit there
        // long after its window closed. Emitting one extra entry dated at each
        // window's expiry makes WidgetKit itself flip the display at the right
        // moment, with no further trigger needed.
        if let connectionId = configuration.connectionId, !connectionId.isEmpty {
            let expiries = [
                loadRateLimitedSetAt(connectionId: connectionId),
                loadSendFailedSetAt(connectionId: connectionId),
            ]
                .compactMap { $0?.addingTimeInterval(rateLimitWindowSeconds) }
                .filter { $0 > now }
                .sorted()
            entries += expiries.map { makeEntry(for: configuration, on: $0) }
        }

        // 위 조회 여부와 무관하게, 결국 시스템 스케줄로 다시 호출되도록 30분 뒤
        // refresh도 계속 건다 — 다음 기회에 TimelineRefreshPolicy가 다시 판단한다.
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: now) ?? now
        return Timeline(entries: entries, policy: .after(nextRefresh))
    }

    private func makeEntry(for configuration: ConnectionWidgetConfigurationIntent, on date: Date) -> ConnectionWidgetEntry {
        guard let connectionId = configuration.connectionId, !connectionId.isEmpty else {
            return ConnectionWidgetEntry(
                date: date,
                connectionId: nil,
                connection: nil,
                rateLimited: false,
                sendFailed: false,
                hasEverLoaded: false,
                ownUid: nil
            )
        }
        let snapshot = loadLocalConnectionSnapshot(connectionId: connectionId)
        return ConnectionWidgetEntry(
            date: date,
            connectionId: connectionId,
            connection: snapshot,
            rateLimited: isRateLimited(connectionId: connectionId, at: date),
            sendFailed: isSendFailed(connectionId: connectionId, at: date),
            // Deliberately NOT OR'd with loadLastFetchedAt(connectionId:): that only
            // tracks whether a server fetch was ever ATTEMPTED (see markFetched's doc
            // comment), not whether anything was actually learned. timeline(for:)
            // marks an attempt and builds this same entry in one call, so OR'ing it in
            // here made this branch true before "불러오는 중" could ever render even
            // once in production - only a real snapshot (from a push or a successful
            // fetch) means there's something to show instead of "still don't know".
            hasEverLoaded: snapshot != nil,
            ownUid: loadCachedOwnUid()
        )
    }
}

struct ConnectionWidgetEntryView: View {
    var entry: ConnectionWidgetProvider.Entry

    var body: some View {
        Group {
            if let connectionId = entry.connectionId {
                if let connection = entry.connection, connection.status != "disconnected" {
                    VStack(spacing: 4) {
                        Text("스트릭 \(connection.streak)")
                            .font(.custom("DoHyeon-Regular", size: 18))
                        partnerSignal(of: connection)
                        if entry.rateLimited {
                            // Retrying immediately would just hit the backend's
                            // rate limit again, so no button while this holds.
                            Text("곧 다시 보내보세요")
                                .font(.custom("Dongle-Regular", size: 15))
                        } else {
                            // A failed send (network error, backend rejection,
                            // malformed response) is worth retrying right away —
                            // unlike rateLimited, keep the button live and just
                            // add a caption so the failure isn't silent.
                            if entry.sendFailed {
                                Text("전송 실패, 다시 시도해주세요")
                                    .font(.custom("Dongle-Regular", size: 15))
                            }
                            Button(intent: SendSignalIntent(connectionId: connectionId)) {
                                Text("신호 보내기")
                                    .font(.custom("DoHyeon-Regular", size: 14))
                            }
                        }
                    }
                } else if entry.hasEverLoaded {
                    Text("연결 끊김")
                        .font(.custom("Dongle-Regular", size: 16))
                } else {
                    // Configured, but no push has arrived and no fetch has
                    // happened yet — not the same thing as a dead connection.
                    Text("불러오는 중")
                        .font(.custom("Dongle-Regular", size: 16))
                }
            } else {
                Text("설정 필요")
                    .font(.custom("Dongle-Regular", size: 16))
            }
        }
    }

    /// 상대의 최신 신호. Only rendered when this device's own uid is known AND the
    /// cached signal came from someone else — the snapshot stores whichever signal
    /// is newest, which may well be this device's own.
    @ViewBuilder
    private func partnerSignal(of connection: ConnectionSnapshot) -> some View {
        if let ownUid = entry.ownUid,
           let senderUid = connection.lastSignalSenderUid,
           senderUid != ownUid,
           let preset = connection.lastSignalPreset {
            Text("상대 신호: \(preset)")
                .font(.custom("Dongle-Regular", size: 15))
        }
    }
}

struct ConnectionWidget: Widget {
    let kind: String = "ConnectionWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: ConnectionWidgetConfigurationIntent.self, provider: ConnectionWidgetProvider()) { entry in
            ConnectionWidgetEntryView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("연결 위젯")
        .description("커플·친구와 신호를 주고받는 위젯")
    }
}
