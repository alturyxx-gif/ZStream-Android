package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.AccountRepository
import com.zstream.android.data.AccountSession
import com.zstream.android.data.remote.BookmarkResponse
import com.zstream.android.data.remote.ProgressInput
import com.zstream.android.data.remote.ProgressMeta
import com.zstream.android.data.remote.ProgressResponse
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
class AccountViewModel @Inject constructor(private val repo: AccountRepository) : ViewModel() {

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)

    val session: StateFlow<AccountSession?> = repo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bookmarks = MutableStateFlow<List<BookmarkResponse>>(emptyList())
    val progress  = MutableStateFlow<List<ProgressResponse>>(emptyList())

    init {
        // Auto-fetch sync data whenever session is available
        viewModelScope.launch {
            session.collect { s -> if (s != null) fetchSyncData(s) }
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
            repo.logout()
            authState.value = AuthState.Idle
            bookmarks.value = emptyList()
            progress.value  = emptyList()
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
        // Find best existing record: same show+episode number, pick highest watched (most authoritative)
        val existing = progress.value
            .filter { p ->
                p.tmdbId == tmdbId &&
                (episodeNumber == null || p.episode.number == episodeNumber) &&
                (seasonNumber == null || p.season.number == seasonNumber)
            }
            .maxByOrNull { it.watched.toLongOrNull() ?: 0L }
        // Always reuse the existing episodeId/seasonId to avoid creating duplicate records
        val realSeasonId = existing?.season?.id ?: seasonId
        val realEpisodeId = existing?.episode?.id ?: episodeId
        val input = ProgressInput(
            tmdbId = tmdbId,
            watched = watchedSec,
            duration = durationSec,
            meta = ProgressMeta(title, year, poster, type),
            seasonId = realSeasonId,
            episodeId = realEpisodeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
        repo.setProgress(s, input)
    }

    private fun fetchSyncData(s: AccountSession) {
        viewModelScope.launch {
            runCatching { bookmarks.value = repo.getBookmarks(s) }
            runCatching {
                val raw = repo.getProgress(s)
                // Deduplicate: keep highest-watched entry per tmdbId+episode+season
                progress.value = raw
                    .sortedByDescending { it.watched.toLongOrNull() ?: 0L }
                    .distinctBy { "${it.tmdbId}:${it.episode.number}:${it.season.number}" }
            }
        }
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
