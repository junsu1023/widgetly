//
//  ConnectionWidgetConfigurationIntent.swift
//  DdayWidgetExtension
//

import AppIntents

/// Configuration for `ConnectionWidget`. There is no "list my connections"
/// data source anywhere in this project yet — Task 4's Android widget hit
/// the exact same gap (`ConnectionWidgetConfigurationActivity` has the user
/// paste the connection id shown on the pairing screen into a text field,
/// since `ConnectionRepository` only supports looking up an already-known
/// id). Matching that scope decision here instead of building a bigger
/// AppEntity/EntityQuery picker than Android has: `connectionId` is a plain
/// text parameter, which AppIntents renders as a text field by default.
/// Swap for a real picker once a connections-list source exists.
struct ConnectionWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource { "연결 위젯 설정" }
    static var description: IntentDescription { "페어링 화면에 표시된 연결 ID를 입력하세요" }

    @Parameter(title: "연결 ID")
    var connectionId: String?
}
