package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WatchPartyManager"

// Configuration constants matching the "New" Pstream Engine
private const val POLL_INTERVAL_MS = 2000L
private const val BACKOFF_MAX_MS = 15000L
private const val STALE_USER_MS = 12000L
private const val DRIFT_THRESHOLD_SECONDS = 5.0
private const val SYNC_COOLDOWN_MS = 3000L
private const val SEEK_SETTLE_MS = 250L
private const val PLAY_SETTLE_MS = 350L

private const val HOST_REPORT_INTERVAL_MS = 1500L
private const val GUEST_REPORT_INTERVAL_MS = 3000L
private const val MIN_CHANGE_INTERVAL_MS = 250L

sealed class WatchPartyAction {
    data class Seek(val timeMs: Long) : WatchPartyAction()
    object Play : WatchPartyAction()
    object Pause : WatchPartyAction()
    data class Navigate(
        val tmdbId: Int,
        val mediaType: String,
        val season: Int?,
        val episode: Int?,
        val title: String,
        val year: Int?,
        val poster: String?
    ) : WatchPartyAction()
}

data class Participant(
    val userId: String,
    val isHost: Boolean,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val time: Double,
    val duration: Double,
    val lastUpdate: Long,
    val tmdbId: String? = null,
    val type: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seasonId: String? = null,
    val episodeId: String? = null
)

