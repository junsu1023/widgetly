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
private let rateLimitWindowSeconds: TimeInterval = 5
private let connectionSnapshotKeyPrefix = "connection_snapshot_"
private let lastFetchedAtKeyPrefix = "connection_last_fetched_at_"
private let pushArrivedKeyPrefix = "connection_push_arrived_"

/// What this task can actually know about a connection, from either a push
/// payload (functions/src/lib/push.ts's `data`: `connectionId`/`presetType`/
/// `senderUid` for a signal, or just `connectionId` for a pairing success) or
/// a future fallback server fetch — just enough to render the two things
/// ConnectionWidgetEntryView draws (streak, active/disconnected).
///
/// Deliberately NOT Shared's `Connection` type (Task 1), even though this
/// task's brief lists that as a consumed interface: `Connection` also
/// requires `memberUids`/`type`/`anniversaryDate`/`lastSignalByUid`, none of
/// which the push payload carries and none of which any fetch path built so
/// far can supply either (see `fetchConnectionSnapshotFromServer` below) —
/// and the view never reads them. Fabricating those fields to satisfy the
/// type would be less honest than a smaller type that only claims to know
/// what's actually knowable. Documented here per task-6-brief's explicit
/// "use your judgment, document the chosen shape" instruction. This also
/// lets this file drop its `import Shared` entirely.
struct ConnectionSnapshot: Codable {
    var status: String
    var streak: Int
}

/// Keyed per connectionId, same convention as the rate-limited/send-failed
/// flags below. Written by this extension's own fallback-fetch seam and by
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

// ponytail: no real network/Firestore client here. A genuine fetch needs
// either (a) Firebase bootstrapped inside this extension — its own Xcode/
// plist manual step, same class of work as this plan's Task 2's pending SPM
// step — or (b) a new read-oriented Cloud Function callable over plain
// HTTPS, the same way SendSignalHttpClient calls sendSignal. Neither exists
// today and both are out of this task's scope (brief lists only 3 files).
// This seam exists so a later task only has to fill in the body; the
// gating/bookkeeping around it — this task's actual deliverable — is fully
// wired and correct regardless of what this returns. Ceiling: until this is
// implemented, the "공통 폴백" fetch never actually refreshes stale data by
// itself — only a push (via NotificationService's own merge-write) can.
private func fetchConnectionSnapshotFromServer(connectionId: String) async -> ConnectionSnapshot? {
    nil
}

private func isRateLimited(connectionId: String) -> Bool {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName) else { return false }
    let setAt = defaults.double(forKey: rateLimitedAtKeyPrefix + connectionId)
    guard setAt > 0 else { return false }
    return Date().timeIntervalSince1970 - setAt < rateLimitWindowSeconds
}

private func markRateLimited(connectionId: String) {
    UserDefaults(suiteName: appGroupSuiteName)?.set(
        Date().timeIntervalSince1970,
        forKey: rateLimitedAtKeyPrefix + connectionId
    )
}

// Mirrors the rate-limited flag above (same App Group read/write/expiry
// pattern) so a failed send isn't silently swallowed — see the `try?` site
// in SendSignalIntent.perform() for what this covers and what it doesn't.
private func isSendFailed(connectionId: String) -> Bool {
    guard let defaults = UserDefaults(suiteName: appGroupSuiteName) else { return false }
    let setAt = defaults.double(forKey: sendFailedAtKeyPrefix + connectionId)
    guard setAt > 0 else { return false }
    return Date().timeIntervalSince1970 - setAt < rateLimitWindowSeconds
}

private func markSendFailed(connectionId: String) {
    UserDefaults(suiteName: appGroupSuiteName)?.set(
        Date().timeIntervalSince1970,
        forKey: sendFailedAtKeyPrefix + connectionId
    )
}

