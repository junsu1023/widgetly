//
//  DdayWidgetExtension.swift
//  DdayWidgetExtension
//

import WidgetKit
import SwiftUI
import SharedCore

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

struct WidgetDisplayState {
    let label: String
    let backgroundColorArgb: Int64
    let foregroundColorArgb: Int64
}

func mapToWidgetState(entry: DdayEntry?, themeId: String?, today: Date) -> WidgetDisplayState {
    let label = DdayCalculatorKt.ddayDisplayLabel(entry: entry, today: today.toKotlinLocalDate())
    let theme = ThemeCatalog.shared.find(id: themeId ?? ThemeCatalog.shared.DEFAULT_THEME_ID)
    return WidgetDisplayState(
        label: label,
        backgroundColorArgb: theme.backgroundColorArgb,
        foregroundColorArgb: theme.foregroundColorArgb
    )
}

extension Color {
    init(argb: Int64) {
        let a = Double((argb >> 24) & 0xFF) / 255.0
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

struct SimpleEntry: TimelineEntry {
    let date: Date
    let label: String
    let backgroundColorArgb: Int64
    let foregroundColorArgb: Int64
}

struct Provider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        let theme = ThemeCatalog.shared.find(id: ThemeCatalog.shared.DEFAULT_THEME_ID)
        return SimpleEntry(
            date: Date(),
            label: "D-DAY",
            backgroundColorArgb: theme.backgroundColorArgb,
            foregroundColorArgb: theme.foregroundColorArgb
        )
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
        let selectedId = configuration.selectedEntry?.id
        var matchingEntry: DdayEntry? = nil
        if let selectedId {
            let allEntries = (try? await IosDdayStorage(suiteName: "group.com.example.widgetly").readAll()) ?? []
            matchingEntry = allEntries.first { $0.id == selectedId }
        }
        let state = mapToWidgetState(entry: matchingEntry, themeId: configuration.selectedTheme?.id, today: date)
        let label = selectedId == nil ? "설정 필요" : state.label
        return SimpleEntry(
            date: date,
            label: label,
            backgroundColorArgb: state.backgroundColorArgb,
            foregroundColorArgb: state.foregroundColorArgb
        )
    }
}

/// Shown instead of a calculated D-day label when there's nothing to
/// calculate - the widget isn't pointed at an entry yet, or the entry it was
/// pointed at no longer exists. See mapToWidgetState/makeEntry.
private let emptyStateLabels: Set<String> = ["설정 필요", "삭제됨"]

struct DdayWidgetExtensionEntryView: View {
    var entry: Provider.Entry

    var body: some View {
        if emptyStateLabels.contains(entry.label) {
            VStack(spacing: 4) {
                Image("Quokka")
                    .resizable()
                    .frame(width: 48, height: 48)
                Text(entry.label)
                    .font(.custom("DoHyeon-Regular", size: 20))
                    .foregroundStyle(Color(argb: entry.foregroundColorArgb))
            }
        } else {
            Text(entry.label)
                .font(.custom("DoHyeon-Regular", size: 20))
                .foregroundStyle(Color(argb: entry.foregroundColorArgb))
        }
    }
}

struct DdayWidgetExtension: Widget {
    let kind: String = "DdayWidgetExtension"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: ConfigurationAppIntent.self, provider: Provider()) { entry in
            DdayWidgetExtensionEntryView(entry: entry)
                .containerBackground(Color(argb: entry.backgroundColorArgb), for: .widget)
        }
    }
}

#Preview(as: .systemSmall) {
    DdayWidgetExtension()
} timeline: {
    let defaultTheme = ThemeCatalog.shared.find(id: ThemeCatalog.shared.DEFAULT_THEME_ID)
    SimpleEntry(date: .now, label: "D-DAY", backgroundColorArgb: defaultTheme.backgroundColorArgb, foregroundColorArgb: defaultTheme.foregroundColorArgb)
    SimpleEntry(date: .now, label: "삭제됨", backgroundColorArgb: defaultTheme.backgroundColorArgb, foregroundColorArgb: defaultTheme.foregroundColorArgb)
}
