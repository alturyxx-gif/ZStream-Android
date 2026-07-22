package com.zstream.android.theme

import com.zstream.android.theme.signature.signatureZStreamThemes

object ThemeRegistry {
    private val builtInThemes: List<ZStreamTheme> = ThemePresets.builtInThemes
    private val allBuiltInThemes: List<ZStreamTheme> = builtInThemes + signatureZStreamThemes

    private val themesById: Map<String, ZStreamTheme> = buildMap {
        allBuiltInThemes.forEach { put(it.id, it) }
        put("dark", builtInThemes.first { it.id == "classic" })
        put("default", builtInThemes.first { it.id == "classic" })
    }

    val allThemes: List<ZStreamTheme>
        get() = builtInThemes

    val signatureThemes: List<ZStreamTheme>
        get() = signatureZStreamThemes

    val defaultTheme: ZStreamTheme
        get() = themesById.getValue("classic")

    fun getThemeById(id: String?): ZStreamTheme {
        if (id.isNullOrBlank()) return defaultTheme
        return themesById[id] ?: defaultTheme
    }
}
