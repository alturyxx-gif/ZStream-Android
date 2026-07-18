package com.zstream.android.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.AuroraKeyInfo
import com.zstream.android.data.AuroraKeyManager
import com.zstream.android.data.BackendConfig
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.ProgressRepository
import com.zstream.android.data.TraktRepository
import com.zstream.android.data.local.entity.CustomThemeSettings
import com.zstream.android.data.local.entity.SavedCustomTheme
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.entity.ThemeColors
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.remote.CustomThemeSettingsResponse
import com.zstream.android.data.remote.SavedCustomThemeResponse
import com.zstream.android.data.remote.ThemeTripletResponse
import com.zstream.android.data.adb.ReleaseCheckInterval
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.PluginState
import com.zstream.android.plugin.SourceInfo
import com.zstream.android.plugin.SourceOrderStore
import com.zstream.android.plugin.pluginVersionLabel
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

internal fun isAcceptedWyzieStatus(code: Int): Boolean? = when (code) {
    200, 402, 429 -> true
    401, 403 -> false
    else -> null
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPreferences,
    private val progressRepo: ProgressRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val traktRepo: TraktRepository,
    private val httpClient: OkHttpClient,
    private val releaseUpdateManager: ReleaseUpdateManager,
    private val pluginManager: PluginManager,
    private val sourceOrderStore: SourceOrderStore,
    private val accountRepo: com.zstream.android.data.AccountRepository,
    private val auroraKeyManager: AuroraKeyManager,
    private val backendConfig: BackendConfig,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    val traktState = traktRepo.state
    val releaseChecksEnabled = releaseUpdateManager.enabled
    val releaseCheckInterval = releaseUpdateManager.interval

    // Custom account/sync backend URL — only affects login/progress/bookmarks/settings sync.
    // TMDB, IMDb, the plugin manifest CDN, and Artemis are unaffected (see BackendConfig).
    private val _backendUrl = MutableStateFlow(backendConfig.baseUrl)
    val backendUrl: StateFlow<String> = _backendUrl
    val isCustomBackend: Boolean get() = backendConfig.isCustom

    /** Returns null on success, or an error message if [url] isn't a valid http(s) URL. */
    fun setBackendUrl(url: String): String? {
        if (!backendConfig.setCustomUrl(url)) return "Enter a valid URL (e.g. https://example.com/)"
        _backendUrl.value = backendConfig.baseUrl
        return null
    }

    fun resetBackendUrl() {
        backendConfig.reset()
        _backendUrl.value = backendConfig.baseUrl
    }

    // Plugin state — exposed directly from PluginManager
    val pluginState = pluginManager.pluginState
    val pluginUpdateError = pluginManager.pluginUpdateError

    private val _sourceOrder = MutableStateFlow<List<SourceInfo>>(emptyList())
    val sourceOrder: StateFlow<List<SourceInfo>> = _sourceOrder

    private val _downloadSourceOrder = MutableStateFlow<List<SourceInfo>>(emptyList())
    val downloadSourceOrder: StateFlow<List<SourceInfo>> = _downloadSourceOrder

    init {
        viewModelScope.launch {
            combine(pluginState, settingsPrefs.settings) { _, current -> current }.collect { current ->
                _sourceOrder.value = sourceOrderStore.getOrderedSources(
                    hasAuroraKey = !current.febboxKey.isNullOrBlank(),
                )
                refreshDownloadSourceOrder()
            }
        }
    }

    private fun refreshSourceOrder() {
        viewModelScope.launch {
            val current = settingsPrefs.settings.first()
            _sourceOrder.value = sourceOrderStore.getOrderedSources(
                hasAuroraKey = !current.febboxKey.isNullOrBlank(),
            )
        }
    }

    fun reorderSources(newOrder: List<SourceInfo>) {
        _sourceOrder.value = newOrder
        viewModelScope.launch {
            sourceOrderStore.saveOrder(newOrder.map { it.id })
        }
    }

    /** Clears the manual source order and recomputes the plugin's default priority order. */
    fun resetSourceOrder() {
        viewModelScope.launch {
            sourceOrderStore.clearOrder()
            refreshSourceOrder()
        }
    }

    private fun refreshDownloadSourceOrder() {
        viewModelScope.launch {
            _downloadSourceOrder.value = sourceOrderStore.getDownloadOrder()
        }
    }

    fun reorderDownloadSources(newOrder: List<SourceInfo>) {
        _downloadSourceOrder.value = newOrder
        viewModelScope.launch {
            sourceOrderStore.saveDownloadOrder(newOrder.map { it.id })
        }
    }

    /** Clears the manual download order and recomputes the plugin's default download priority order. */
    fun resetDownloadSourceOrder() {
        viewModelScope.launch {
            sourceOrderStore.clearDownloadOrder()
            refreshDownloadSourceOrder()
        }
    }

    private val _pluginUpdateMessage = MutableStateFlow<String?>(null)
    val pluginUpdateMessage = _pluginUpdateMessage.asStateFlow()

    fun checkPluginUpdate() {
        viewModelScope.launch {
            _pluginUpdateMessage.value = null
            runCatching {
                val stagedVersion = pluginManager.checkForUpdate()
                _pluginUpdateMessage.value = if (stagedVersion != null) {
                    val display = pluginManager.stagedDisplayVersion() ?: stagedVersion.toString()
                    "Plugin ${pluginVersionLabel(display)} will be applied on next launch."
                } else {
                    val current = pluginManager.pluginDisplayVersion()
                    "Plugin is up to date${if (current != null) " (${pluginVersionLabel(current)})" else ""}."
                }
            }.onFailure {
                _pluginUpdateMessage.value = "Update check failed: ${it.message}"
            }
        }
    }

    fun setReleaseChecksEnabled(enabled: Boolean) = releaseUpdateManager.setEnabled(enabled)
    fun setReleaseCheckInterval(label: String) = releaseUpdateManager.setInterval(ReleaseCheckInterval.fromLabel(label))

    private val _appUpdateMessage = MutableStateFlow<String?>(null)
    val appUpdateMessage = _appUpdateMessage.asStateFlow()

    fun checkAppUpdate() {
        viewModelScope.launch {
            _appUpdateMessage.value = null
            runCatching {
                val repositoryUrl = releaseUpdateManager.repositoryUrl
                val apks = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.zstream.android.data.adb.GithubReleaseCatalog().loadAllApks(repositoryUrl)
                }
                if (releaseUpdateManager.checkForUpdate(apks)) {
                    _appUpdateMessage.value = "Update ${releaseUpdateManager.pendingVersion} is available."
                    com.zstream.android.data.adb.ReleaseUpdateNavigation.dispatch(false)
                } else {
                    _appUpdateMessage.value = "App is up to date (${com.zstream.android.BuildConfig.VERSION_NAME})."
                }
            }.onFailure {
                _appUpdateMessage.value = "Update check failed: ${it.message}"
            }
        }
    }

    /** Debug-only: shows the update dialogs without a real pending release/plugin build. */
    fun simulateAppUpdate() = releaseUpdateManager.simulateUpdate()
    fun simulatePluginUpdate() = pluginManager.simulateUpdate()

    fun connectTrakt(context: android.content.Context) {
        viewModelScope.launch {
            runCatching { traktRepo.beginDeviceAuthorization() }
                .onSuccess {
                    traktRepo.state.value.activationCode?.let { code ->
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Trakt activation code", code))
                    }
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it)))
                }
        }
    }

    fun disconnectTrakt() { viewModelScope.launch { traktRepo.disconnect() } }
    fun syncTrakt() { viewModelScope.launch { traktRepo.syncWatchlist(); traktRepo.syncHistory() } }
    private val _customSubtitleSlot = MutableStateFlow<SettingsEntity?>(null)

    val settings: StateFlow<SettingsEntity> = settingsPrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab

    fun setTab(index: Int) { _currentTab.value = index }

    private fun update(transform: SettingsEntity.() -> SettingsEntity) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.transform()
            settingsPrefs.updateSettings(updated, syncToRemote = false)
        }
    }

    fun setApplicationLanguage(lang: String) {
        update { copy(applicationLanguage = lang) }
    }

    fun setApplicationTheme(theme: String) {
        update { copy(applicationTheme = theme) }
    }

    fun setApplicationFont(font: String) {
        update { copy(applicationFont = font) }
    }

    fun setKidsModeEnabled(v: Boolean) {
        update { copy(kidsModeEnabled = v) }
        // Kids Mode is scoped per-profile on TV; keep the saved profile's flag in sync so the
        // picker can show which profiles are kids profiles and switching restores the right value.
        viewModelScope.launch {
            accountRepo.currentSession?.userId?.let { accountRepo.setProfileKidsMode(it, v) }
        }
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

    fun setEnableImageLogos(v: Boolean) {
        update { copy(enableImageLogos = v) }
    }

    fun setEnableCarouselView(v: Boolean) {
        update { copy(enableCarouselView = v) }
    }

    fun setGridRows(v: Int) {
        update { copy(gridRows = v.coerceIn(1, 8)) }
    }

    fun setAutoPipEnabled(v: Boolean) {
        update { copy(autoPipEnabled = v) }
    }

    fun setEnableBackgroundPlaybackOnScreenLock(v: Boolean) {
        update { copy(enableBackgroundPlaybackOnScreenLock = v) }
    }

    fun setTrailersOpenInApp(v: Boolean) {
        update { copy(trailersOpenInApp = v) }
    }

    fun setEnableMinimalCards(v: Boolean) {
        update { copy(enableMinimalCards = v) }
    }

    fun setHomeSectionCarouselLimit(v: Int) {
        update { copy(homeSectionCarouselLimit = v.coerceIn(1, 50)) }
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

    fun setEnableSideGestures(v: Boolean) {
        update { copy(enableSideGestures = v) }
    }

    fun setEnableHoldToBoost(v: Boolean) {
        update { copy(enableHoldToBoost = v) }
    }

    fun setManualSourceSelection(v: Boolean) {
        update { copy(manualSourceSelection = v) }
    }

    fun setAllowParallelDownload(v: Boolean) {
        update { copy(allowParallelDownload = v) }
    }

    fun setEnableDoubleClickToSeek(v: Boolean) {
        update { copy(enableDoubleClickToSeek = v) }
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        update { copy(doubleTapSeekSeconds = seconds) }
    }

    fun setEnableAutoResumeOnPlaybackError(v: Boolean) {
        update { copy(enableAutoResumeOnPlaybackError = v) }
    }

    fun setEnableNativeKeyboard(v: Boolean) {
        update { copy(enableNativeKeyboard = v) }
    }

    fun setEnableSourceOrder(v: Boolean) {
        update { copy(enableSourceOrder = v) }
    }

    fun setTmdbApiKey(key: String?) {
        update { copy(tmdbApiKey = key) }
    }

    fun setFebboxKey(key: String?) {
        update { copy(febboxKey = key) }
    }

    /** Replaces the whole Aurora key list (add/remove/edit rows all go through this). */
    fun setFebboxKeys(keys: List<String>) {
        update { copy(febboxKeys = keys) }
    }

    fun addFebboxKey() {
        update { copy(febboxKeys = febboxKeys + "") }
    }

    fun updateFebboxKeyAt(index: Int, value: String) {
        update {
            val keys = febboxKeys.toMutableList()
            if (index !in keys.indices) return@update this
            keys[index] = value
            copy(febboxKeys = keys)
        }
    }

    fun removeFebboxKeyAt(index: Int) {
        update {
            val keys = febboxKeys.toMutableList()
            if (index !in keys.indices) return@update this
            val removed = keys.removeAt(index)
            val nextActive = if (febboxKey == removed) keys.firstOrNull { it.isNotBlank() } else febboxKey
            copy(febboxKeys = keys, febboxKey = nextActive)
        }
    }

    /** Validates a single Aurora key and reports its status + remaining daily bandwidth. */
    suspend fun checkAuroraKey(key: String): AuroraKeyInfo = auroraKeyManager.checkKey(key)

    /**
     * Re-picks the active Aurora key from the configured list, rotating away from any key
     * that's invalid or out of bandwidth. Called after the key list changes and before every
     * scrape/download attempt (see PlayerViewModel/DownloadResolver).
     */
    suspend fun ensureActiveAuroraKey(): String? = auroraKeyManager.ensureActiveKey()

    fun setTidbKey(key: String?) {
        update { copy(tidbKey = key) }
    }

    fun setWyzieKey(key: String?) {
        update { copy(wyzieKey = key?.trim()?.takeIf(String::isNotEmpty)) }
    }

    suspend fun validateTmdbKey(key: String): Result<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.success(false)

        // If it looks like a v4 token (JWT), we should ideally use Bearer auth,
        // but the app currently uses api_key query param for everything.
        // TMDB v3 API key is 32 chars. v4 is a long JWT.
        val isV4 = key.length > 32 && key.contains(".")

        val url = "https://api.themoviedb.org/3/authentication"
        val requestBuilder = Request.Builder().url(url)
        
        if (isV4) {
            requestBuilder.header("Authorization", "Bearer $key")
        } else {
            val fullUrl = Request.Builder().url(url).build().url.newBuilder()
                .addQueryParameter("api_key", key)
                .build()
            requestBuilder.url(fullUrl)
        }

        runCatching {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(true)
                    401 -> Result.success(false)
                    else -> {
                        val errorBody = response.body?.string().orEmpty()
                        val errorMessage = runCatching { JSONObject(errorBody).optString("status_message") }.getOrNull()
                        throw IllegalStateException(errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Validation request failed (${response.code})")
                    }
                }
            }
        }.getOrElse { Result.failure(it) }
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

    suspend fun validateWyzieKey(key: String): Result<Boolean> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.success(false)
        val url = "https://sub.wyzie.io/search".toHttpUrl().newBuilder()
            .addQueryParameter("id", "286217")
            .addQueryParameter("language", "en")
            .addQueryParameter("key", key.trim())
            .build()

        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                isAcceptedWyzieStatus(response.code)
                    ?: throw IllegalStateException("Validation request failed (${response.code})")
            }
        }
    }

    fun setHomeSectionOrder(order: List<String>) {
        update { copy(homeSectionOrder = order) }
    }

    fun setGroupOrder(order: List<String>) {
        update { copy(groupOrder = order) }
    }

    fun renameGroup(oldGroup: String, newGroup: String) {
        viewModelScope.launch {
            bookmarkRepo.renameGroup(oldGroup, newGroup)
        }
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
        update { copy(subtitleVerticalPosition = pos.coerceIn(-15f, 30f)) }
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
            subtitleBackgroundOpacity = 0f,
            subtitleBackgroundBlur = 0f,
            subtitleBackgroundBlurEnabled = false,
            subtitleBold = false,
            subtitleVerticalPosition = 0f,
            subtitleFontStyle = "dropShadow",
            subtitleBorderThickness = 1f,
            subtitleLineHeight = 1.2f,
            subtitleFont = "sans-serif-condensed",
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
                    subtitleBackgroundBlur = 0.0f,
                    subtitleBackgroundBlurEnabled = false,
                    subtitleBold = false,
                    subtitleVerticalPosition = 0.0f,
                    subtitleFontStyle = "dropShadow",
                    subtitleLineHeight = 1.2f,
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

    suspend fun exportDataJson(): String {
        val settings = settings.value
        val progress = progressRepo.observeAllProgress().first()
        val bookmarks = bookmarkRepo.observeAllBookmarks().first()

        // The user's manually-saved source order lives in SourceOrderStore's own DataStore
        // (plugin_prefs), not in SettingsEntity — settings.sourceOrder is a legacy backend-sync
        // field nothing actually writes anymore. Pull the real saved order in separately so a
        // backup captures it too.
        val sourceOrder = sourceOrderStore.getSavedOrderIds()
        val downloadSourceOrder = sourceOrderStore.getSavedDownloadOrderIds()

        // Trakt access/refresh tokens live in TraktRepository's own DataStore, not SettingsEntity.
        val traktSession = traktRepo.exportSession()

        // Paired TV connection info (host/model/ports) lives in TvAdbManager's SharedPreferences,
        // not SettingsEntity.
        val pairedTvs = com.zstream.android.data.adb.TvAdbManager.get(appContext).getSavedTvs()

        // Construct the export map. Gson will automatically omit null fields
        // like tmdbApiKey or febboxKey if they haven't been set by the user.
        // Note: API keys (TMDB, febbox/Aurora, debrid, tidb, wyzie) are already
        // inside `settings` — SettingsEntity is dumped whole, not field-by-field.
        val exportMap = mapOf(
            "settings" to settings,
            "progress" to progress,
            "bookmarks" to bookmarks,
            "sourceOrder" to sourceOrder,
            "downloadSourceOrder" to downloadSourceOrder,
            "traktSession" to traktSession,
            "pairedTvs" to pairedTvs,
            "exportDate" to System.currentTimeMillis(),
            "version" to 3
        )

        return Gson().toJson(exportMap)
    }

    /**
     * TV has no file picker UI worth using (Storage Access Framework's "create document" flow
     * is built for touch + a file browser, neither of which exist on a remote-control-only
     * device), so TV writes straight to the public Downloads folder instead of prompting for a
     * location. Returns the display file name that was written, for the caller to show in a
     * Toast.
     */
    suspend fun exportToDownloads(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val json = exportDataJson()
        val fileName = "zstream_backup_${System.currentTimeMillis()}.json"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: throw IllegalStateException("Could not open output stream for Downloads file")
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, fileName).writeText(json)
        }
        fileName
    }

    /**
     * Imports bookmarks and watch progress from an external app's export JSON
     * (e.g. MediaWatch's `account/bookmarks/progress/watchHistory` shape) rather
     * than ZStream's own backup format. Skips entries missing a title/type since
     * those can't be reconstructed.
     */
    // JsonObject.getAsJsonObject/getAsJsonArray do a raw, unchecked cast on whatever value is
    // stored for that key, so a differently-shaped field elsewhere in a hand-edited or
    // differently-versioned export (e.g. a null or a scalar where an object is expected) throws
    // a bare ClassCastException. These helpers check the element's actual type first so one
    // oddly-shaped field can't abort the whole import.
    private fun com.google.gson.JsonObject.safeObject(key: String): com.google.gson.JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun com.google.gson.JsonObject.safeArray(key: String): com.google.gson.JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun com.google.gson.JsonElement.safeString(): String? = takeIf { it.isJsonPrimitive }?.asString
    private fun com.google.gson.JsonElement.safeInt(): Int? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    private fun com.google.gson.JsonElement.safeNumberAsInt(): Int? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.toInt()
    private fun com.google.gson.JsonElement.safeLong(): Long? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    // Different export versions store `bookmarks`/`progress` either as a map keyed by tmdbId
    // (MediaWatch's current shape) or as a flat array of entries carrying their own id field.
    // Normalizes both into (tmdbId, entryObject) pairs so the rest of the import doesn't care
    // which shape it got. Returns empty (with a log) if the key is missing or neither shape.
    private fun com.google.gson.JsonObject.entryPairs(key: String): List<Pair<String, com.google.gson.JsonObject>> {
        val value = get(key)
        if (value == null || value.isJsonNull) return emptyList()
        return when {
            value.isJsonObject -> value.asJsonObject.entrySet().mapNotNull { (k, v) ->
                v.takeIf { it.isJsonObject }?.asJsonObject?.let { k to it }
            }
            value.isJsonArray -> value.asJsonArray.mapNotNull { el ->
                val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.get("tmdbId")?.safeString()
                    ?: obj.get("tmdbId")?.safeNumberAsInt()?.toString()
                    ?: obj.get("id")?.safeString()
                    ?: obj.get("id")?.safeNumberAsInt()?.toString()
                id?.let { it to obj }
            }
            else -> {
                Log.w("SettingsViewModel", "Import: '$key' is neither an object nor an array, skipping")
                emptyList()
            }
        }
    }

    suspend fun importDataJson(json: String): ImportSummary = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val root = JsonParser.parseString(json).asJsonObject
        var bookmarksImported = 0
        var progressImported = 0

        // Both the local Room updatedAt and the backend's own sync-receipt timestamp are stamped
        // in call order, not from any value in this file, so entries must be replayed oldest-first
        // by their original `updatedAt` or "most recently watched/bookmarked" ends up scrambled
        // (JSON object keys with integer-like names iterate in numeric-ascending order in Gson,
        // not insertion/recency order, which is what caused the reversed ordering).
        root.entryPairs("bookmarks")
            .sortedBy { (_, obj) -> obj.get("updatedAt")?.safeLong() ?: 0L }
            .forEach { (tmdbId, obj) ->
            runCatching {
                val title = obj.get("title")?.safeString() ?: return@runCatching
                val type = obj.get("type")?.safeString() ?: "movie"
                val year = obj.get("year")?.safeInt()
                // "poster"/"group" match MediaWatch's export shape; "posterPath"/"groups" match
                // ZStream's own BookmarkEntity export shape (exportDataJson dumps entities as-is).
                val poster = obj.get("poster")?.safeString() ?: obj.get("posterPath")?.safeString()
                val groups = (obj.safeArray("group") ?: obj.safeArray("groups"))?.mapNotNull { it.safeString() }
                bookmarkRepo.addBookmark(tmdbId, title, type, year, poster, groups)
                bookmarksImported++
            }.onFailure { e -> Log.w("SettingsViewModel", "Skipping malformed bookmark entry $tmdbId", e) }
        }

        root.entryPairs("progress")
            .sortedBy { (_, obj) -> obj.get("updatedAt")?.safeLong() ?: 0L }
            .forEach { (tmdbId, obj) ->
            runCatching {
                val title = obj.get("title")?.safeString() ?: return@runCatching
                val type = obj.get("type")?.safeString() ?: "movie"
                val year = obj.get("year")?.safeInt()
                val poster = obj.get("poster")?.safeString() ?: obj.get("posterPath")?.safeString()

                // ZStream's own ProgressEntity export shape: watched/duration/episodeId/seasonId
                // sit directly on the entry (one flat row per movie or per episode), unlike
                // MediaWatch's nested `progress`/`episodes`/`seasons` shape handled below.
                val flatWatched = obj.get("watched")?.safeNumberAsInt()
                val flatDuration = obj.get("duration")?.safeNumberAsInt()
                if (flatWatched != null && flatDuration != null) {
                    progressRepo.updateProgress(
                        tmdbId, title, type, flatWatched, flatDuration, year, poster,
                        episodeId = obj.get("episodeId")?.safeString(),
                        seasonId = obj.get("seasonId")?.safeString(),
                        episodeNumber = obj.get("episodeNumber")?.safeInt(),
                        seasonNumber = obj.get("seasonNumber")?.safeInt(),
                    )
                    progressImported++
                    return@runCatching
                }

                obj.safeObject("progress")?.let { p ->
                    val watched = p.get("watched")?.safeNumberAsInt() ?: 0
                    val duration = p.get("duration")?.safeNumberAsInt() ?: 0
                    progressRepo.updateProgress(tmdbId, title, type, watched, duration, year, poster)
                    progressImported++
                }

                val seasons = obj.safeObject("seasons")
                obj.safeObject("episodes")?.entrySet()
                    ?.sortedBy { (_, epElement) -> epElement.asJsonObject.get("updatedAt")?.safeLong() ?: 0L }
                    ?.forEach { (episodeId, epElement) ->
                    runCatching {
                        val ep = epElement.asJsonObject
                        val epProgress = ep.safeObject("progress") ?: return@runCatching
                        val watched = epProgress.get("watched")?.safeNumberAsInt() ?: 0
                        val duration = epProgress.get("duration")?.safeNumberAsInt() ?: 0
                        val seasonId = ep.get("seasonId")?.safeString()
                        val episodeNumber = ep.get("number")?.safeInt()
                        val seasonNumber = seasonId?.let { sid -> seasons?.safeObject(sid)?.get("number")?.safeInt() }
                        progressRepo.updateProgress(
                            tmdbId, title, type, watched, duration, year, poster,
                            episodeId = episodeId, seasonId = seasonId,
                            episodeNumber = episodeNumber, seasonNumber = seasonNumber,
                        )
                        progressImported++
                    }.onFailure { e -> Log.w("SettingsViewModel", "Skipping malformed episode entry $tmdbId/$episodeId", e) }
                }
            }.onFailure { e -> Log.w("SettingsViewModel", "Skipping malformed progress entry $tmdbId", e) }
        }

        ImportSummary(bookmarksImported, progressImported)
    }
}

data class ImportSummary(val bookmarksImported: Int, val progressImported: Int)
