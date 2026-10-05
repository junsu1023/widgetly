//
//  TimelineRefreshPolicyTests.swift
//  DdayWidgetExtensionTests
//

import Testing
import Foundation

struct TimelineRefreshPolicyTests {
    @Test func testFetchesImmediatelyWhenPushArrived() {
        let policy = TimelineRefreshPolicy()
        #expect(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: Date(), pushArrivedSinceLastFetch: true))
    }

    @Test func testDoesNotFetchBeforeThirtyMinutesWithoutAPush() {
        let policy = TimelineRefreshPolicy()
        let tenMinutesAgo = Date().addingTimeInterval(-10 * 60)
        #expect(!policy.shouldFetchFromServer(now: Date(), lastFetchedAt: tenMinutesAgo, pushArrivedSinceLastFetch: false))
    }

    @Test func testFetchesAfterThirtyMinutesWithoutAPush() {
        let policy = TimelineRefreshPolicy()
        let fortyMinutesAgo = Date().addingTimeInterval(-40 * 60)
        #expect(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: fortyMinutesAgo, pushArrivedSinceLastFetch: false))
    }

    @Test func testFetchesWhenThereHasBeenNoFetchYet() {
        let policy = TimelineRefreshPolicy()
        #expect(policy.shouldFetchFromServer(now: Date(), lastFetchedAt: nil, pushArrivedSinceLastFetch: false))
    }
}
