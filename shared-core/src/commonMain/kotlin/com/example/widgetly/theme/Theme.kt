package com.example.widgetly.theme

data class Theme(
    val id: String,
    val name: String,
    val backgroundColorArgb: Long,
    val foregroundColorArgb: Long,
)

object ThemeCatalog {
    const val DEFAULT_THEME_ID: String = "peach"

    val all: List<Theme> = listOf(
        Theme(
            id = DEFAULT_THEME_ID,
            name = "피치",
            backgroundColorArgb = 0xFFFFF0E5L,
            foregroundColorArgb = 0xFF7A3B12L,
        ),
        Theme(
            id = "lemon",
            name = "레몬",
            backgroundColorArgb = 0xFFFFFBE0L,
            foregroundColorArgb = 0xFF6B5800L,
        ),
        Theme(
            id = "skyblue",
            name = "스카이블루",
            backgroundColorArgb = 0xFFE6F4FFL,
            foregroundColorArgb = 0xFF1B4B73L,
        ),
        Theme(
            id = "cream",
            name = "크림",
            backgroundColorArgb = 0xFFFFF8EEL,
            foregroundColorArgb = 0xFF5C4A33L,
        ),
    )

    fun find(id: String): Theme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_THEME_ID }
}
