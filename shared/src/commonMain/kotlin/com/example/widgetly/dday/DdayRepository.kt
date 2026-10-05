package com.example.widgetly.dday

class DdayRepository(private val storage: DdayStorage) {
    suspend fun list(): List<DdayEntry> = storage.readAll()

    suspend fun upsert(entry: DdayEntry) {
        require(entry.title.isNotBlank()) { "title must not be blank" }
        val current = storage.readAll()
        val replaced = current.filterNot { it.id == entry.id } + entry
        storage.writeAll(replaced)
    }

    suspend fun delete(id: String) {
        val current = storage.readAll()
        storage.writeAll(current.filterNot { it.id == id })
    }
}
