package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.AccountRepository
import com.zstream.android.data.AccountSession
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.remote.BackendApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPreferences,
    private val accountRepo: AccountRepository,
    private val api: BackendApi,
) : ViewModel() {

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty

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

    fun setFebboxKey(key: String?) {
        update { copy(febboxKey = key) }
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

    private fun SettingsEntity.toResponse() = com.zstream.android.data.remote.SettingsResponse(
        applicationTheme = applicationTheme,
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
}

private fun AccountSession.bearer() = "Bearer $token"
