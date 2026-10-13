//
//  ConnectionWidgetTests.swift
//  DdayWidgetExtensionTests
//

import Testing
import Foundation

private func signal(_ preset: String, at sentAt: String) -> GetConnectionResult.Signal {
    GetConnectionResult.Signal(presetType: preset, sentAt: sentAt)
}

/// Regression coverage for a fix-wave-2 re-review finding: fetchConnectionSnapshotFromServer
/// used to pick the newest signal across BOTH members, so this device's own signal (when it
/// sent last) overwrote the partner's in the cached snapshot and the partner's signal line
/// disappeared from the widget. selectPartnerSignal must pick by uid, not recency, whenever
/// the device's own uid is known.
struct ConnectionWidgetTests {
    @Test func testPicksThePartnersSignalEvenWhenThisDeviceSentMostRecently() {
        let lastSignalByUid = [
            "me": signal("star", at: "2026-02-03T05:00:00.000Z"),   // newer
            "you": signal("heart", at: "2026-02-03T04:00:00.000Z"), // older, but the partner's
        ]

        let partner = selectPartnerSignal(from: lastSignalByUid, ownUid: "me")

        #expect(partner?.key == "you")
        #expect(partner?.value.presetType == "heart")
    }

    @Test func testPicksThePartnersSignalWhenItIsTheMostRecentToo() {
        let lastSignalByUid = [
            "me": signal("star", at: "2026-02-03T04:00:00.000Z"),
            "you": signal("heart", at: "2026-02-03T05:00:00.000Z"),
        ]

        let partner = selectPartnerSignal(from: lastSignalByUid, ownUid: "me")

        #expect(partner?.key == "you")
    }

    @Test func testFallsBackToNewestOverallWhenOwnUidIsUnknown() {
        let lastSignalByUid = [
            "a": signal("star", at: "2026-02-03T04:00:00.000Z"),
            "b": signal("heart", at: "2026-02-03T05:00:00.000Z"), // newer
        ]

        let partner = selectPartnerSignal(from: lastSignalByUid, ownUid: nil)

        #expect(partner?.key == "b")
    }

    @Test func testReturnsNilWhenThereAreNoSignalsYet() {
        #expect(selectPartnerSignal(from: [:], ownUid: "me") == nil)
        #expect(selectPartnerSignal(from: [:], ownUid: nil) == nil)
    }
}

/// Coverage for the mascot id -> asset name mapping and partner lookup added
/// alongside the mascot-avatar-selection feature's iOS mirror.
struct MascotMappingTests {
    @Test func testEachCatalogIdMapsToItsCapitalizedAssetName() {
        #expect(mascotAssetName(forCatalogId: "quokka") == "Quokka")
        #expect(mascotAssetName(forCatalogId: "cat") == "Cat")
        #expect(mascotAssetName(forCatalogId: "bear") == "Bear")
        #expect(mascotAssetName(forCatalogId: "rabbit") == "Rabbit")
        #expect(mascotAssetName(forCatalogId: "hamster") == "Hamster")
    }

    @Test func testUnrecognizedOrMissingIdFallsBackToQuokka() {
        #expect(mascotAssetName(forCatalogId: "does-not-exist") == "Quokka")
        #expect(mascotAssetName(forCatalogId: nil) == "Quokka")
    }

    @Test func testPartnerMascotIdPicksTheOtherUidsEntry() {
        let mascotByUid = ["me": "bear", "you": "cat"]
        #expect(selectPartnerMascotId(from: mascotByUid, ownUid: "me") == "cat")
    }

    @Test func testPartnerMascotIdFallsBackToQuokkaWhenPartnerHasNotPickedYet() {
        let mascotByUid = ["me": "bear"]
        #expect(selectPartnerMascotId(from: mascotByUid, ownUid: "me") == "quokka")
    }

    @Test func testPartnerMascotIdFallsBackToQuokkaWhenOwnUidOrMapIsUnknown() {
        #expect(selectPartnerMascotId(from: ["you": "cat"], ownUid: nil) == "quokka")
        #expect(selectPartnerMascotId(from: nil, ownUid: "me") == "quokka")
    }
}
