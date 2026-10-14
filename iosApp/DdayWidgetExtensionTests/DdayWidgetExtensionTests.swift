//
//  DdayWidgetExtensionTests.swift
//  DdayWidgetExtensionTests
//

import Testing
import SharedCore
import Foundation

extension Date {
    func toKotlinLocalDate() -> Kotlinx_datetimeLocalDate {
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: self)
        return Kotlinx_datetimeLocalDate(
            year: Int32(components.year!),
            month: Int32(components.month!),
            day: Int32(components.day!)
        )
    }
}

struct DdayWidgetExtensionTests {

    @Test func missingEntryMapsToDeletedLabel() {
        let today = Kotlinx_datetimeLocalDate(year: 2026, month: 10, day: 5)
        let label = DdayCalculatorKt.ddayDisplayLabel(entry: nil, today: today)
        #expect(label == "삭제됨")
    }

    @Test func knownEntryMapsToItsCalculatedLabel() {
        let today = Kotlinx_datetimeLocalDate(year: 2026, month: 10, day: 5)
        let entry = DdayEntry(id: "1", title: "t", targetDate: today, countMode: .startAtZero, repeat: .none, category: .other, icon: .heart)
        let label = DdayCalculatorKt.ddayDisplayLabel(entry: entry, today: today)
        #expect(label == "D-DAY")
    }

    @Test func unknownThemeIdFallsBackToTheDefaultTheme() {
        let result = ThemeCatalog.shared.find(id: "does-not-exist")
        #expect(result.id == ThemeCatalog.shared.DEFAULT_THEME_ID)
    }

    @Test func knownThemeIdReturnsTheMatchingThemeWithItsColors() {
        let known = ThemeCatalog.shared.all.first { $0.id != ThemeCatalog.shared.DEFAULT_THEME_ID }!
        let result = ThemeCatalog.shared.find(id: known.id)
        #expect(result.id == known.id)
        #expect(result.backgroundColorArgb == known.backgroundColorArgb)
        #expect(result.foregroundColorArgb == known.foregroundColorArgb)
    }

    @Test func dateConvertsToTheMatchingKotlinLocalDate() {
        var components = DateComponents()
        components.year = 2026
        components.month = 10
        components.day = 5
        components.hour = 23
        components.minute = 30
        let calendar = Calendar(identifier: .gregorian)
        let date = calendar.date(from: components)!

        let localDate = date.toKotlinLocalDate()

        #expect(localDate == Kotlinx_datetimeLocalDate(year: 2026, month: 10, day: 5))
    }
}
