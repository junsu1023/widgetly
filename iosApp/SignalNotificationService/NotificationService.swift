//
//  NotificationService.swift
//  SignalNotificationService
//
//  NOTE: This file has no Xcode target to compile into yet. Creating the
//  "SignalNotificationService" Notification Service Extension target (File >
//  New > Target) and registering the App Group capability across this
//  target, iosApp, and DdayWidgetExtensionExtension is a manual Xcode GUI
//  step (task-6-brief.md) — out of scope for headless editing here, same
//  reasoning as this plan's Task 2 SPM step. This file is written as a
//  complete, correct source file as if that target already existed; a human
//  adds both the target and this file's target membership together later.
//

import UserNotifications
import WidgetKit

private let appGroupSuiteName = "group.com.example.widgetly"
private let connectionSnapshotKeyPrefix = "connection_snapshot_"
private let pushArrivedKeyPrefix = "connection_push_arrived_"

/// Mirrors DdayWidgetExtension/ConnectionWidget.swift's `ConnectionSnapshot`
/// exactly (field names, JSON shape, App Group key prefix) — kept duplicated
/// rather than shared through a common framework because this extension and
/// DdayWidgetExtension are separate Xcode targets/modules with no shared
/// framework between them, and introducing one for a single small Codable
/// struct would be over-engineering for what it buys. If you change one
/// side, change the other.
private struct ConnectionSnapshot: Codable {
    var status: String
    var streak: Int
    var lastSignalPreset: String?
    var lastSignalSenderUid: String?
    var mascotByUid: [String: String]?
}

/// iOS 17~25 "즉시 반영" path (docs/widget-development.md): when a push about
/// a connection arrives, write what's knowable into the App Group and ask
/// WidgetKit to re-run ConnectionWidget's TimelineProvider right away, rather
/// than waiting for the 30-minute 공통 폴백 in TimelineRefreshPolicy.
///
/// Real-device verification required, not done here (task-6-brief.md step
/// 6): NSEs run under a strict (~24MB) memory limit and the OS can kill this
/// process under memory pressure before `didReceive` finishes — this is not
/// a guaranteed-to-run hook. If killed, iOS falls back to delivering the
/// unmodified original notification content, and the widget simply waits for
/// the 공통 폴백 path instead of refreshing immediately — a degraded, not
/// broken, outcome. Whether this actually fires reliably on a real device,
/// with notifications on vs. off, needs manual verification this
/// environment cannot perform.
class NotificationService: UNNotificationServiceExtension {
    var contentHandler: ((UNNotificationContent) -> Void)?
    var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        bestAttemptContent = request.content.mutableCopy() as? UNMutableNotificationContent

        // Always hand back a notification to display, no matter which path
        // below is taken (or fails) — an NSE that never calls this is a
        // silently-dropped notification, not just a silently-skipped widget
        // refresh.
        defer {
            if let bestAttemptContent {
                contentHandler(bestAttemptContent)
            }
        }

        // functions/src/lib/push.ts's `data` payload: `{connectionId,
        // presetType, senderUid}` for a signal (sendSignal.ts), or just
        // `{connectionId}` for a pairing success (redeemPairingCode.ts).
        // Both arrive as top-level APNs payload keys via FCM's notification+
        // data message conversion (same assumption Android's
        // WidgetlyFirebaseMessagingService.onMessageReceived makes for
        // `message.data` — unverified against a real APNs payload here,
        // same class of "needs real-device check" as the memory-limit note
        // above).
        guard let connectionId = request.content.userInfo["connectionId"] as? String,
              let defaults = UserDefaults(suiteName: appGroupSuiteName) else {
            return
        }

        // Either push shape implies the connection is active: sendSignal.ts
        // refuses to push for anything else (`connection.status !== "active"`
        // throws before any push is sent), and a pairing-success push is by
        // definition a brand-new active connection. Merge that onto whatever
        // is already cached instead of overwriting wholesale — the payload
        // never carries streak (see ConnectionSnapshot's doc comment and
        // push.ts's `data` argument), so there's nothing truer to put in that
        // field than what's already there.
        let snapshotKey = connectionSnapshotKeyPrefix + connectionId
        var snapshot: ConnectionSnapshot
        if let existingJson = defaults.string(forKey: snapshotKey),
           let existingData = existingJson.data(using: .utf8),
           let existing = try? JSONDecoder().decode(ConnectionSnapshot.self, from: existingData) {
            snapshot = existing
            snapshot.status = "active"
        } else {
            // No prior snapshot to merge a real streak from — write only
            // what's actually knowable (status) rather than fabricate a
            // number. This is a known, short-lived placeholder: the next
            // ConnectionWidgetProvider.timeline(for:in:) call (triggered by
            // reloadTimelines below) sees pushArrivedSinceLastFetch and does
            // its own fallback fetch.
            snapshot = ConnectionSnapshot(status: "active", streak: 0)
        }

        // A signal push carries presetType/senderUid right alongside
        // connectionId; a pairing-success push carries neither. They were being
        // ignored, which left the widget with no way to show the partner's latest
        // signal until the 30-minute fallback fetch came round. Only overwrite
        // when actually present, so a pairing-success push doesn't wipe the last
        // known signal.
        if let presetType = request.content.userInfo["presetType"] as? String,
           let senderUid = request.content.userInfo["senderUid"] as? String {
            snapshot.lastSignalPreset = presetType
            snapshot.lastSignalSenderUid = senderUid
        }

        if let snapshotData = try? JSONEncoder().encode(snapshot),
           let snapshotJson = String(data: snapshotData, encoding: .utf8) {
            defaults.set(snapshotJson, forKey: snapshotKey)
        }
        defaults.set(true, forKey: pushArrivedKeyPrefix + connectionId)

        WidgetCenter.shared.reloadTimelines(ofKind: "ConnectionWidget")
    }

    /// Called when the system's ~30s budget for this extension is about to
    /// run out before `didReceive` finished — still must deliver *something*.
    override func serviceExtensionTimeWillExpire() {
        if let contentHandler, let bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }
}