// ponytail: an ID token for the Authorization header has to come from
// somewhere the main app writes it into the App Group — this extension has
// no Firebase SDK/Auth session of its own, and wiring that cache is Task 2's
// job, not this one (brief: "you don't need to implement how the token is
// obtained"). Until that plumbing exists this reads a placeholder key and
// sends whatever is there (possibly nothing, in which case the button is a
// no-op). `SendSignalHttpClient`'s own contract — accept authToken, set the
// header — is what this task owns, and it's fully tested independent of
// where the token actually comes from.
private func loadCachedAuthToken() -> String? {
    UserDefaults(suiteName: appGroupSuiteName)?.string(forKey: authTokenKey)
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
            return .result()
        }

        let client = SendSignalHttpClient()
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
}

struct ConnectionWidgetProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> ConnectionWidgetEntry {
        ConnectionWidgetEntry(date: Date(), connectionId: nil, connection: nil, rateLimited: false, sendFailed: false)
    }

    func snapshot(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> ConnectionWidgetEntry {
        makeEntry(for: configuration, on: Date())
    }

    func timeline(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> Timeline<ConnectionWidgetEntry> {
        // 공통 폴백(docs/widget-development.md): 푸시/NSE 경로가 실패하거나 꺼져
        // 있어도 결국 반영되도록 주기적으로 재조회. Firestore 읽기 할당량 때문에
        // 매 갱신마다 조회하지 않고, TimelineRefreshPolicy가 "푸시 수신 직후"
        // 또는 "마지막 조회 후 30분 경과"일 때만 조회하도록 게이팅한다.
        if let connectionId = configuration.connectionId, !connectionId.isEmpty {
            let now = Date()
            let shouldFetch = TimelineRefreshPolicy().shouldFetchFromServer(
                now: now,
                lastFetchedAt: loadLastFetchedAt(connectionId: connectionId),
                pushArrivedSinceLastFetch: loadPushArrivedFlag(connectionId: connectionId)
            )
            if shouldFetch {
                // TODO: once fetchConnectionSnapshotFromServer does a real
                // fetch, a non-nil result here must be persisted (e.g. via
                // the same App Group write loadLocalConnectionSnapshot
                // reads) — otherwise a successful fetch still wouldn't
                // change what the widget renders.
                _ = await fetchConnectionSnapshotFromServer(connectionId: connectionId)
                markFetched(connectionId: connectionId, at: now)
            }
        }
        // 위 조회 여부와 무관하게, 결국 시스템 스케줄로 다시 호출되도록 30분 뒤
        // refresh도 계속 건다 — 다음 기회에 TimelineRefreshPolicy가 다시 판단한다.
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date()
        return Timeline(entries: [makeEntry(for: configuration, on: Date())], policy: .after(nextRefresh))
    }

    private func makeEntry(for configuration: ConnectionWidgetConfigurationIntent, on date: Date) -> ConnectionWidgetEntry {
        guard let connectionId = configuration.connectionId, !connectionId.isEmpty else {
            return ConnectionWidgetEntry(date: date, connectionId: nil, connection: nil, rateLimited: false, sendFailed: false)
        }
        return ConnectionWidgetEntry(
            date: date,
            connectionId: connectionId,
            connection: loadLocalConnectionSnapshot(connectionId: connectionId),
            rateLimited: isRateLimited(connectionId: connectionId),
            sendFailed: isSendFailed(connectionId: connectionId)
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
                        if entry.rateLimited {
                            // Retrying immediately would just hit the backend's
                            // rate limit again, so no button while this holds.
                            Text("곧 다시 보내보세요")
                                .font(.caption)
                        } else {
                            // A failed send (network error, backend rejection,
                            // malformed response) is worth retrying right away —
                            // unlike rateLimited, keep the button live and just
                            // add a caption so the failure isn't silent.
                            if entry.sendFailed {
                                Text("전송 실패, 다시 시도해주세요")
                                    .font(.caption)
                            }
                            Button(intent: SendSignalIntent(connectionId: connectionId)) {
                                Text("신호 보내기")
                            }
                        }
                    }
                } else {
                    Text("연결 끊김")
                }
            } else {
                Text("설정 필요")
            }
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
