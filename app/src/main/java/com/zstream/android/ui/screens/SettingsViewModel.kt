package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.ProgressRepository
import com.zstream.android.data.AccountRepository
import com.zstream.android.data.AccountSession
import com.zstream.android.data.local.entity.CustomThemeSettings
import com.zstream.android.data.local.entity.SavedCustomTheme
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.entity.ThemeColors
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.CustomThemeSettingsResponse
import com.zstream.android.data.remote.SavedCustomThemeResponse
import com.zstream.android.data.remote.ThemeTripletResponse
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPreferences,
    private val accountRepo: AccountRepository,
    private val progressRepo: ProgressRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val api: BackendApi,
) : ViewModel() {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty

    // Persists the user's last hand-crafted subtitle settings so they can be
    // restored any time they tap "Custom" after switching to a named preset.
    private val _customSubtitleSlot = MutableStateFlow<SettingsEntity?>(null)

    val settings: StateFlow<SettingsEntity> = settingsPrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab

    private var savedEntity: SettingsEntity = SettingsEntity()

    init {
        viewModelScope.launch {
            settings.collect { entity ->
                savedEntity = entity
                _dirty.value = false
            }
        }
    }

    fun setTab(index: Int) { _currentTab.value = index }

    private fun update(transform: SettingsEntity.() -> SettingsEntity) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.transform()
            settingsPrefs.updateSettings(updated, syncToRemote = false)
            _dirty.value = updated != savedEntity
        }
    }

    fun setApplicationLanguage(lang: String) {
        update { copy(applicationLanguage = lang) }
    }

    fun setApplicationTheme(theme: String) {
        update { copy(applicationTheme = theme) }
    }

    fun setEnableThumbnails(v: Boolean) {
        update { copy(enableThumbnails = v) }
    }

    fun setEnableAutoplay(v: Boolean) {
        update { copy(enableAutoplay = v) }
    }

    fun setEnableSkipCredits(v: Boolean) {
        update { copy(enableSkipCredits = v) }
    }

    fun setEnableDiscover(v: Boolean) {
        update { copy(enableDiscover = v) }
    }

    fun setEnableFeatured(v: Boolean) {
        update { copy(enableFeatured = v) }
    }

    fun setEnableDetailsModal(v: Boolean) {
        update { copy(enableDetailsModal = v) }
    }

    fun setEnableImageLogos(v: Boolean) {
        update { copy(enableImageLogos = v) }
    }

    fun setEnableCarouselView(v: Boolean) {
        update { copy(enableCarouselView = v) }
    }

    fun setGridRows(v: Int) {
        update { copy(gridRows = v) }
    }

    fun setEnableMinimalCards(v: Boolean) {
        update { copy(enableMinimalCards = v) }
    }

    fun setForceCompactEpisodeView(v: Boolean) {
        update { copy(forceCompactEpisodeView = v) }
    }

    fun setEnableLowPerformanceMode(v: Boolean) {
        update { copy(enableLowPerformanceMode = v) }
    }

    fun setEnableNativeSubtitles(v: Boolean) {
        update { copy(enableNativeSubtitles = v) }
    }

    fun setEnablePauseOverlay(v: Boolean) {
        update { copy(enablePauseOverlay = v) }
    }

    fun setEnableHoldToBoost(v: Boolean) {
        update { copy(enableHoldToBoost = v) }
    }

    fun setManualSourceSelection(v: Boolean) {
        update { copy(manualSourceSelection = v) }
    }

    fun setEnableDoubleClickToSeek(v: Boolean) {
        update { copy(enableDoubleClickToSeek = v) }
    }

    fun setEnableAutoResumeOnPlaybackError(v: Boolean) {
        update { copy(enableAutoResumeOnPlaybackError = v) }
    }

    fun setEnableSourceOrder(v: Boolean) {
        update { copy(enableSourceOrder = v) }
    }

    fun setEnableEmbedOrder(v: Boolean) {
        update { copy(enableEmbedOrder = v) }
    }

    fun setProxyTmdb(v: Boolean) {
        update { copy(proxyTmdb = v) }
    }

    fun setTmdbApiKey(key: String?) {
        update { copy(tmdbApiKey = key) }
    }

    fun setFebboxKey(key: String?) {
        update { copy(febboxKey = key) }
    }

    fun setTidbKey(key: String?) {
        update { copy(tidbKey = key) }
    }

    suspend fun validateTidbKey(key: String): Result<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.success(false)

        val body = JSONObject().apply {
            put("tmdb_id", 0)
            put("type", "movie")
            put("segment", "intro")
            put("start_sec", JSONObject.NULL)
            put("end_sec", 1)
            put("video_duration_ms", 1000)
        }.toString()

        val request = Request.Builder()
            .url("https://api.theintrodb.org/v3/submit")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $key")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201, 202, 400, 404, 409, 422 -> true
                    401, 403 -> false
                    else -> {
                        val errorBody = response.body?.string().orEmpty()
                        val errorMessage = runCatching { JSONObject(errorBody).optString("error") }.getOrNull()
                        throw IllegalStateException(errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Validation request failed (${response.code})")
                    }
                }
            }
        }
    }

    fun setHomeSectionOrder(order: List<String>) {
        update { copy(homeSectionOrder = order) }
    }

    fun setSourceOrder(order: List<String>) {
        update { copy(sourceOrder = order) }
    }

    fun setDefaultSubtitleLanguage(lang: String?) {
        update { copy(defaultSubtitleLanguage = lang) }
    }

    fun setSubtitleColor(color: String) {
        update { copy(subtitleColor = color) }
    }

    fun setSubtitleSize(size: Float) {
        update { copy(subtitleSize = size) }
    }

    fun setSubtitleBackgroundOpacity(opacity: Float) {
        update { copy(subtitleBackgroundOpacity = opacity) }
    }

    fun setSubtitleBackgroundBlur(blur: Float) {
        update { copy(subtitleBackgroundBlur = blur) }
    }

    fun setSubtitleBackgroundBlurEnabled(enabled: Boolean) {
        update { copy(subtitleBackgroundBlurEnabled = enabled) }
    }

    fun setSubtitleBold(bold: Boolean) {
        update { copy(subtitleBold = bold) }
    }

    fun setSubtitleVerticalPosition(pos: Float) {
        update { copy(subtitleVerticalPosition = pos) }
    }

    fun setSubtitleFontStyle(style: String) {
        update { copy(subtitleFontStyle = style) }
    }

    fun setSubtitleBorderThickness(thickness: Float) {
        update { copy(subtitleBorderThickness = thickness) }
    }

    fun setSubtitleLineHeight(height: Float) {
        update { copy(subtitleLineHeight = height) }
    }

    fun setSubtitleFont(font: String) {
        update { copy(subtitleFont = font) }
    }

    fun resetSubtitleStyling() {
        update { copy(
            subtitleColor = "#ffffff",
            subtitleSize = 1f,
            subtitleBackgroundOpacity = 0.5f,
            subtitleBackgroundBlur = 0.5f,
            subtitleBackgroundBlurEnabled = true,
            subtitleBold = false,
            subtitleVerticalPosition = 1f,
            subtitleFontStyle = "default",
            subtitleBorderThickness = 1f,
            subtitleLineHeight = 1.5f,
            subtitleFont = "sans-serif",
        ) }
    }

    fun applySubtitlePreset(presetName: String) {
        // Before overwriting with a preset, snapshot current settings into the
        // custom slot so the user can restore their tweaks via "Custom".
        _customSubtitleSlot.value = settings.value
        update {
            when (presetName) {
                "netflix" -> copy(
                    subtitleColor = "#ffffff",
                    subtitleBackgroundOpacity = 0.0f,
                    subtitleBackgroundBlurEnabled = false,
                    subtitleBold = false,
                    subtitleFontStyle = "dropShadow",
                    subtitleLineHeight = 1.4f,
                    subtitleSize = 1.0f,
                    subtitleFont = "sans-serif-condensed"
                )
                "youtube" -> copy(
                    subtitleColor = "#ffffff",
                    subtitleBackgroundOpacity = 0.4f,
                    subtitleBackgroundBlurEnabled = false,
                    subtitleBold = true,
                    subtitleFontStyle = "default",
                    subtitleLineHeight = 1.3f,
                    subtitleSize = 1.0f,
                    subtitleFont = "sans-serif"
                )
                "anime" -> copy(
                    subtitleColor = "#e2e535",
                    subtitleBackgroundOpacity = 0.0f,
                    subtitleBackgroundBlurEnabled = false,
                    subtitleBold = true,
                    subtitleFontStyle = "Border",
                    subtitleBorderThickness = 3.0f,
                    subtitleLineHeight = 1.3f,
                    subtitleSize = 1.0f,
                    subtitleFont = "monospace"
                )
                else -> this
            }
        }
    }

    /** Restores the last hand-crafted subtitle settings saved before a preset was applied. */
    fun restoreCustomSubtitleSlot() {
        val slot = _customSubtitleSlot.value ?: return
        update { slot }
    }

    fun saveToRemote() {
        viewModelScope.launch {
            val session = accountRepo.currentSession ?: return@launch
            val entity = settings.value
            try {
                api.updateSettings(session.userId, session.bearer(), entity.toResponse())
                savedEntity = entity
                _dirty.value = false
            } catch (_: Exception) { }
        }
    }

    fun resetToLocal() {
        viewModelScope.launch {
            settingsPrefs.updateSettings(savedEntity, syncToRemote = false)
            _dirty.value = false
        }
    }

    suspend fun exportDataJson(): String {
        val settings = settings.value
        val progress = progressRepo.observeAllProgress().first()
        val bookmarks = bookmarkRepo.observeAllBookmarks().first()
        
        // Construct the export map. Gson will automatically omit null fields 
        // like tmdbApiKey or febboxKey if they haven't been set by the user.
        val exportMap = mapOf(
            "settings" to settings,
            "progress" to progress,
            "bookmarks" to bookmarks,
            "exportDate" to System.currentTimeMillis(),
            "version" to 1
        )
        
        return Gson().toJson(exportMap)
    }

    private fun SettingsEntity.toResponse() = com.zstream.android.data.remote.SettingsResponse(
        applicationTheme = applicationTheme,
        customTheme = customTheme?.toResponse(),
        applicationLanguage = applicationLanguage,
        defaultSubtitleLanguage = defaultSubtitleLanguage,
        enableThumbnails = enableThumbnails,
        enableAutoplay = enableAutoplay,
        enableSkipCredits = enableSkipCredits,
        enableDiscover = enableDiscover,
        enableFeatured = enableFeatured,
        enableDetailsModal = enableDetailsModal,
        enableImageLogos = enableImageLogos,
        enableCarouselView = enableCarouselView,
        enableMinimalCards = enableMinimalCards,
        forceCompactEpisodeView = forceCompactEpisodeView,
        enableLowPerformanceMode = enableLowPerformanceMode,
        enableNativeSubtitles = enableNativeSubtitles,
        enablePauseOverlay = enablePauseOverlay,
        enableHoldToBoost = enableHoldToBoost,
        manualSourceSelection = manualSourceSelection,
        enableDoubleClickToSeek = enableDoubleClickToSeek,
        enableAutoResumeOnPlaybackError = enableAutoResumeOnPlaybackError,
        enableSourceOrder = enableSourceOrder,
        enableEmbedOrder = enableEmbedOrder,
        proxyTmdb = proxyTmdb,
        sourceOrder = sourceOrder,
        homeSectionOrder = homeSectionOrder,
    )

    private fun CustomThemeSettings.toResponse() = CustomThemeSettingsResponse(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        activeTheme = activeTheme?.let {
            ThemeTripletResponse(
                primary = it.primary,
                secondary = it.secondary,
                tertiary = it.tertiary,
            )
        },
        savedCustomThemes = savedCustomThemes.map { it.toResponse() },
        hiddenDefaultThemes = hiddenDefaultThemes,
    )

    private fun SavedCustomTheme.toResponse() = SavedCustomThemeResponse(
        id = id,
        name = name,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        customPrimaryHex = customPrimaryHex,
        customSecondaryHex = customSecondaryHex,
        customTertiaryHex = customTertiaryHex,
    )
}

private fun AccountSession.bearer() = "Bearer $token"
