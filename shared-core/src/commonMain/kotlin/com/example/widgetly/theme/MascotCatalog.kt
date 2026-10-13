package com.example.widgetly.theme

data class Mascot(
    val id: String,
    val name: String,
)

object MascotCatalog {
    const val DEFAULT_MASCOT_ID: String = "quokka"

    val all: List<Mascot> = listOf(
        Mascot(
            id = DEFAULT_MASCOT_ID,
            name = "쿼카",
        ),
        Mascot(
            id = "cat",
            name = "고양이",
        ),
        Mascot(
            id = "bear",
            name = "곰",
        ),
        Mascot(
            id = "rabbit",
            name = "토끼",
        ),
        Mascot(
            id = "hamster",
            name = "햄스터",
        ),
    )

    fun find(id: String): Mascot = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_MASCOT_ID }
}
