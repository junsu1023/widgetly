package com.example.widgetly.theme

data class Theme(
    val id: String,
    val name: String,
    val backgroundColorArgb: Long,
    val foregroundColorArgb: Long,
)

object ThemeCatalog {
    const val DEFAULT_THEME_ID: String = "lavender"

    val all: List<Theme> = listOf(
        Theme(
            id = DEFAULT_THEME_ID,
            name = "라벤더",
            backgroundColorArgb = 0xFFF5F0FFL,
            foregroundColorArgb = 0xFF3B2A5AL,
        ),
        Theme(
            id = "mint",
            name = "민트",
            backgroundColorArgb = 0xFFE8FFF5L,
            foregroundColorArgb = 0xFF0F5132L,
        ),
        Theme(
            id = "sunset",
            name = "선셋",
            backgroundColorArgb = 0xFFFFF0E5L,
            foregroundColorArgb = 0xFF7A3B12L,
        ),
    )

    fun find(id: String): Theme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_THEME_ID }
}
