//
//  AppIntent.swift
//  DdayWidgetExtension
//

import WidgetKit
import AppIntents
import SharedCore

struct DdayEntryPickerEntity: AppEntity {
    let id: String
    let title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "디데이"
    static var defaultQuery = DdayEntryPickerQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)")
    }
}

struct DdayEntryPickerQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [DdayEntryPickerEntity] {
        let all = try await fetchAllEntries()
        return all.filter { identifiers.contains($0.id) }.map { DdayEntryPickerEntity(id: $0.id, title: $0.title) }
    }

    func suggestedEntities() async throws -> [DdayEntryPickerEntity] {
        let all = try await fetchAllEntries()
        return all.map { DdayEntryPickerEntity(id: $0.id, title: $0.title) }
    }

    private func fetchAllEntries() async throws -> [DdayEntry] {
        try await IosDdayStorage(suiteName: "group.com.example.widgetly").readAll()
    }
}

struct ThemeEntity: AppEntity {
    let id: String
    let name: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "테마"
    static var defaultQuery = ThemeEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }
}

struct ThemeEntityQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [ThemeEntity] {
        ThemeCatalog.shared.all.filter { identifiers.contains($0.id) }.map { ThemeEntity(id: $0.id, name: $0.name) }
    }

    func suggestedEntities() async throws -> [ThemeEntity] {
        ThemeCatalog.shared.all.map { ThemeEntity(id: $0.id, name: $0.name) }
    }
}

struct ConfigurationAppIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource { "디데이 선택" }
    static var description: IntentDescription { "위젯에 표시할 디데이를 선택하세요" }

    @Parameter(title: "디데이")
    var selectedEntry: DdayEntryPickerEntity?

    @Parameter(title: "테마")
    var selectedTheme: ThemeEntity?
}
