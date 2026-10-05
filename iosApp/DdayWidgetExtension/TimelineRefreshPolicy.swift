//
//  TimelineRefreshPolicy.swift
//  DdayWidgetExtension
//

import Foundation

/// Gates the "공통 폴백" server fetch described in docs/widget-development.md:
/// the widget's TimelineProvider re-queries the server only "직후 푸시 수신" or
/// "마지막 조회 후 30분 경과" — never on every timeline refresh — to keep
/// Firestore read quota under control (see docs/firebase-pairing.md's quota
/// math). Pure logic, no App Group/Shared/Firebase dependency, so this is
/// fully unit-testable without any of this plan's known build blockers.
struct TimelineRefreshPolicy {
    private let refreshInterval: TimeInterval = 30 * 60

    func shouldFetchFromServer(now: Date, lastFetchedAt: Date?, pushArrivedSinceLastFetch: Bool) -> Bool {
        if pushArrivedSinceLastFetch {
            return true
        }
        guard let lastFetchedAt else {
            return true
        }
        return now.timeIntervalSince(lastFetchedAt) >= refreshInterval
    }
}
