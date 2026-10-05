//
//  DdayWidgetExtension.swift
//  DdayWidgetExtension
//

import WidgetKit
import SwiftUI
import Shared

private extension Date {
    func toKotlinLocalDate() -> Kotlinx_datetimeLocalDate {
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: self)
        return Kotlinx_datetimeLocalDate(
            year: Int32(components.year!),
            month: Int32(components.month!),
            day: Int32(components.day!)
        )
    }
}

struct SimpleEntry: TimelineEntry {
    let date: Date
    let label: String
}

struct Provider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        SimpleEntry(date: Date(), label: "D-DAY")
    }

    func snapshot(for configuration: ConfigurationAppIntent, in context: Context) async -> SimpleEntry {
        await makeEntry(for: configuration, on: Date())
    }

    func timeline(for configuration: ConfigurationAppIntent, in context: Context) async -> Timeline<SimpleEntry> {
        let calendar = Calendar(identifier: .gregorian)
        let startOfToday = calendar.startOfDay(for: Date())
        var entries: [SimpleEntry] = []
        for dayOffset in 0..<7 {
            guard let entryDate = calendar.date(byAdding: .day, value: dayOffset, to: startOfToday) else { continue }
            entries.append(await makeEntry(for: configuration, on: entryDate))
        }
        let nextWindowStart = calendar.date(byAdding: .day, value: 7, to: startOfToday) ?? Date()
        return Timeline(entries: entries, policy: .after(nextWindowStart))
    }

    private func makeEntry(for configuration: ConfigurationAppIntent, on date: Date) async -> SimpleEntry {
        guard let selectedId = configuration.selectedEntry?.id else {
            return SimpleEntry(date: date, label: "설정 필요")
        }
        let allEntries = (try? await IosDdayStorage(suiteName: "group.com.example.widgetly").readAll()) ?? []
        let matchingEntry = allEntries.first { $0.id == selectedId }
        let label = DdayCalculatorKt.ddayDisplayLabel(entry: matchingEntry, today: date.toKotlinLocalDate())
        return SimpleEntry(date: date, label: label)
    }
}

struct DdayWidgetExtensionEntryView: View {
    var entry: Provider.Entry

    var body: some View {
        Text(entry.label)
    }
}

struct DdayWidgetExtension: Widget {
    let kind: String = "DdayWidgetExtension"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: ConfigurationAppIntent.self, provider: Provider()) { entry in
            DdayWidgetExtensionEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
    }
}

#Preview(as: .systemSmall) {
    DdayWidgetExtension()
} timeline: {
    SimpleEntry(date: .now, label: "D-DAY")
    SimpleEntry(date: .now, label: "삭제됨")
}
