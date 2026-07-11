package com.zstream.android.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.*
import com.zstream.android.data.local.preferences.SettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Error(val message: String) : AuthState
    data class Success(val session: AccountSession) : AuthState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repo: AccountRepository,
    private val syncManager: DataSyncManager,
    private val progressRepo: ProgressRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val settingsPrefs: SettingsPreferences,
) : ViewModel() {

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)

    val session: StateFlow<AccountSession?> = repo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** TV-only: cached logins available for a fast profile switch. */
    val savedProfiles: StateFlow<List<SavedProfile>> = repo.savedProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe bookmarks and progress from persistent local storage (Room)
    val bookmarks = bookmarkRepo.observeAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val progress = progressRepo.observeAllProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastSyncedSettingsJson: String? = null

    init {
        // Auto-fetch sync data whenever session is available
        viewModelScope.launch {
            session.first { it != null }
            syncManager.syncAllFromRemote()
            // Snapshot current syncable fields so the watcher won't push them back
            lastSyncedSettingsJson = settingsPrefs.settings.first().toSyncableJsonString()
            // Start watching local settings changes to push to remote
            launchSettingsSync()
        }
    }

    /**
     * Watch local settings changes and push to backend after a 1-second debounce.
     * Only reacts when backend-syncable fields change (not subtitle styling, which is local-only).
     * Mirrors p-stream's SettingsSyncer pattern.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun launchSettingsSync() {
        settingsPrefs.settings
            .map { it.toSyncableJsonString() to it }
            .distinctUntilChanged { old, new -> old.first == new.first }
            .debounce(1000)
            .onEach { (syncableJson, settings) ->
                // Skip push if the change was from syncAllFromRemote (not user-initiated)
                if (syncableJson == lastSyncedSettingsJson) return@onEach
                Log.d("AccountVM", "Settings changed locally, syncing to remote")
                runCatching {
                    syncManager.syncSettingsToRemote()
                    lastSyncedSettingsJson = settings.toSyncableJsonString()
                }.onFailure { e ->
                    Log.e("AccountVM", "Failed to sync settings to remote", e)
                }
            }
            .launchIn(viewModelScope)
    }

    fun login(passphrase: String, device: String = "Android") = launch {
        repo.login(passphrase, device)
    }

    fun register(passphrase: String, device: String = "Android") = launch {
        repo.register(passphrase, device)
    }

    fun loginWithPasskey(device: String = "Android") = launch {
        repo.loginWithPasskey(device)
    }

    fun registerWithPasskey(userName: String, device: String = "Android") = launch {
        repo.registerWithPasskey(userName, device)
    }

    fun logout() {
        viewModelScope.launch {
            // First sync current settings to cloud so they aren't lost
            try {
                syncManager.syncSettingsToRemote()
            } catch (e: Exception) {
                Log.e("AccountVM", "Failed to sync settings before logout", e)
            }

            // Clear user-specific data, but keep app settings and anonymous playback state.
            progressRepo.clearProgress()
            bookmarkRepo.clearBookmarks()
            repo.logout()
            authState.value = AuthState.Idle
        }
    }

    fun clearError() { if (authState.value is AuthState.Error) authState.value = AuthState.Idle }

    /** TV-only: switches to an already-saved login without re-authenticating, swapping local progress/bookmarks for the new profile's synced data. */
    fun switchProfile(id: String) {
        viewModelScope.launch {
            val target = repo.savedProfilesSnapshot().find { it.id == id } ?: return@launch
            if (target.userId == session.value?.userId) return@launch
            repo.activateProfile(target)
            clearLocalAndResync()
        }
    }

    /** TV-only: forgets a saved login. If it was the active one, also signs out of it locally. */
    fun removeProfile(id: String) {
        viewModelScope.launch {
            val wasActive = repo.savedProfilesSnapshot().find { it.id == id }?.userId == session.value?.userId
            repo.removeProfile(id)
            if (wasActive) {
                progressRepo.clearProgress()
                bookmarkRepo.clearBookmarks()
                repo.logout()
                authState.value = AuthState.Idle
            }
        }
    }

    /** TV-only: called after logging into a new profile (e.g. via "Add Profile") to swap out local data for the new account's. */
    fun onProfileActivated() {
        viewModelScope.launch { clearLocalAndResync() }
    }

    private suspend fun clearLocalAndResync() {
        progressRepo.clearProgress()
        bookmarkRepo.clearBookmarks()
        runCatching { syncManager.syncAllFromRemote() }
    }

    /** Called from PlayerScreen every 3s and on exit — mirrors p-stream ProgressSaver */
    suspend fun syncProgress(
        s: AccountSession?,
        tmdbId: String,
        watchedSec: Long,
        durationSec: Long,
        title: String,
        year: Int,
        mediaType: String,
        seasonId: String?,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        poster: String?,
    ) {
        val type = if (mediaType == "tv") "show" else "movie"
        
        // Always write local progress so anonymous playback still feeds Continue Watching.
        progressRepo.updateProgress(
            tmdbId = tmdbId,
            title = title,
            type = type,
            watched = watchedSec.toInt(),
            duration = durationSec.toInt(),
            year = year,
            posterPath = poster,
            episodeId = episodeId,
            seasonId = seasonId,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber
        )

        if (s == null) return
    }

    private fun launch(block: suspend () -> AccountSession) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            runCatching { block() }
                .onSuccess { authState.value = AuthState.Success(it) }
                .onFailure { authState.value = AuthState.Error(it.message ?: "Unknown error") }
        }
    }
}
