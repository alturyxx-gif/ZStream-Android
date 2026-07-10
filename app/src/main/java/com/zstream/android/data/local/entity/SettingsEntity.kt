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
    val applicationTheme: String = "classic",
    val applicationLanguage: String = "en",
    val enableThumbnails: Boolean = false,
    val enableImageLogos: Boolean = true,
    val enableCarouselView: Boolean = true,
    val gridRows: Int = 2,
    val enableMinimalCards: Boolean = false,
    val homeSectionCarouselLimit: Int = 20,
    val enableLowPerformanceMode: Boolean = false,
    val enablePauseOverlay: Boolean = false,

    // Playback Settings
    val defaultSubtitleLanguage: String? = null,
    val enableAutoplay: Boolean = true,
    val enableSkipCredits: Boolean = true,
    val enableAutoSkipSegments: Boolean = false,
    val enableNativeSubtitles: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val enableDoubleClickToSeek: Boolean = true,
    // How far double-tap-to-seek jumps, in seconds. Local-only, not synced.
    val doubleTapSeekSeconds: Int = 10,
    val enableNumberKeySeeking: Boolean = false,
    val enableHoldToBoost: Boolean = false,
    val videoBrightness: Int = 100,
    val videoContrast: Int = 100,
    val videoSaturation: Int = 100,
    val videoHueRotate: Int = 0,
    val volumeBoost: Int = 100,
    val videoScaleMode: String = "fit",
    val tvPipPosition: String = "bottom_end",
    // When leaving the app (home/recents) while a video is playing: false = pause playback,
    // true = automatically enter system Picture-in-Picture instead. Local-only, not synced.
    val autoPipEnabled: Boolean = false,
    // Phone only: whether trailers play in the in-app player or launch externally. Local-only, not synced.
    val trailersOpenInApp: Boolean = true,
    // Playback speed carried over to the next video played. Local-only, not synced.
    val defaultPlaybackSpeed: Float = 1f,

    // Discover/Home Settings
    val enableDiscover: Boolean = false, // Synced with backend, but the local UI no longer reads it.
    val enableFeatured: Boolean = true,

    // Source Settings
    val lastSuccessfulSource: String? = null,
    val enableLastSuccessfulSource: Boolean = false,
    val manualSourceSelection: Boolean = false,
    val allowParallelDownload: Boolean = false,
    val sourceOrder: List<String> = emptyList(),
    val enableSourceOrder: Boolean = false,

    // External Services
    val proxyUrls: List<String> = emptyList(),
    val febboxKey: String? = null,
    val debridToken: String? = null,
    val debridService: String = "realdebrid",
    val tidbKey: String? = null,
    val wyzieKey: String? = null,
    val proxyTmdb: Boolean = false, // Synced with backend, but the local UI/runtime no longer reads it.
    val tmdbApiKey: String? = null,

    // Subtitle Styling
    val subtitleColor: String = "#ffffff",
    val subtitleSize: Float = 1f,
    val subtitleBackgroundOpacity: Float = 0f,
    val subtitleBackgroundBlur: Float = 0f,
    val subtitleBackgroundBlurEnabled: Boolean = false,
    val subtitleBold: Boolean = false,
    val subtitleVerticalPosition: Float = 0f,
    val subtitleFontStyle: String = "dropShadow",
    val subtitleBorderThickness: Float = 1f,
    val subtitleLineHeight: Float = 1.2f,
    val subtitleFont: String = "sans-serif-condensed",

    // Interface
    val homeSectionOrder: List<String> = emptyList(),
    val groupOrder: List<String> = emptyList(),
    val forceCompactEpisodeView: Boolean = false,

    // Other
    val enableAutoResumeOnPlaybackError: Boolean = true,
    
    // Virtual Keyboard
    val enableNativeKeyboard: Boolean = false,
    
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
        lastSuccessfulSource?.let { json.put("lastSuccessfulSource", it) }
        json.put("enableLastSuccessfulSource", enableLastSuccessfulSource)
        json.put("manualSourceSelection", manualSourceSelection)
        json.put("allowParallelDownload", allowParallelDownload)
        json.put("sourceOrder", JSONArray(sourceOrder))
        json.put("enableSourceOrder", enableSourceOrder)
        json.put("enableImageLogos", enableImageLogos)
        json.put("enableCarouselView", enableCarouselView)
        json.put("enableMinimalCards", enableMinimalCards)
        json.put("forceCompactEpisodeView", forceCompactEpisodeView)
        json.put("enableLowPerformanceMode", enableLowPerformanceMode)
        json.put("enablePauseOverlay", enablePauseOverlay)
        json.put("proxyTmdb", proxyTmdb)
        json.put("homeSectionOrder", JSONArray(homeSectionOrderForSync()))
        json.put("groupOrder", JSONArray(groupOrder))
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
