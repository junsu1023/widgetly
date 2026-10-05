//
//  ConnectionWidget.swift
//  DdayWidgetExtension
//

import WidgetKit
import SwiftUI
import AppIntents
import Shared

private let appGroupSuiteName = "group.com.example.widgetly"
private let rateLimitedAtKeyPrefix = "connection_rate_limited_at_"
private let authTokenKey = "firebase_id_token"
private let rateLimitWindowSeconds: TimeInterval = 5

// ponytail: there is no local connection snapshot storage yet. Writing one
// into the App Group is a later task's job (the Notification Service
// Extension path described in docs/widget-development.md's "iOS 17~25" push
// handling) — this task only owns rendering + sending, not that plumbing.
// Until a snapshot writer exists, this always returns nil, which correctly
// renders as "연결 끊김" (the same honest state as a connection that's
// genuinely gone — not a lie about real-time-ness). Replace with a real App
// Group read (suiteName above) once the writer lands.
private func loadLocalConnectionSnapshot(connectionId: String) -> Connection? {
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
        if let result = try? await client.sendSignal(
            connectionId: connectionId,
            preset: "heart",
            signalId: UUID().uuidString,
            authToken: authToken
        ), result.rateLimited {
            markRateLimited(connectionId: connectionId)
        }

        return .result()
    }
}

struct ConnectionWidgetEntry: TimelineEntry {
    let date: Date
    let connectionId: String?
    let connection: Connection?
    let rateLimited: Bool
}

struct ConnectionWidgetProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> ConnectionWidgetEntry {
        ConnectionWidgetEntry(date: Date(), connectionId: nil, connection: nil, rateLimited: false)
    }

    func snapshot(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> ConnectionWidgetEntry {
        makeEntry(for: configuration, on: Date())
    }

    func timeline(for configuration: ConnectionWidgetConfigurationIntent, in context: Context) async -> Timeline<ConnectionWidgetEntry> {
        // 공통 폴백(docs/widget-development.md): 푸시/NSE 경로가 실패하거나 꺼져
        // 있어도 결국 반영되도록 주기적으로 재조회. Firestore 읽기 할당량 때문에
        // 너무 자주는 안 함 — 30분 간격.
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date()
        return Timeline(entries: [makeEntry(for: configuration, on: Date())], policy: .after(nextRefresh))
    }

    private func makeEntry(for configuration: ConnectionWidgetConfigurationIntent, on date: Date) -> ConnectionWidgetEntry {
        guard let connectionId = configuration.connectionId, !connectionId.isEmpty else {
            return ConnectionWidgetEntry(date: date, connectionId: nil, connection: nil, rateLimited: false)
        }
        return ConnectionWidgetEntry(
            date: date,
            connectionId: connectionId,
            connection: loadLocalConnectionSnapshot(connectionId: connectionId),
            rateLimited: isRateLimited(connectionId: connectionId)
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
                            Text("곧 다시 보내보세요")
                                .font(.caption)
                        } else {
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
