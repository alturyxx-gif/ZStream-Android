package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WatchPartyManager"

sealed class WatchPartyAction {
    data class Seek(val timeMs: Long) : WatchPartyAction()
    object Play : WatchPartyAction()
    object Pause : WatchPartyAction()
    data class Navigate(val tmdbId: String, val seasonId: String?, val episodeId: String?) : WatchPartyAction()
    data class Error(val message: String) : WatchPartyAction()
}

data class Participant(
    val userId: String,
    val isHost: Boolean,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val time: Double,
    val duration: Double,
    val lastUpdate: Long,
    val nickname: String = ""
)

@Singleton
class WatchPartyManager @Inject constructor(
    private val repository: WatchPartyRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null

    // State
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    private val _actions = MutableSharedFlow<WatchPartyAction>(replay = 0)
    val actions: SharedFlow<WatchPartyAction> = _actions.asSharedFlow()

    private val _contentMismatch = MutableStateFlow(false)
    val contentMismatch: StateFlow<Boolean> = _contentMismatch.asStateFlow()

    // Local Player Data
    private var lastLocalState: WatchPartyPlayerDto? = null
    private var lastLocalContent: WatchPartyContentDto? = null

    fun hostRoom() {
        val code = generateRoomCode()
        _roomCode.value = code
        _isHost.value = true
        _enabled.value = true
        startPolling()
    }

    fun joinRoom(code: String) {
        _roomCode.value = code
        _isHost.value = false
        _enabled.value = true
        startPolling()
    }

    fun leaveRoom() {
        _enabled.value = false
        _roomCode.value = null
        _isHost.value = false
        _participants.value = emptyList()
        stopPolling()
    }

    /**
     * Called by PlayerViewModel to update the manager with local player state
     */
    fun updateLocalState(content: WatchPartyContentDto, player: WatchPartyPlayerDto) {
        lastLocalContent = content
        lastLocalState = player
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = scope.launch {
            while (isActive && _enabled.value) {
                val code = _roomCode.value ?: break
                reportStatus(code)
                fetchRoomStatus(code)
                delay(if (_isHost.value) 500L else 1000L)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun reportStatus(code: String) {
        val userId = repository.getUserId() ?: "guest"
        val content = lastLocalContent ?: return
        val player = lastLocalState ?: return

        val request = WatchPartyStatusRequest(
            userId = userId,
            roomCode = code,
            isHost = _isHost.value,
            content = content,
            player = player
        )

        repository.sendStatus(request).onFailure {
            Log.e(TAG, "Failed to report status", it)
        }
    }

    private suspend fun fetchRoomStatus(code: String) {
        repository.getRoomStatuses(code).onSuccess { response ->
            val users = response.users.values.flatten()
                .map { dto ->
                    Participant(
                        userId = dto.userId,
                        isHost = dto.isHost,
                        isPlaying = dto.player.isPlaying,
                        isPaused = dto.player.isPaused,
                        time = dto.player.time,
                        duration = dto.player.duration,
                        lastUpdate = dto.timestamp
                    )
                }
                .sortedWith(compareByDescending<Participant> { it.isHost }.thenByDescending { it.lastUpdate })

            _participants.value = users

            if (!_isHost.value) {
                handleGuestSync(users)
            }
        }.onFailure {
            Log.e(TAG, "Failed to fetch room status", it)
        }
    }

    private suspend fun handleGuestSync(users: List<Participant>) {
        val host = users.find { it.isHost } ?: return
        val local = lastLocalState ?: return
        val localContent = lastLocalContent ?: return

        // 1. Validate Content Mismatch
        // Simplified for now, just checking existence
        
        // Predict host time
        val timeSinceUpdate = (System.currentTimeMillis() - host.lastUpdate) / 1000.0
        val predictedHostTime = if (host.isPlaying && !host.isPaused) {
            host.time + timeSinceUpdate
        } else {
            host.time
        }

        // Play/Pause
        val hostIsPaused = host.isPaused || !host.isPlaying
        if (hostIsPaused != local.isPaused) {
            if (hostIsPaused) _actions.emit(WatchPartyAction.Pause)
            else _actions.emit(WatchPartyAction.Play)
        }
        // Drift
        val drift = Math.abs(local.time - predictedHostTime)
        if (drift > 2.0) {
            _actions.emit(WatchPartyAction.Seek((predictedHostTime * 1000).toLong()))
        }
    }

    private fun generateRoomCode(): String {
        return (1000..9999).random().toString()
    }
}
