package com.zstream.android.data.local.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zstream.android.data.local.entity.CustomThemeSettings
import com.zstream.android.data.local.entity.SavedCustomTheme
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.entity.ThemeColors
import com.zstream.android.di.TmdbTokenCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("app_settings")

/**
 * Based on reference: /p-stream/src/backend/accounts/settings.ts
 * Manages persistent app settings like theme, language, autoplay, etc.
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    // UI/Display Settings
    private val KEY_APPLICATION_THEME = stringPreferencesKey("application_theme")
    private val KEY_APPLICATION_FONT = stringPreferencesKey("application_font")
    private val KEY_KIDS_MODE_ENABLED = booleanPreferencesKey("kids_mode_enabled")
    private val KEY_CUSTOM_THEME = stringPreferencesKey("custom_theme")
    private val KEY_APPLICATION_LANGUAGE = stringPreferencesKey("application_language")
    private val KEY_ENABLE_THUMBNAILS = booleanPreferencesKey("enable_thumbnails")
    private val KEY_ENABLE_IMAGE_LOGOS = booleanPreferencesKey("enable_image_logos")
    private val KEY_ENABLE_CAROUSEL_VIEW = booleanPreferencesKey("enable_carousel_view")
    private val KEY_GRID_ROWS = intPreferencesKey("grid_rows")
    private val KEY_ENABLE_MINIMAL_CARDS = booleanPreferencesKey("enable_minimal_cards")
    private val KEY_HOME_SECTION_CAROUSEL_LIMIT = intPreferencesKey("home_section_carousel_limit")
    private val KEY_ENABLE_LOW_PERFORMANCE_MODE = booleanPreferencesKey("enable_low_performance_mode")
    private val KEY_ENABLE_PAUSE_OVERLAY = booleanPreferencesKey("enable_pause_overlay")

    // Playback Settings
    private val KEY_DEFAULT_SUBTITLE_LANGUAGE = stringPreferencesKey("default_subtitle_language")
    private val KEY_ENABLE_AUTOPLAY = booleanPreferencesKey("enable_autoplay")
    private val KEY_ENABLE_SKIP_CREDITS = booleanPreferencesKey("enable_skip_credits")
    private val KEY_ENABLE_AUTO_SKIP_SEGMENTS = booleanPreferencesKey("enable_auto_skip_segments")
    private val KEY_ENABLE_NATIVE_SUBTITLES = booleanPreferencesKey("enable_native_subtitles")
    private val KEY_SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
    private val KEY_ENABLE_DOUBLE_CLICK_TO_SEEK = booleanPreferencesKey("enable_double_click_to_seek")
    private val KEY_DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
    private val KEY_ENABLE_NUMBER_KEY_SEEKING = booleanPreferencesKey("enable_number_key_seeking")
    private val KEY_ENABLE_HOLD_TO_BOOST = booleanPreferencesKey("enable_hold_to_boost")
    private val KEY_VIDEO_BRIGHTNESS = intPreferencesKey("video_brightness")
    private val KEY_VIDEO_CONTRAST = intPreferencesKey("video_contrast")
    private val KEY_VIDEO_SATURATION = intPreferencesKey("video_saturation")
    private val KEY_VIDEO_HUE_ROTATE = intPreferencesKey("video_hue_rotate")
    private val KEY_VOLUME_BOOST = intPreferencesKey("volume_boost")
    private val KEY_VIDEO_SCALE_MODE = stringPreferencesKey("video_scale_mode")
    private val KEY_TV_PIP_POSITION = stringPreferencesKey("tv_pip_position")
    private val KEY_AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
    private val KEY_TRAILERS_OPEN_IN_APP = booleanPreferencesKey("trailers_open_in_app")
    private val KEY_DEFAULT_PLAYBACK_SPEED = stringPreferencesKey("default_playback_speed")

    // Discover/Home Settings
    private val KEY_ENABLE_DISCOVER = booleanPreferencesKey("enable_discover")
    private val KEY_ENABLE_FEATURED = booleanPreferencesKey("enable_featured")

    // Source Settings
    private val KEY_LAST_SUCCESSFUL_SOURCE = stringPreferencesKey("last_successful_source")
    private val KEY_ENABLE_LAST_SUCCESSFUL_SOURCE = booleanPreferencesKey("enable_last_successful_source")
    private val KEY_MANUAL_SOURCE_SELECTION = booleanPreferencesKey("manual_source_selection")
    private val KEY_ALLOW_PARALLEL_DOWNLOAD = booleanPreferencesKey("allow_parallel_download")
    private val KEY_SOURCE_ORDER = stringPreferencesKey("source_order") // Stored as comma-separated
    private val KEY_ENABLE_SOURCE_ORDER = booleanPreferencesKey("enable_source_order")

    // External Services
    private val KEY_PROXY_URLS = stringPreferencesKey("proxy_urls")
    private val KEY_FEBBOX_KEY = stringPreferencesKey("febbox_key")
    private val KEY_DEBRID_TOKEN = stringPreferencesKey("debrid_token")
    private val KEY_DEBRID_SERVICE = stringPreferencesKey("debrid_service")
    private val KEY_TIDB_KEY = stringPreferencesKey("tidb_key")
    private val KEY_WYZIE_KEY = stringPreferencesKey("wyzie_key")
    private val KEY_PROXY_TMDB = booleanPreferencesKey("proxy_tmdb")
    private val KEY_TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")

    // Interface
    private val KEY_HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
    private val KEY_GROUP_ORDER = stringPreferencesKey("group_order")
    private val KEY_FORCE_COMPACT_EPISODE_VIEW = booleanPreferencesKey("force_compact_episode_view")

    // Subtitle Styling
    private val KEY_SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
    private val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
    private val KEY_SUBTITLE_BACKGROUND_OPACITY = stringPreferencesKey("subtitle_background_opacity")
    private val KEY_SUBTITLE_BACKGROUND_BLUR = stringPreferencesKey("subtitle_background_blur")
    private val KEY_SUBTITLE_BACKGROUND_BLUR_ENABLED = booleanPreferencesKey("subtitle_background_blur_enabled")
    private val KEY_SUBTITLE_BOLD = booleanPreferencesKey("subtitle_bold")
    private val KEY_SUBTITLE_VERTICAL_POSITION = stringPreferencesKey("subtitle_vertical_position")
    private val KEY_SUBTITLE_FONT_STYLE = stringPreferencesKey("subtitle_font_style")
    private val KEY_SUBTITLE_BORDER_THICKNESS = stringPreferencesKey("subtitle_border_thickness")
    private val KEY_SUBTITLE_LINE_HEIGHT = stringPreferencesKey("subtitle_line_height")
    private val KEY_SUBTITLE_FONT = stringPreferencesKey("subtitle_font")

    // Other
    private val KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR = booleanPreferencesKey("enable_auto_resume_on_playback_error")
    private val KEY_ENABLE_NATIVE_KEYBOARD = booleanPreferencesKey("enable_native_keyboard")

    // Mirrors the language codes offered in the subtitle language picker (SettingsScreen.kt) —
    // used only to decide whether the device's locale is a sane first-run subtitle default.
    private val SUBTITLE_LANGUAGE_AUTO_DETECT_CODES = setOf(
        "en", "es", "fr", "de", "pt", "it", "nl", "ja", "ko", "zh", "ar", "hi", "ru", "tr"
    )

    /**
     * Observe all settings as a data class
     */
    val settings: Flow<SettingsEntity> = context.settingsStore.data.map { prefs ->
        SettingsEntity(
            applicationTheme = prefs[KEY_APPLICATION_THEME] ?: "classic",
            applicationFont = prefs[KEY_APPLICATION_FONT] ?: "onest",
            kidsModeEnabled = prefs[KEY_KIDS_MODE_ENABLED] ?: false,
            customTheme = decodeCustomTheme(prefs[KEY_CUSTOM_THEME]),
            applicationLanguage = prefs[KEY_APPLICATION_LANGUAGE] ?: "en",
            defaultSubtitleLanguage = prefs[KEY_DEFAULT_SUBTITLE_LANGUAGE]
                ?: java.util.Locale.getDefault().language.takeIf { it in SUBTITLE_LANGUAGE_AUTO_DETECT_CODES },
            enableThumbnails = prefs[KEY_ENABLE_THUMBNAILS] ?: false,
            enableImageLogos = prefs[KEY_ENABLE_IMAGE_LOGOS] ?: true,
            enableCarouselView = prefs[KEY_ENABLE_CAROUSEL_VIEW] ?: true,
            gridRows = (prefs[KEY_GRID_ROWS] ?: 2).coerceIn(1, 8),
            enableMinimalCards = prefs[KEY_ENABLE_MINIMAL_CARDS] ?: false,
            homeSectionCarouselLimit = (prefs[KEY_HOME_SECTION_CAROUSEL_LIMIT] ?: 20).coerceIn(1, 50),
            enableLowPerformanceMode = prefs[KEY_ENABLE_LOW_PERFORMANCE_MODE] ?: false,
            enablePauseOverlay = prefs[KEY_ENABLE_PAUSE_OVERLAY] ?: false,
            enableAutoplay = prefs[KEY_ENABLE_AUTOPLAY] ?: true,
            enableSkipCredits = prefs[KEY_ENABLE_SKIP_CREDITS] ?: false,
            enableAutoSkipSegments = prefs[KEY_ENABLE_AUTO_SKIP_SEGMENTS] ?: false,
            enableNativeSubtitles = prefs[KEY_ENABLE_NATIVE_SUBTITLES] ?: false,
            subtitlesEnabled = prefs[KEY_SUBTITLES_ENABLED] ?: false,
            enableDoubleClickToSeek = prefs[KEY_ENABLE_DOUBLE_CLICK_TO_SEEK] ?: true,
            doubleTapSeekSeconds = prefs[KEY_DOUBLE_TAP_SEEK_SECONDS] ?: 10,
            enableNumberKeySeeking = prefs[KEY_ENABLE_NUMBER_KEY_SEEKING] ?: false,
            enableHoldToBoost = prefs[KEY_ENABLE_HOLD_TO_BOOST] ?: false,
            videoBrightness = prefs[KEY_VIDEO_BRIGHTNESS] ?: 100,
            videoContrast = prefs[KEY_VIDEO_CONTRAST] ?: 100,
            videoSaturation = prefs[KEY_VIDEO_SATURATION] ?: 100,
            videoHueRotate = prefs[KEY_VIDEO_HUE_ROTATE] ?: 0,
            volumeBoost = prefs[KEY_VOLUME_BOOST] ?: 100,
            videoScaleMode = prefs[KEY_VIDEO_SCALE_MODE] ?: "fit",
            tvPipPosition = prefs[KEY_TV_PIP_POSITION] ?: "bottom_end",
            autoPipEnabled = prefs[KEY_AUTO_PIP_ENABLED] ?: true,
            trailersOpenInApp = prefs[KEY_TRAILERS_OPEN_IN_APP] ?: true,
            defaultPlaybackSpeed = prefs[KEY_DEFAULT_PLAYBACK_SPEED]?.toFloatOrNull() ?: 1f,
            enableDiscover = prefs[KEY_ENABLE_DISCOVER] ?: false,
            enableFeatured = prefs[KEY_ENABLE_FEATURED] ?: true,
            lastSuccessfulSource = prefs[KEY_LAST_SUCCESSFUL_SOURCE],
            enableLastSuccessfulSource = prefs[KEY_ENABLE_LAST_SUCCESSFUL_SOURCE] ?: false,
            manualSourceSelection = prefs[KEY_MANUAL_SOURCE_SELECTION] ?: false,
            allowParallelDownload = prefs[KEY_ALLOW_PARALLEL_DOWNLOAD] ?: false,
            sourceOrder = prefs[KEY_SOURCE_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            enableSourceOrder = prefs[KEY_ENABLE_SOURCE_ORDER] ?: false,
            proxyUrls = prefs[KEY_PROXY_URLS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            febboxKey = prefs[KEY_FEBBOX_KEY],
            debridToken = prefs[KEY_DEBRID_TOKEN],
            debridService = prefs[KEY_DEBRID_SERVICE] ?: "realdebrid",
            tidbKey = prefs[KEY_TIDB_KEY],
            wyzieKey = prefs[KEY_WYZIE_KEY],
            proxyTmdb = prefs[KEY_PROXY_TMDB] ?: false,
            tmdbApiKey = prefs[KEY_TMDB_API_KEY],
            homeSectionOrder = prefs[KEY_HOME_SECTION_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            groupOrder = prefs[KEY_GROUP_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            forceCompactEpisodeView = prefs[KEY_FORCE_COMPACT_EPISODE_VIEW] ?: false,
            enableAutoResumeOnPlaybackError = prefs[KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR] ?: true,
            enableNativeKeyboard = prefs[KEY_ENABLE_NATIVE_KEYBOARD] ?: false,
            subtitleColor = prefs[KEY_SUBTITLE_COLOR] ?: "#ffffff",
            subtitleSize = prefs[KEY_SUBTITLE_SIZE]?.toFloatOrNull() ?: 1f,
            subtitleBackgroundOpacity = prefs[KEY_SUBTITLE_BACKGROUND_OPACITY]?.toFloatOrNull() ?: 0f,
            subtitleBackgroundBlur = prefs[KEY_SUBTITLE_BACKGROUND_BLUR]?.toFloatOrNull() ?: 0f,
            subtitleBackgroundBlurEnabled = prefs[KEY_SUBTITLE_BACKGROUND_BLUR_ENABLED] ?: false,
            subtitleBold = prefs[KEY_SUBTITLE_BOLD] ?: false,
            subtitleVerticalPosition = (prefs[KEY_SUBTITLE_VERTICAL_POSITION]?.toFloatOrNull() ?: 0f).coerceAtLeast(-15f),
            subtitleFontStyle = prefs[KEY_SUBTITLE_FONT_STYLE] ?: "dropShadow",
            subtitleBorderThickness = prefs[KEY_SUBTITLE_BORDER_THICKNESS]?.toFloatOrNull() ?: 1f,
            subtitleLineHeight = prefs[KEY_SUBTITLE_LINE_HEIGHT]?.toFloatOrNull() ?: 1.2f,
            subtitleFont = prefs[KEY_SUBTITLE_FONT] ?: "sans-serif-condensed",
        ).also { entity ->
            TmdbTokenCache.token = entity.tmdbApiKey
        }
    }

    // Individual setter functions for each setting

    suspend fun updateSettings(entity: SettingsEntity, syncToRemote: Boolean = true) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_THEME] = entity.applicationTheme
            prefs[KEY_APPLICATION_FONT] = entity.applicationFont
            prefs[KEY_KIDS_MODE_ENABLED] = entity.kidsModeEnabled
            if (entity.customTheme != null) {
                prefs[KEY_CUSTOM_THEME] = gson.toJson(entity.customTheme)
            } else {
                prefs.remove(KEY_CUSTOM_THEME)
            }
            prefs[KEY_APPLICATION_LANGUAGE] = entity.applicationLanguage
            if (entity.defaultSubtitleLanguage != null) {
                prefs[KEY_DEFAULT_SUBTITLE_LANGUAGE] = entity.defaultSubtitleLanguage
            } else {
                prefs.remove(KEY_DEFAULT_SUBTITLE_LANGUAGE)
            }
            prefs[KEY_ENABLE_THUMBNAILS] = entity.enableThumbnails
            prefs[KEY_ENABLE_IMAGE_LOGOS] = entity.enableImageLogos
            prefs[KEY_ENABLE_CAROUSEL_VIEW] = entity.enableCarouselView
            prefs[KEY_GRID_ROWS] = entity.gridRows
            prefs[KEY_ENABLE_MINIMAL_CARDS] = entity.enableMinimalCards
            prefs[KEY_HOME_SECTION_CAROUSEL_LIMIT] = entity.homeSectionCarouselLimit.coerceIn(1, 50)
            prefs[KEY_ENABLE_LOW_PERFORMANCE_MODE] = entity.enableLowPerformanceMode
            prefs[KEY_ENABLE_PAUSE_OVERLAY] = entity.enablePauseOverlay
            prefs[KEY_ENABLE_AUTOPLAY] = entity.enableAutoplay
            prefs[KEY_ENABLE_SKIP_CREDITS] = entity.enableSkipCredits
            prefs[KEY_ENABLE_AUTO_SKIP_SEGMENTS] = entity.enableAutoSkipSegments
            prefs[KEY_ENABLE_NATIVE_SUBTITLES] = entity.enableNativeSubtitles
            prefs[KEY_SUBTITLES_ENABLED] = entity.subtitlesEnabled
            prefs[KEY_ENABLE_DOUBLE_CLICK_TO_SEEK] = entity.enableDoubleClickToSeek
            prefs[KEY_DOUBLE_TAP_SEEK_SECONDS] = entity.doubleTapSeekSeconds
            prefs[KEY_ENABLE_NUMBER_KEY_SEEKING] = entity.enableNumberKeySeeking
            prefs[KEY_ENABLE_HOLD_TO_BOOST] = entity.enableHoldToBoost
            prefs[KEY_VIDEO_BRIGHTNESS] = entity.videoBrightness
            prefs[KEY_VIDEO_CONTRAST] = entity.videoContrast
            prefs[KEY_VIDEO_SATURATION] = entity.videoSaturation
            prefs[KEY_VIDEO_HUE_ROTATE] = entity.videoHueRotate
            prefs[KEY_VOLUME_BOOST] = entity.volumeBoost
            prefs[KEY_VIDEO_SCALE_MODE] = entity.videoScaleMode
            prefs[KEY_TV_PIP_POSITION] = entity.tvPipPosition
            prefs[KEY_AUTO_PIP_ENABLED] = entity.autoPipEnabled
            prefs[KEY_TRAILERS_OPEN_IN_APP] = entity.trailersOpenInApp
            prefs[KEY_DEFAULT_PLAYBACK_SPEED] = entity.defaultPlaybackSpeed.toString()
            prefs[KEY_ENABLE_DISCOVER] = entity.enableDiscover
            prefs[KEY_ENABLE_FEATURED] = entity.enableFeatured
            if (entity.lastSuccessfulSource != null) {
                prefs[KEY_LAST_SUCCESSFUL_SOURCE] = entity.lastSuccessfulSource
            } else {
                prefs.remove(KEY_LAST_SUCCESSFUL_SOURCE)
            }
            prefs[KEY_ENABLE_LAST_SUCCESSFUL_SOURCE] = entity.enableLastSuccessfulSource
            prefs[KEY_MANUAL_SOURCE_SELECTION] = entity.manualSourceSelection
            prefs[KEY_ALLOW_PARALLEL_DOWNLOAD] = entity.allowParallelDownload
            prefs[KEY_SOURCE_ORDER] = entity.sourceOrder.joinToString(",")
            prefs[KEY_ENABLE_SOURCE_ORDER] = entity.enableSourceOrder
            prefs[KEY_PROXY_URLS] = entity.proxyUrls.joinToString(",")
            if (entity.febboxKey != null) prefs[KEY_FEBBOX_KEY] = entity.febboxKey else prefs.remove(KEY_FEBBOX_KEY)
            if (entity.debridToken != null) prefs[KEY_DEBRID_TOKEN] = entity.debridToken else prefs.remove(KEY_DEBRID_TOKEN)
            prefs[KEY_DEBRID_SERVICE] = entity.debridService
            if (entity.tidbKey != null) prefs[KEY_TIDB_KEY] = entity.tidbKey else prefs.remove(KEY_TIDB_KEY)
            if (entity.wyzieKey != null) prefs[KEY_WYZIE_KEY] = entity.wyzieKey else prefs.remove(KEY_WYZIE_KEY)
            prefs[KEY_PROXY_TMDB] = entity.proxyTmdb
            if (entity.tmdbApiKey != null) {
                prefs[KEY_TMDB_API_KEY] = entity.tmdbApiKey
                TmdbTokenCache.token = entity.tmdbApiKey
            } else {
                prefs.remove(KEY_TMDB_API_KEY)
                TmdbTokenCache.token = null
            }
            prefs[KEY_HOME_SECTION_ORDER] = entity.homeSectionOrder.joinToString(",")
            prefs[KEY_GROUP_ORDER] = entity.groupOrder.joinToString(",")
            prefs[KEY_FORCE_COMPACT_EPISODE_VIEW] = entity.forceCompactEpisodeView
            prefs[KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR] = entity.enableAutoResumeOnPlaybackError
            prefs[KEY_ENABLE_NATIVE_KEYBOARD] = entity.enableNativeKeyboard
            prefs[KEY_SUBTITLE_COLOR] = entity.subtitleColor
            prefs[KEY_SUBTITLE_SIZE] = entity.subtitleSize.toString()
            prefs[KEY_SUBTITLE_BACKGROUND_OPACITY] = entity.subtitleBackgroundOpacity.toString()
            prefs[KEY_SUBTITLE_BACKGROUND_BLUR] = entity.subtitleBackgroundBlur.toString()
            prefs[KEY_SUBTITLE_BACKGROUND_BLUR_ENABLED] = entity.subtitleBackgroundBlurEnabled
            prefs[KEY_SUBTITLE_BOLD] = entity.subtitleBold
            prefs[KEY_SUBTITLE_VERTICAL_POSITION] = entity.subtitleVerticalPosition.coerceAtLeast(-15f).toString()
            prefs[KEY_SUBTITLE_FONT_STYLE] = entity.subtitleFontStyle
            prefs[KEY_SUBTITLE_BORDER_THICKNESS] = entity.subtitleBorderThickness.toString()
            prefs[KEY_SUBTITLE_LINE_HEIGHT] = entity.subtitleLineHeight.toString()
            prefs[KEY_SUBTITLE_FONT] = entity.subtitleFont
        }
        
        if (syncToRemote) {
            // This should ideally be handled by a worker or a background task
            // but for now we'll just try to sync it if possible
        }
    }

    /**
     * Fetch settings from remote and update local DataStore
     * Preserves local-only fields (subtitle styling) that are not synced from backend.
     */
    suspend fun syncFromRemote(userId: String, auth: String, api: com.zstream.android.data.remote.BackendApi) {
        try {
            val remote = api.getSettings(userId, auth)
            // Read current local settings first to preserve local-only fields
            val current = context.settingsStore.data.first()
            val currentColor = current[KEY_SUBTITLE_COLOR] ?: "#ffffff"
            val currentSize = current[KEY_SUBTITLE_SIZE]?.toFloatOrNull() ?: 1f
            val currentBgOpacity = current[KEY_SUBTITLE_BACKGROUND_OPACITY]?.toFloatOrNull() ?: 0.5f
            val currentBgBlur = current[KEY_SUBTITLE_BACKGROUND_BLUR]?.toFloatOrNull() ?: 0.5f
            val currentBgBlurEnabled = current[KEY_SUBTITLE_BACKGROUND_BLUR_ENABLED] ?: true
            val currentBold = current[KEY_SUBTITLE_BOLD] ?: false
            val currentVPos = (current[KEY_SUBTITLE_VERTICAL_POSITION]?.toFloatOrNull() ?: 1f).coerceAtLeast(-15f)
            val currentFontStyle = current[KEY_SUBTITLE_FONT_STYLE] ?: "default"
            val currentBorderThickness = current[KEY_SUBTITLE_BORDER_THICKNESS]?.toFloatOrNull() ?: 1f
            val currentLineHeight = current[KEY_SUBTITLE_LINE_HEIGHT]?.toFloatOrNull() ?: 1.5f
            val currentFont = current[KEY_SUBTITLE_FONT] ?: "sans-serif"
            val currentTmdbApiKey = current[KEY_TMDB_API_KEY]
            val currentWyzieKey = current[KEY_WYZIE_KEY]
            val currentSubtitlesEnabled = current[KEY_SUBTITLES_ENABLED] ?: false
            val currentVideoBrightness = current[KEY_VIDEO_BRIGHTNESS] ?: 100
            val currentVideoContrast = current[KEY_VIDEO_CONTRAST] ?: 100
            val currentVideoSaturation = current[KEY_VIDEO_SATURATION] ?: 100
            val currentVideoHueRotate = current[KEY_VIDEO_HUE_ROTATE] ?: 0
            val currentVolumeBoost = current[KEY_VOLUME_BOOST] ?: 100
            val currentVideoScaleMode = current[KEY_VIDEO_SCALE_MODE] ?: "fit"
            val currentTvPipPosition = current[KEY_TV_PIP_POSITION] ?: "bottom_end"
            val currentAutoPipEnabled = current[KEY_AUTO_PIP_ENABLED] ?: true
            val currentTrailersOpenInApp = current[KEY_TRAILERS_OPEN_IN_APP] ?: true
            val currentDefaultPlaybackSpeed = current[KEY_DEFAULT_PLAYBACK_SPEED]?.toFloatOrNull() ?: 1f
            val currentDoubleTapSeekSeconds = current[KEY_DOUBLE_TAP_SEEK_SECONDS] ?: 10
            val currentGridRows = (current[KEY_GRID_ROWS] ?: 2).coerceIn(1, 8)
            val currentApplicationFont = current[KEY_APPLICATION_FONT] ?: "onest"
            val currentKidsModeEnabled = current[KEY_KIDS_MODE_ENABLED] ?: false

            val mappedHomeSectionOrder = remote.homeSectionOrder?.map { section ->
                when (section) {
                    "watching" -> "continue_watching"
                    else -> section
                }
            } ?: emptyList()

            updateSettings(SettingsEntity(
                applicationTheme = remote.applicationTheme ?: "classic",
                applicationFont = currentApplicationFont,
                kidsModeEnabled = currentKidsModeEnabled,
                customTheme = remote.customTheme?.toEntity(),
                applicationLanguage = remote.applicationLanguage ?: "en",
                defaultSubtitleLanguage = remote.defaultSubtitleLanguage,
                enableThumbnails = remote.enableThumbnails ?: false,
                enableImageLogos = remote.enableImageLogos ?: true,
                enableCarouselView = remote.enableCarouselView ?: true,
                gridRows = currentGridRows,
                enableMinimalCards = remote.enableMinimalCards ?: false,
                enableLowPerformanceMode = remote.enableLowPerformanceMode ?: false,
                enablePauseOverlay = remote.enablePauseOverlay ?: false,
                enableAutoplay = remote.enableAutoplay ?: true,
                enableSkipCredits = remote.enableSkipCredits ?: false,
                enableAutoSkipSegments = remote.enableAutoSkipSegments ?: false,
                enableNativeSubtitles = remote.enableNativeSubtitles ?: false,
                subtitlesEnabled = currentSubtitlesEnabled,
                enableDoubleClickToSeek = remote.enableDoubleClickToSeek ?: true,
                doubleTapSeekSeconds = currentDoubleTapSeekSeconds,
                enableNumberKeySeeking = remote.enableNumberKeySeeking ?: false,
                enableHoldToBoost = remote.enableHoldToBoost ?: false,
                videoBrightness = currentVideoBrightness,
                videoContrast = currentVideoContrast,
                videoSaturation = currentVideoSaturation,
                videoHueRotate = currentVideoHueRotate,
                volumeBoost = currentVolumeBoost,
                videoScaleMode = currentVideoScaleMode,
                tvPipPosition = currentTvPipPosition,
                autoPipEnabled = currentAutoPipEnabled,
                trailersOpenInApp = currentTrailersOpenInApp,
                defaultPlaybackSpeed = currentDefaultPlaybackSpeed,
                enableDiscover = remote.enableDiscover ?: false,
                enableFeatured = remote.enableFeatured ?: true,
                lastSuccessfulSource = remote.lastSuccessfulSource,
                enableLastSuccessfulSource = remote.enableLastSuccessfulSource ?: false,
                manualSourceSelection = remote.manualSourceSelection ?: false,
                sourceOrder = remote.sourceOrder ?: emptyList(),
                enableSourceOrder = remote.enableSourceOrder ?: false,
                proxyUrls = remote.proxyUrls ?: emptyList(),
                febboxKey = remote.febboxKey,
                debridToken = remote.debridToken,
                debridService = remote.debridService ?: "realdebrid",
                tidbKey = remote.tidbKey,
                wyzieKey = currentWyzieKey,
                proxyTmdb = remote.proxyTmdb ?: false,
                tmdbApiKey = currentTmdbApiKey,
                homeSectionOrder = mappedHomeSectionOrder,
                groupOrder = remote.groupOrder ?: emptyList(),
                forceCompactEpisodeView = remote.forceCompactEpisodeView ?: false,
                enableAutoResumeOnPlaybackError = remote.enableAutoResumeOnPlaybackError ?: true,
                enableNativeKeyboard = current[KEY_ENABLE_NATIVE_KEYBOARD] ?: false,
                subtitleColor = currentColor,
                subtitleSize = currentSize,
                subtitleBackgroundOpacity = currentBgOpacity,
                subtitleBackgroundBlur = currentBgBlur,
                subtitleBackgroundBlurEnabled = currentBgBlurEnabled,
                subtitleBold = currentBold,
                subtitleVerticalPosition = currentVPos,
                subtitleFontStyle = currentFontStyle,
                subtitleBorderThickness = currentBorderThickness,
                subtitleLineHeight = currentLineHeight,
                subtitleFont = currentFont,
            ), syncToRemote = false)
            Log.d("SettingsPreferences", "Successfully synced settings from remote")
        } catch (e: Exception) {
            Log.e("SettingsPreferences", "Failed to sync settings from remote", e)
        }
    }

    suspend fun syncGroupOrderToRemote(userId: String, auth: String, api: com.zstream.android.data.remote.BackendApi, groupOrder: List<String>) {
        try {
            api.updateGroupOrder(userId, auth, groupOrder)
        } catch (e: Exception) {
            Log.e("SettingsPreferences", "Failed to sync group order to remote", e)
        }
    }

    suspend fun appendGroupOrder(groups: List<String>): List<String> {
        val normalized = groups.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return emptyList()
        // Read-modify-write happens inside the same edit{} transform (DataStore serializes
        // concurrent edit{} calls) instead of a separate .data.first() read beforehand, which
        // could race a concurrent appendGroupOrder/setGroupOrder call and silently drop it.
        var next: List<String> = emptyList()
        context.settingsStore.edit { prefs ->
            val existing = prefs[KEY_GROUP_ORDER]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            next = (existing + normalized).distinct()
            prefs[KEY_GROUP_ORDER] = next.joinToString(",")
        }
        return next
    }

    suspend fun setGroupOrder(order: List<String>) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_GROUP_ORDER] = order.joinToString(",")
        }
    }

    suspend fun setApplicationTheme(theme: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_THEME] = theme
        }
    }

    suspend fun setApplicationFont(font: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_FONT] = font
        }
    }

    suspend fun setKidsModeEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_KIDS_MODE_ENABLED] = enabled
        }
    }

    suspend fun setCustomTheme(customTheme: CustomThemeSettings?) {
        context.settingsStore.edit { prefs ->
            if (customTheme == null) {
                prefs.remove(KEY_CUSTOM_THEME)
            } else {
                prefs[KEY_CUSTOM_THEME] = gson.toJson(customTheme)
            }
        }
    }

    suspend fun setApplicationLanguage(language: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_LANGUAGE] = language
        }
    }

    suspend fun setDefaultSubtitleLanguage(language: String?) {
        context.settingsStore.edit { prefs ->
            if (language != null) {
                prefs[KEY_DEFAULT_SUBTITLE_LANGUAGE] = language
            } else {
                prefs.remove(KEY_DEFAULT_SUBTITLE_LANGUAGE)
            }
        }
    }

    suspend fun setEnableThumbnails(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_THUMBNAILS] = enabled
        }
    }

    suspend fun setEnableImageLogos(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_IMAGE_LOGOS] = enabled
        }
    }

    suspend fun setEnableCarouselView(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_CAROUSEL_VIEW] = enabled
        }
    }

    suspend fun setEnableMinimalCards(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_MINIMAL_CARDS] = enabled
        }
    }

    suspend fun setEnableLowPerformanceMode(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_LOW_PERFORMANCE_MODE] = enabled
        }
    }

    suspend fun setEnablePauseOverlay(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_PAUSE_OVERLAY] = enabled
        }
    }

    suspend fun setEnableAutoplay(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_AUTOPLAY] = enabled
        }
    }

    suspend fun setEnableSkipCredits(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_SKIP_CREDITS] = enabled
        }
    }

    suspend fun setEnableAutoSkipSegments(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_AUTO_SKIP_SEGMENTS] = enabled
        }
    }

    suspend fun setEnableNativeSubtitles(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_NATIVE_SUBTITLES] = enabled
        }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_SUBTITLES_ENABLED] = enabled
        }
    }

    suspend fun setEnableDoubleClickToSeek(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_DOUBLE_CLICK_TO_SEEK] = enabled
        }
    }

    suspend fun setEnableNumberKeySeeking(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_NUMBER_KEY_SEEKING] = enabled
        }
    }

    suspend fun setEnableHoldToBoost(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_HOLD_TO_BOOST] = enabled
        }
    }

    suspend fun setVideoBrightness(value: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VIDEO_BRIGHTNESS] = value
        }
    }

    suspend fun setVideoContrast(value: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VIDEO_CONTRAST] = value
        }
    }

    suspend fun setVideoSaturation(value: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VIDEO_SATURATION] = value
        }
    }

    suspend fun setVideoHueRotate(value: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VIDEO_HUE_ROTATE] = value
        }
    }

    suspend fun setVolumeBoost(value: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VOLUME_BOOST] = value
        }
    }

    suspend fun setVideoScaleMode(value: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_VIDEO_SCALE_MODE] = value
        }
    }

    suspend fun setTvPipPosition(value: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_TV_PIP_POSITION] = value
        }
    }

    suspend fun setAutoPipEnabled(value: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_AUTO_PIP_ENABLED] = value
        }
    }

    suspend fun setTrailersOpenInApp(value: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_TRAILERS_OPEN_IN_APP] = value
        }
    }

    suspend fun setDefaultPlaybackSpeed(value: Float) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_DEFAULT_PLAYBACK_SPEED] = value.toString()
        }
    }

    suspend fun setEnableDiscover(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_DISCOVER] = enabled
        }
    }

    suspend fun setEnableFeatured(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_FEATURED] = enabled
        }
    }

    suspend fun setLastSuccessfulSource(source: String?) {
        context.settingsStore.edit { prefs ->
            if (source != null) {
                prefs[KEY_LAST_SUCCESSFUL_SOURCE] = source
            } else {
                prefs.remove(KEY_LAST_SUCCESSFUL_SOURCE)
            }
        }
    }

    suspend fun setEnableLastSuccessfulSource(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_LAST_SUCCESSFUL_SOURCE] = enabled
        }
    }

    suspend fun setManualSourceSelection(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_MANUAL_SOURCE_SELECTION] = enabled
        }
    }

    suspend fun setAllowParallelDownload(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ALLOW_PARALLEL_DOWNLOAD] = enabled
        }
    }

    suspend fun setForceCompactEpisodeView(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_FORCE_COMPACT_EPISODE_VIEW] = enabled
        }
    }

    suspend fun setEnableAutoResumeOnPlaybackError(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR] = enabled
        }
    }

    suspend fun setEnableNativeKeyboard(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_NATIVE_KEYBOARD] = enabled
        }
    }

    /**
     * Clear all settings
     */
    suspend fun clear() {
        context.settingsStore.edit { prefs ->
            prefs.clear()
        }
    }

    // Subtitle styling individual setters

    suspend fun setSubtitleColor(color: String) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_COLOR] = color }
    }

    suspend fun setSubtitleSize(size: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_SIZE] = size.toString() }
    }

    suspend fun setSubtitleBackgroundOpacity(opacity: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_BACKGROUND_OPACITY] = opacity.toString() }
    }

    suspend fun setSubtitleBackgroundBlur(blur: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_BACKGROUND_BLUR] = blur.toString() }
    }

    suspend fun setSubtitleBackgroundBlurEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_BACKGROUND_BLUR_ENABLED] = enabled }
    }

    suspend fun setSubtitleBold(bold: Boolean) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_BOLD] = bold }
    }

    suspend fun setSubtitleVerticalPosition(pos: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_VERTICAL_POSITION] = pos.toString() }
    }

    suspend fun setSubtitleFontStyle(style: String) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_FONT_STYLE] = style }
    }

    suspend fun setSubtitleBorderThickness(thickness: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_BORDER_THICKNESS] = thickness.toString() }
    }

    suspend fun setSubtitleLineHeight(height: Float) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_LINE_HEIGHT] = height.toString() }
    }

    suspend fun setSubtitleFont(font: String) {
        context.settingsStore.edit { prefs -> prefs[KEY_SUBTITLE_FONT] = font }
    }

    suspend fun setTmdbApiKey(key: String?) {
        context.settingsStore.edit { prefs ->
            if (key != null) {
                prefs[KEY_TMDB_API_KEY] = key
                TmdbTokenCache.token = key
            } else {
                prefs.remove(KEY_TMDB_API_KEY)
                TmdbTokenCache.token = null
            }
        }
    }

    private fun decodeCustomTheme(raw: String?): CustomThemeSettings? {
        if (raw.isNullOrBlank()) return null
        return try {
            gson.fromJson(raw, CustomThemeSettings::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}

private fun com.zstream.android.data.remote.CustomThemeSettingsResponse.toEntity(): CustomThemeSettings =
    CustomThemeSettings(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        activeTheme = activeTheme?.let {
            ThemeColors(
                primary = it.primary,
                secondary = it.secondary,
                tertiary = it.tertiary,
            )
        },
        savedCustomThemes = savedCustomThemes?.map { saved ->
            SavedCustomTheme(
                id = saved.id,
                name = saved.name,
                primary = saved.primary,
                secondary = saved.secondary,
                tertiary = saved.tertiary,
                customPrimaryHex = saved.customPrimaryHex,
                customSecondaryHex = saved.customSecondaryHex,
                customTertiaryHex = saved.customTertiaryHex,
            )
        } ?: emptyList(),
        hiddenDefaultThemes = hiddenDefaultThemes ?: emptyList(),
    )
