package com.zstream.android.data.local.entity

/**
 * Data class representing all user application settings
 * Based on reference: /p-stream/src/backend/accounts/settings.ts
 * This is stored in DataStore (not Room) for quick access and atomic updates
 */
data class SettingsEntity(
    // UI/Display Settings
    val applicationTheme: String = "dark",
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
    val enableDoubleClickToSeek: Boolean = true,
    val enableNumberKeySeeking: Boolean = false,
    val enableHoldToBoost: Boolean = false,

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

    // Interface
    val homeSectionOrder: List<String> = emptyList(),
    val forceCompactEpisodeView: Boolean = false,

    // Other
    val enableAutoResumeOnPlaybackError: Boolean = true,
    
    // Theme
    val customTheme: CustomThemeSettings? = null
)

data class CustomThemeSettings(
    val primary: String? = null,
    val secondary: String? = null,
    val tertiary: String? = null,
    val activeTheme: ThemeColors? = null,
    val savedCustomThemes: List<SavedCustomTheme> = emptyList(),
    val hiddenDefaultThemes: List<String> = emptyList()
)

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
    val tertiary: String
)
