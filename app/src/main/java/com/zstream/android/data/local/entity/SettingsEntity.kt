package com.zstream.android.data.local.entity

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing all user application settings
 * Based on reference: /p-stream/src/backend/accounts/settings.ts
 * This is stored in DataStore (not Room) for quick access and atomic updates
 */
data class SettingsEntity(
    // UI/Display Settings
    val applicationTheme: String = "default",
    val applicationLanguage: String = "en",
    val enableThumbnails: Boolean = true,
    val enableImageLogos: Boolean = true,
    val enableCarouselView: Boolean = true,
    val enableMinimalCards: Boolean = false,
    val enableLowPerformanceMode: Boolean = false,
    val enablePauseOverlay: Boolean = true,

    // Playback Settings
    val defaultSubtitleLanguage: String? = null,
    val enableAutoplay: Boolean = true,
    val enableSkipCredits: Boolean = false,
    val enableAutoSkipSegments: Boolean = false,
    val enableNativeSubtitles: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val enableDoubleClickToSeek: Boolean = true,
    val enableNumberKeySeeking: Boolean = false,
    val enableHoldToBoost: Boolean = false,
    val videoBrightness: Int = 100,
    val videoContrast: Int = 100,
    val videoSaturation: Int = 100,
    val videoHueRotate: Int = 0,
    val volumeBoost: Int = 100,
    val videoScaleMode: String = "fit",

    // Discover/Home Settings
    val enableDiscover: Boolean = true,
    val enableFeatured: Boolean = true,
    val enableDetailsModal: Boolean = true,

    // Source Settings
    val lastSuccessfulSource: String? = null,
    val enableLastSuccessfulSource: Boolean = false,
    val manualSourceSelection: Boolean = false,
    val sourceOrder: List<String> = emptyList(),
    val enableSourceOrder: Boolean = false,
    val embedOrder: List<String> = emptyList(),
    val enableEmbedOrder: Boolean = false,

    // External Services
    val proxyUrls: List<String> = emptyList(),
    val febboxKey: String? = null,
    val debridToken: String? = null,
    val debridService: String = "realdebrid",
    val tidbKey: String? = null,
    val proxyTmdb: Boolean = false,
    val tmdbApiKey: String? = null,

    // Subtitle Styling
    val subtitleColor: String = "#ffffff",
    val subtitleSize: Float = 1f,
    val subtitleBackgroundOpacity: Float = 0.5f,
    val subtitleBackgroundBlur: Float = 0.5f,
    val subtitleBackgroundBlurEnabled: Boolean = true,
    val subtitleBold: Boolean = false,
    val subtitleVerticalPosition: Float = 1f,
    val subtitleFontStyle: String = "default",
    val subtitleBorderThickness: Float = 1f,
    val subtitleLineHeight: Float = 1.5f,
    val subtitleFont: String = "sans-serif",

    // Interface
    val homeSectionOrder: List<String> = emptyList(),
    val forceCompactEpisodeView: Boolean = false,

    // Other
    val enableAutoResumeOnPlaybackError: Boolean = true,
    
    // Theme
    val customTheme: CustomThemeSettings? = null
) {
    private fun homeSectionOrderForSync(): List<String> = homeSectionOrder.map { section ->
        when (section) {
            "continue_watching" -> "watching"
            else -> section
        }
    }

    /**
     * Build a JSON request body containing only the fields that the backend's
     * settings endpoint knows about.  Fields not present in the JSON are
     * skipped by the backend (hasOwnProperty check), so we omit `null` values
     * instead of sending `"field": null` (which would overwrite the remote value).
     *
     * Backend schema: backend/server/routes/users/[id]/settings.ts
     */
    fun toSyncableJsonString(): String {
        val json = JSONObject()
        json.put("applicationTheme", applicationTheme)
        customTheme?.let { theme ->
            json.put("customTheme", theme.toJson())
        }
        json.put("applicationLanguage", applicationLanguage)
        defaultSubtitleLanguage?.let { json.put("defaultSubtitleLanguage", it) }
        json.put("enableThumbnails", enableThumbnails)
        json.put("enableAutoplay", enableAutoplay)
        json.put("enableSkipCredits", enableSkipCredits)
        json.put("enableAutoSkipSegments", enableAutoSkipSegments)
        json.put("enableNativeSubtitles", enableNativeSubtitles)
        json.put("enableDoubleClickToSeek", enableDoubleClickToSeek)
        json.put("enableNumberKeySeeking", enableNumberKeySeeking)
        json.put("enableHoldToBoost", enableHoldToBoost)
        json.put("enableDiscover", enableDiscover)
        json.put("enableFeatured", enableFeatured)
        json.put("enableDetailsModal", enableDetailsModal)
        lastSuccessfulSource?.let { json.put("lastSuccessfulSource", it) }
        json.put("enableLastSuccessfulSource", enableLastSuccessfulSource)
        json.put("manualSourceSelection", manualSourceSelection)
        json.put("sourceOrder", JSONArray(sourceOrder))
        json.put("enableSourceOrder", enableSourceOrder)
        json.put("embedOrder", JSONArray(embedOrder))
        json.put("enableEmbedOrder", enableEmbedOrder)
        json.put("enableImageLogos", enableImageLogos)
        json.put("enableCarouselView", enableCarouselView)
        json.put("enableMinimalCards", enableMinimalCards)
        json.put("forceCompactEpisodeView", forceCompactEpisodeView)
        json.put("enableLowPerformanceMode", enableLowPerformanceMode)
        json.put("enablePauseOverlay", enablePauseOverlay)
        json.put("proxyTmdb", proxyTmdb)
        json.put("homeSectionOrder", JSONArray(homeSectionOrderForSync()))
        json.put("enableAutoResumeOnPlaybackError", enableAutoResumeOnPlaybackError)
        febboxKey?.let { json.put("febboxKey", it) }
        debridToken?.let { json.put("debridToken", it) }
        json.put("debridService", debridService)
        tidbKey?.let { json.put("tidbKey", it) }
        json.put("proxyUrls", JSONArray(proxyUrls))
        return json.toString()
    }

    fun toSyncableJsonBody(): okhttp3.RequestBody {
        val jsonStr = toSyncableJsonString()
        Log.d("SettingsEntity", "toSyncableJsonBody: $jsonStr")
        return jsonStr.toRequestBody("application/json".toMediaType())
    }
}