@Singleton
class WatchPartyManager @Inject constructor(
    private val repository: WatchPartyRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engineJob: Job? = null
    private var reporterJob: Job? = null

    // --- State Flow ---
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _contentMismatch = MutableStateFlow(false)
    val contentMismatch: StateFlow<Boolean> = _contentMismatch.asStateFlow()

    private val _actions = MutableSharedFlow<WatchPartyAction>(replay = 0)
    val actions: SharedFlow<WatchPartyAction> = _actions.asSharedFlow()

    // --- Engine Internal Refs ---
    private var consecutiveErrors = 0
    private var syncInProgress = false
    private var hasInitialSynced = false
    private var lastHostPlaying: Boolean? = null
    private var pendingHostPlaying: Boolean? = null
    private var confirmedHostPlaying: Boolean? = null
    private var lastSyncAt = 0L
    private var lastFollowKey: String? = null

    // --- Reporter Internal Refs ---
    private var lastReportTime = 0L
    private var lastFingerprint = ""
    private var lastLocalState: WatchPartyPlayerDto? = null
    private var lastLocalContent: WatchPartyContentDto? = null

    /**
     * Start hosting a new watch party.
     */
    fun hostRoom() {
        val chars = ('A'..'Z') + ('0'..'9')
        val code = (1..6).map { chars.random() }.joinToString("")
        _roomCode.value = code
        _isHost.value = true
        _enabled.value = true
        resetEngineState()
        startEngine()
        startReporter()
    }

    /**
     * Update the active room code.
     */
    fun updateRoomCode(code: String) {
        _roomCode.value = code.uppercase()
        // Reset reporter state to force immediate update to new code
        lastFingerprint = ""
        lastReportTime = 0L
    }

    /**
     * Join an existing watch party.
     */
    fun joinRoom(code: String) {
        _roomCode.value = code.uppercase()
        _isHost.value = false
        _enabled.value = true
        resetEngineState()
        startEngine()
        startReporter()
    }

    /**
     * Leave current watch party.
     */
    fun leaveRoom() {
        _enabled.value = false
        _roomCode.value = null
        _isHost.value = false
        _participants.value = emptyList()
        _isOffline.value = false
        _contentMismatch.value = false
        stopEngine()
        stopReporter()
    }

    /**
     * Force a manual sync with the host.
     */
    fun manualSync() {
        if (_isHost.value) return
        val host = _participants.value.find { it.isHost } ?: return

        scope.launch {
            val hostIsPlaying = host.isPlaying && !host.isPaused
            val elapsed = Math.max(0L, System.currentTimeMillis() - host.lastUpdate) / 1000.0
            val predicted = if (hostIsPlaying) host.time + elapsed else host.time

            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()

            _actions.emit(WatchPartyAction.Seek((predicted * 1000).toLong()))
            delay(SEEK_SETTLE_MS)

            if (hostIsPlaying) _actions.emit(WatchPartyAction.Play)
            else _actions.emit(WatchPartyAction.Pause)

            delay(PLAY_SETTLE_MS)

            _isSyncing.value = false
            syncInProgress = false
            hasInitialSynced = true
            lastHostPlaying = hostIsPlaying
        }
    }

    /**
     * Called by PlayerViewModel to update local state for reporting and sync comparison.
     */
    fun updateLocalState(content: WatchPartyContentDto, player: WatchPartyPlayerDto) {
        lastLocalContent = content
        lastLocalState = player
    }

    private fun resetEngineState() {
        consecutiveErrors = 0
        syncInProgress = false
        hasInitialSynced = false
        lastHostPlaying = null
        pendingHostPlaying = null
        confirmedHostPlaying = null
        lastSyncAt = 0L
        lastFollowKey = null
        lastFingerprint = ""
        lastReportTime = 0L
    }

    private fun startEngine() {
        stopEngine()
        engineJob = scope.launch {
            while (isActive && _enabled.value) {
                val code = _roomCode.value ?: break
                
                refreshRoomData(code)

                val interval = if (consecutiveErrors > 0) {
                    Math.min(POLL_INTERVAL_MS * (1 shl consecutiveErrors), BACKOFF_MAX_MS)
                } else {
                    POLL_INTERVAL_MS
                }
                delay(interval)
            }
        }
    }

    private fun stopEngine() {
        engineJob?.cancel()
        engineJob = null
    }

    private fun startReporter() {
        stopReporter()
        reporterJob = scope.launch {
            while (isActive && _enabled.value) {
                val code = _roomCode.value ?: break
                val now = System.currentTimeMillis()
                
                val meta = lastLocalContent
                val status = lastLocalState

                if (meta != null && status != null) {
                    // Guard: Guest must have started playing at least once (mirrors web)
                    if (!_isHost.value && !status.hasPlayedOnce) {
                        delay(MIN_CHANGE_INTERVAL_MS)
                        continue
                    }

                    // Guard: Must have a valid content ID
                    if (meta.tmdbId.isNullOrBlank()) {
                        delay(MIN_CHANGE_INTERVAL_MS)
                        continue
                    }

                    val fingerprint = "${status.isPlaying}:${status.isPaused}:${status.isLoading}:${status.time.toInt()}:${status.playbackRate}"
                    val changed = fingerprint != lastFingerprint
                    
                    val minInterval = if (_isHost.value) HOST_REPORT_INTERVAL_MS else GUEST_REPORT_INTERVAL_MS
                    val dueByTime = now - lastReportTime >= minInterval
                    val dueByChange = changed && (now - lastReportTime >= MIN_CHANGE_INTERVAL_MS)

                    if (dueByChange || dueByTime) {
                        val userId = repository.getUserId() ?: repository.getGuestId()
                        val request = WatchPartyStatusRequest(
                            userId = userId,
                            roomCode = code,
                            isHost = _isHost.value,
                            content = meta,
                            player = status
                        )

                        repository.sendStatus(request).onSuccess {
                            lastReportTime = now
                            lastFingerprint = fingerprint
                        }.onFailure {
                            Log.e(TAG, "Failed to report status", it)
                        }
                    }
                }
                delay(MIN_CHANGE_INTERVAL_MS)
            }
        }
    }

    private fun stopReporter() {
        reporterJob?.cancel()
        reporterJob = null
    }

    private suspend fun refreshRoomData(code: String) {
        repository.getRoomStatuses(code).onSuccess { response ->
            consecutiveErrors = 0
            _isOffline.value = false
            
            val now = System.currentTimeMillis()
            val users = response.users.mapNotNull { (_, statuses) ->
                val latest = statuses.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                if ((now - latest.timestamp) > STALE_USER_MS) return@mapNotNull null
                
                Participant(
                    userId = latest.userId,
                    isHost = latest.isHost,
                    isPlaying = latest.player.isPlaying,
                    isPaused = latest.player.isPaused,
                    time = latest.player.time,
                    duration = latest.player.duration,
                    lastUpdate = latest.timestamp,
                    tmdbId = latest.content.tmdbId,
                    type = when {
                        latest.content.type.equals("TV Show", true) -> "tv"
                        latest.content.type.equals("show", true) -> "tv"
                        else -> "movie"
                    },
                    title = latest.content.title,
                    year = latest.content.year,
                    poster = latest.content.poster,
                    seasonNumber = latest.content.seasonNumber,
                    episodeNumber = latest.content.episodeNumber,
                    seasonId = latest.content.seasonId,
                    episodeId = latest.content.episodeId
                )
            }.sortedWith(compareByDescending<Participant> { it.isHost }.thenByDescending { it.lastUpdate })

            _participants.value = users

            if (!_isHost.value) {
                processGuestEngine(users)
            }
        }.onFailure {
            consecutiveErrors++
            if (consecutiveErrors >= 3) {
                _isOffline.value = true
            }
            Log.e(TAG, "Failed to refresh room data", it)
        }
    }

    private suspend fun processGuestEngine(users: List<Participant>) {
        val host = users.find { it.isHost } ?: return
        val local = lastLocalState ?: return
        val localMeta = lastLocalContent ?: return

        // 1. Check for sync block conditions
        if (syncInProgress) return
        if (System.currentTimeMillis() - lastSyncAt < SYNC_COOLDOWN_MS) return
        if (local.isLoading) return

        // 2. Content Validation & Auto-Follow
        val hostType = if (host.type.equals("tv", true)) "show" else "movie"
        val hostKey = if (host.tmdbId != null) {
            if (hostType == "show") {
                "show:${host.tmdbId}:${host.seasonId}:${host.episodeId}"
            } else {
                "movie:${host.tmdbId}"
            }
        } else null
        
        val localType = if (localMeta.type.equals("TV Show", true)) "show" else "movie"
        val localKey = if (localMeta.tmdbId != null) {
            if (localType == "show") {
                "show:${localMeta.tmdbId}:${localMeta.seasonId}:${localMeta.episodeId}"
            } else {
                "movie:${localMeta.tmdbId}"
            }
        } else null
        
        if (hostKey != null && hostKey != localKey) {
            if (lastFollowKey != hostKey) {
                lastFollowKey = hostKey
                val targetTmdbId = host.tmdbId ?: return
                hasInitialSynced = false // Reset sync for new content
                _actions.emit(WatchPartyAction.Navigate(
                    tmdbId = targetTmdbId.toIntOrNull() ?: 0,
                    mediaType = host.type ?: "movie",
                    season = host.seasonNumber,
                    episode = host.episodeNumber,
                    title = host.title.orEmpty(),
                    year = host.year,
                    poster = host.poster
                ))
            }
            _contentMismatch.value = true
            return
        }
        _contentMismatch.value = false
        lastFollowKey = hostKey

        // 3. Play State Confirmation (Prevents jitter)
        val hostIsPlaying = host.isPlaying && !host.isPaused
        if (pendingHostPlaying == hostIsPlaying) {
            confirmedHostPlaying = hostIsPlaying
        } else {
            pendingHostPlaying = hostIsPlaying
            return // Wait for next tick to confirm
        }

        // 4. Latency Compensation
        val elapsed = Math.max(0L, System.currentTimeMillis() - host.lastUpdate) / 1000.0
        val predicted = if (hostIsPlaying) host.time + elapsed else host.time
        
        // 5. Duration Guard
        if (host.duration > 0 && local.duration > 0 && Math.abs(host.duration - local.duration) > 30.0) {
            return
        }

        // 6. Start-of-Video Sync Guard (Rubber-band protection)
        if (hostIsPlaying && predicted < 0.5 && host.duration > 30 && local.time > 1.0) {
            return
        }

        // 7. Drift & State Logic
        val drift = local.time - predicted
        val needsInitial = !hasInitialSynced
        val needsDrift = hasInitialSynced && Math.abs(drift) > DRIFT_THRESHOLD_SECONDS
        val needsPlayState = confirmedHostPlaying != null && lastHostPlaying != null && confirmedHostPlaying != lastHostPlaying

        if (needsInitial || needsDrift || needsPlayState) {
            val targetPlaying = if (needsInitial) hostIsPlaying else confirmedHostPlaying ?: hostIsPlaying
            
            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()

            // Apply sync actions
            if (needsInitial || needsDrift || needsPlayState) {
                _actions.emit(WatchPartyAction.Seek((predicted * 1000).toLong()))
            }
            
            // Wait for seek to "settle" before toggling playback
            delay(SEEK_SETTLE_MS)
            
            if (targetPlaying) _actions.emit(WatchPartyAction.Play)
            else _actions.emit(WatchPartyAction.Pause)

            // Final settle window
            delay(PLAY_SETTLE_MS)
            
            _isSyncing.value = false
            syncInProgress = false
            hasInitialSynced = true
            lastHostPlaying = targetPlaying
        }
    }
}
