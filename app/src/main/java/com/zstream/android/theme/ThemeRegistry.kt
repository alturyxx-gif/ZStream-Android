package com.zstream.android.theme

object ThemeRegistry {
    private val builtInThemes: List<ZStreamTheme> = ThemePresets.builtInThemes

    private val themesById: Map<String, ZStreamTheme> = buildMap {
        builtInThemes.forEach { put(it.id, it) }
        put("dark", builtInThemes.first { it.id == "classic" })
        put("default", builtInThemes.first { it.id == "classic" })
    }

    val allThemes: List<ZStreamTheme>
        get() = builtInThemes

    val defaultTheme: ZStreamTheme
        get() = themesById.getValue("classic")

    fun getThemeById(id: String?): ZStreamTheme {
        if (id.isNullOrBlank()) return defaultTheme
        return themesById[id] ?: defaultTheme
    }
}
