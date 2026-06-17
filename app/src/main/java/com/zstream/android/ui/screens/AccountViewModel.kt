package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)

    val session: StateFlow<AccountSession?> = repo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Observe bookmarks and progress from persistent local storage (Room)
    val bookmarks = bookmarkRepo.observeAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val progress = progressRepo.observeAllProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Auto-fetch sync data whenever session is available
        viewModelScope.launch {
            session.collect { s -> if (s != null) syncManager.syncAllFromRemote() }
        }
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
            syncManager.clearAllLocalData()
            repo.logout()
            authState.value = AuthState.Idle
        }
    }

    fun clearError() { if (authState.value is AuthState.Error) authState.value = AuthState.Idle }

    /** Called from PlayerScreen every 3s and on exit — mirrors p-stream ProgressSaver */
    suspend fun syncProgress(
        s: AccountSession,
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
        
        // Use the repository which handles both local update and remote sync
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
