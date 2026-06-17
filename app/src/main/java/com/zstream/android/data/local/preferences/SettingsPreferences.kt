package com.zstream.android.data.local.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zstream.android.data.local.entity.SettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    // UI/Display Settings
    private val KEY_APPLICATION_THEME = stringPreferencesKey("application_theme")
    private val KEY_APPLICATION_LANGUAGE = stringPreferencesKey("application_language")
    private val KEY_ENABLE_THUMBNAILS = booleanPreferencesKey("enable_thumbnails")
    private val KEY_ENABLE_IMAGE_LOGOS = booleanPreferencesKey("enable_image_logos")
    private val KEY_ENABLE_CAROUSEL_VIEW = booleanPreferencesKey("enable_carousel_view")
    private val KEY_ENABLE_MINIMAL_CARDS = booleanPreferencesKey("enable_minimal_cards")
    private val KEY_ENABLE_LOW_PERFORMANCE_MODE = booleanPreferencesKey("enable_low_performance_mode")
    private val KEY_ENABLE_PAUSE_OVERLAY = booleanPreferencesKey("enable_pause_overlay")

    // Playback Settings
    private val KEY_DEFAULT_SUBTITLE_LANGUAGE = stringPreferencesKey("default_subtitle_language")
    private val KEY_ENABLE_AUTOPLAY = booleanPreferencesKey("enable_autoplay")
    private val KEY_ENABLE_SKIP_CREDITS = booleanPreferencesKey("enable_skip_credits")
    private val KEY_ENABLE_AUTO_SKIP_SEGMENTS = booleanPreferencesKey("enable_auto_skip_segments")
    private val KEY_ENABLE_NATIVE_SUBTITLES = booleanPreferencesKey("enable_native_subtitles")
    private val KEY_ENABLE_DOUBLE_CLICK_TO_SEEK = booleanPreferencesKey("enable_double_click_to_seek")
    private val KEY_ENABLE_NUMBER_KEY_SEEKING = booleanPreferencesKey("enable_number_key_seeking")
    private val KEY_ENABLE_HOLD_TO_BOOST = booleanPreferencesKey("enable_hold_to_boost")

    // Discover/Home Settings
    private val KEY_ENABLE_DISCOVER = booleanPreferencesKey("enable_discover")
    private val KEY_ENABLE_FEATURED = booleanPreferencesKey("enable_featured")
    private val KEY_ENABLE_DETAILS_MODAL = booleanPreferencesKey("enable_details_modal")

    // Source Settings
    private val KEY_LAST_SUCCESSFUL_SOURCE = stringPreferencesKey("last_successful_source")
    private val KEY_ENABLE_LAST_SUCCESSFUL_SOURCE = booleanPreferencesKey("enable_last_successful_source")
    private val KEY_MANUAL_SOURCE_SELECTION = booleanPreferencesKey("manual_source_selection")
    private val KEY_SOURCE_ORDER = stringPreferencesKey("source_order") // Stored as comma-separated
    private val KEY_ENABLE_SOURCE_ORDER = booleanPreferencesKey("enable_source_order")
    private val KEY_EMBED_ORDER = stringPreferencesKey("embed_order") // Stored as comma-separated
    private val KEY_ENABLE_EMBED_ORDER = booleanPreferencesKey("enable_embed_order")

    // External Services
    private val KEY_PROXY_URLS = stringPreferencesKey("proxy_urls")
    private val KEY_FEBBOX_KEY = stringPreferencesKey("febbox_key")
    private val KEY_DEBRID_TOKEN = stringPreferencesKey("debrid_token")
    private val KEY_DEBRID_SERVICE = stringPreferencesKey("debrid_service")
    private val KEY_TIDB_KEY = stringPreferencesKey("tidb_key")
    private val KEY_PROXY_TMDB = booleanPreferencesKey("proxy_tmdb")

    // Interface
    private val KEY_HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
    private val KEY_FORCE_COMPACT_EPISODE_VIEW = booleanPreferencesKey("force_compact_episode_view")

    // Other
    private val KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR = booleanPreferencesKey("enable_auto_resume_on_playback_error")

    /**
     * Observe all settings as a data class
     */
    val settings: Flow<SettingsEntity> = context.settingsStore.data.map { prefs ->
        SettingsEntity(
            applicationTheme = prefs[KEY_APPLICATION_THEME] ?: "dark",
            applicationLanguage = prefs[KEY_APPLICATION_LANGUAGE] ?: "en",
            defaultSubtitleLanguage = prefs[KEY_DEFAULT_SUBTITLE_LANGUAGE],
            enableThumbnails = prefs[KEY_ENABLE_THUMBNAILS] ?: true,
            enableImageLogos = prefs[KEY_ENABLE_IMAGE_LOGOS] ?: true,
            enableCarouselView = prefs[KEY_ENABLE_CAROUSEL_VIEW] ?: true,
            enableMinimalCards = prefs[KEY_ENABLE_MINIMAL_CARDS] ?: false,
            enableLowPerformanceMode = prefs[KEY_ENABLE_LOW_PERFORMANCE_MODE] ?: false,
            enablePauseOverlay = prefs[KEY_ENABLE_PAUSE_OVERLAY] ?: true,
            enableAutoplay = prefs[KEY_ENABLE_AUTOPLAY] ?: true,
            enableSkipCredits = prefs[KEY_ENABLE_SKIP_CREDITS] ?: false,
            enableAutoSkipSegments = prefs[KEY_ENABLE_AUTO_SKIP_SEGMENTS] ?: false,
            enableNativeSubtitles = prefs[KEY_ENABLE_NATIVE_SUBTITLES] ?: true,
            enableDoubleClickToSeek = prefs[KEY_ENABLE_DOUBLE_CLICK_TO_SEEK] ?: true,
            enableNumberKeySeeking = prefs[KEY_ENABLE_NUMBER_KEY_SEEKING] ?: false,
            enableHoldToBoost = prefs[KEY_ENABLE_HOLD_TO_BOOST] ?: false,
            enableDiscover = prefs[KEY_ENABLE_DISCOVER] ?: true,
            enableFeatured = prefs[KEY_ENABLE_FEATURED] ?: true,
            enableDetailsModal = prefs[KEY_ENABLE_DETAILS_MODAL] ?: true,
            lastSuccessfulSource = prefs[KEY_LAST_SUCCESSFUL_SOURCE],
            enableLastSuccessfulSource = prefs[KEY_ENABLE_LAST_SUCCESSFUL_SOURCE] ?: false,
            manualSourceSelection = prefs[KEY_MANUAL_SOURCE_SELECTION] ?: false,
            sourceOrder = prefs[KEY_SOURCE_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            enableSourceOrder = prefs[KEY_ENABLE_SOURCE_ORDER] ?: false,
            embedOrder = prefs[KEY_EMBED_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            enableEmbedOrder = prefs[KEY_ENABLE_EMBED_ORDER] ?: false,
            proxyUrls = prefs[KEY_PROXY_URLS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            febboxKey = prefs[KEY_FEBBOX_KEY],
            debridToken = prefs[KEY_DEBRID_TOKEN],
            debridService = prefs[KEY_DEBRID_SERVICE] ?: "realdebrid",
            tidbKey = prefs[KEY_TIDB_KEY],
            proxyTmdb = prefs[KEY_PROXY_TMDB] ?: false,
            homeSectionOrder = prefs[KEY_HOME_SECTION_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            forceCompactEpisodeView = prefs[KEY_FORCE_COMPACT_EPISODE_VIEW] ?: false,
            enableAutoResumeOnPlaybackError = prefs[KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR] ?: true,
        )
    }

    // Individual setter functions for each setting

    suspend fun updateSettings(entity: SettingsEntity, syncToRemote: Boolean = true) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_THEME] = entity.applicationTheme
            prefs[KEY_APPLICATION_LANGUAGE] = entity.applicationLanguage
            if (entity.defaultSubtitleLanguage != null) {
                prefs[KEY_DEFAULT_SUBTITLE_LANGUAGE] = entity.defaultSubtitleLanguage
            } else {
                prefs.remove(KEY_DEFAULT_SUBTITLE_LANGUAGE)
            }
            prefs[KEY_ENABLE_THUMBNAILS] = entity.enableThumbnails
            prefs[KEY_ENABLE_IMAGE_LOGOS] = entity.enableImageLogos
            prefs[KEY_ENABLE_CAROUSEL_VIEW] = entity.enableCarouselView
            prefs[KEY_ENABLE_MINIMAL_CARDS] = entity.enableMinimalCards
            prefs[KEY_ENABLE_LOW_PERFORMANCE_MODE] = entity.enableLowPerformanceMode
            prefs[KEY_ENABLE_PAUSE_OVERLAY] = entity.enablePauseOverlay
            prefs[KEY_ENABLE_AUTOPLAY] = entity.enableAutoplay
            prefs[KEY_ENABLE_SKIP_CREDITS] = entity.enableSkipCredits
            prefs[KEY_ENABLE_AUTO_SKIP_SEGMENTS] = entity.enableAutoSkipSegments
            prefs[KEY_ENABLE_NATIVE_SUBTITLES] = entity.enableNativeSubtitles
            prefs[KEY_ENABLE_DOUBLE_CLICK_TO_SEEK] = entity.enableDoubleClickToSeek
            prefs[KEY_ENABLE_NUMBER_KEY_SEEKING] = entity.enableNumberKeySeeking
            prefs[KEY_ENABLE_HOLD_TO_BOOST] = entity.enableHoldToBoost
            prefs[KEY_ENABLE_DISCOVER] = entity.enableDiscover
            prefs[KEY_ENABLE_FEATURED] = entity.enableFeatured
            prefs[KEY_ENABLE_DETAILS_MODAL] = entity.enableDetailsModal
            if (entity.lastSuccessfulSource != null) {
                prefs[KEY_LAST_SUCCESSFUL_SOURCE] = entity.lastSuccessfulSource
            } else {
                prefs.remove(KEY_LAST_SUCCESSFUL_SOURCE)
            }
            prefs[KEY_ENABLE_LAST_SUCCESSFUL_SOURCE] = entity.enableLastSuccessfulSource
            prefs[KEY_MANUAL_SOURCE_SELECTION] = entity.manualSourceSelection
            prefs[KEY_SOURCE_ORDER] = entity.sourceOrder.joinToString(",")
            prefs[KEY_ENABLE_SOURCE_ORDER] = entity.enableSourceOrder
            prefs[KEY_EMBED_ORDER] = entity.embedOrder.joinToString(",")
            prefs[KEY_ENABLE_EMBED_ORDER] = entity.enableEmbedOrder
            prefs[KEY_PROXY_URLS] = entity.proxyUrls.joinToString(",")
            if (entity.febboxKey != null) prefs[KEY_FEBBOX_KEY] = entity.febboxKey else prefs.remove(KEY_FEBBOX_KEY)
            if (entity.debridToken != null) prefs[KEY_DEBRID_TOKEN] = entity.debridToken else prefs.remove(KEY_DEBRID_TOKEN)
            prefs[KEY_DEBRID_SERVICE] = entity.debridService
            if (entity.tidbKey != null) prefs[KEY_TIDB_KEY] = entity.tidbKey else prefs.remove(KEY_TIDB_KEY)
            prefs[KEY_PROXY_TMDB] = entity.proxyTmdb
            prefs[KEY_HOME_SECTION_ORDER] = entity.homeSectionOrder.joinToString(",")
            prefs[KEY_FORCE_COMPACT_EPISODE_VIEW] = entity.forceCompactEpisodeView
            prefs[KEY_ENABLE_AUTO_RESUME_ON_PLAYBACK_ERROR] = entity.enableAutoResumeOnPlaybackError
        }
        
        if (syncToRemote) {
            // This should ideally be handled by a worker or a background task
            // but for now we'll just try to sync it if possible
        }
    }

    /**
     * Fetch settings from remote and update local DataStore
     */
    suspend fun syncFromRemote(userId: String, auth: String, api: com.zstream.android.data.remote.BackendApi) {
        try {
            val remote = api.getSettings(userId, auth)
            updateSettings(SettingsEntity(
                applicationTheme = remote.applicationTheme ?: "dark",
                applicationLanguage = remote.applicationLanguage ?: "en",
                defaultSubtitleLanguage = remote.defaultSubtitleLanguage,
                enableThumbnails = remote.enableThumbnails ?: true,
                enableImageLogos = remote.enableImageLogos ?: true,
                enableCarouselView = remote.enableCarouselView ?: true,
                enableMinimalCards = remote.enableMinimalCards ?: false,
                enableLowPerformanceMode = remote.enableLowPerformanceMode ?: false,
                enablePauseOverlay = remote.enablePauseOverlay ?: true,
                enableAutoplay = remote.enableAutoplay ?: true,
                enableSkipCredits = remote.enableSkipCredits ?: false,
                enableAutoSkipSegments = remote.enableAutoSkipSegments ?: false,
                enableNativeSubtitles = remote.enableNativeSubtitles ?: true,
                enableDoubleClickToSeek = remote.enableDoubleClickToSeek ?: true,
                enableNumberKeySeeking = remote.enableNumberKeySeeking ?: false,
                enableHoldToBoost = remote.enableHoldToBoost ?: false,
                enableDiscover = remote.enableDiscover ?: true,
                enableFeatured = remote.enableFeatured ?: true,
                enableDetailsModal = remote.enableDetailsModal ?: true,
                lastSuccessfulSource = remote.lastSuccessfulSource,
                enableLastSuccessfulSource = remote.enableLastSuccessfulSource ?: false,
                manualSourceSelection = remote.manualSourceSelection ?: false,
                sourceOrder = remote.sourceOrder ?: emptyList(),
                enableSourceOrder = remote.enableSourceOrder ?: false,
                embedOrder = remote.embedOrder ?: emptyList(),
                enableEmbedOrder = remote.enableEmbedOrder ?: false,
                proxyUrls = remote.proxyUrls ?: emptyList(),
                febboxKey = remote.febboxKey,
                debridToken = remote.debridToken,
                debridService = remote.debridService ?: "realdebrid",
                tidbKey = remote.tidbKey,
                proxyTmdb = remote.proxyTmdb ?: false,
                homeSectionOrder = remote.homeSectionOrder ?: emptyList(),
                forceCompactEpisodeView = remote.forceCompactEpisodeView ?: false,
                enableAutoResumeOnPlaybackError = remote.enableAutoResumeOnPlaybackError ?: true,
            ), syncToRemote = false)
            Log.d("SettingsPreferences", "Successfully synced settings from remote")
        } catch (e: Exception) {
            Log.e("SettingsPreferences", "Failed to sync settings from remote", e)
        }
    }

    suspend fun setApplicationTheme(theme: String) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_APPLICATION_THEME] = theme
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

    suspend fun setEnableDetailsModal(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_ENABLE_DETAILS_MODAL] = enabled
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

    /**
     * Clear all settings
     */
    suspend fun clear() {
        context.settingsStore.edit { prefs ->
            prefs.clear()
        }
    }
}