data class CustomThemeSettings(
    val primary: String? = null,
    val secondary: String? = null,
    val tertiary: String? = null,
    val activeTheme: ThemeColors? = null,
    val savedCustomThemes: List<SavedCustomTheme> = emptyList(),
    val hiddenDefaultThemes: List<String> = emptyList()
)

private fun CustomThemeSettings.toJson(): JSONObject = JSONObject().apply {
    primary?.let { put("primary", it) }
    secondary?.let { put("secondary", it) }
    tertiary?.let { put("tertiary", it) }
    activeTheme?.let { active ->
        put("activeTheme", JSONObject().apply {
            put("primary", active.primary)
            put("secondary", active.secondary)
            put("tertiary", active.tertiary)
        })
    }
    put("savedCustomThemes", JSONArray(savedCustomThemes.map { it.toJson() }))
    put("hiddenDefaultThemes", JSONArray(hiddenDefaultThemes))
}

data class ThemeColors(
    val primary: String,
    val secondary: String,
    val tertiary: String
)

data class SavedCustomTheme(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val customPrimaryHex: String? = null,
    val customSecondaryHex: String? = null,
    val customTertiaryHex: String? = null
)

private fun SavedCustomTheme.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("primary", primary)
    put("secondary", secondary)
    put("tertiary", tertiary)
    customPrimaryHex?.let { put("customPrimaryHex", it) }
    customSecondaryHex?.let { put("customSecondaryHex", it) }
    customTertiaryHex?.let { put("customTertiaryHex", it) }
}
