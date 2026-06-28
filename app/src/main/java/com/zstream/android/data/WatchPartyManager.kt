package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WatchPartyManager"

private const val POLL_INTERVAL_MS = 2000L
private const val BACKOFF_MAX_MS = 15000L
private const val STALE_USER_MS = 12000L
private const val DRIFT_THRESHOLD_SECONDS = 5.0
private const val SYNC_COOLDOWN_MS = 3000L
private const val SEEK_SETTLE_MS = 500L
private const val PLAY_SETTLE_MS = 500L

private const val HOST_REPORT_INTERVAL_MS = 1500L
private const val GUEST_REPORT_INTERVAL_MS = 3000L
private const val MIN_CHANGE_INTERVAL_MS = 250L

sealed class WatchPartyAction {
    data class Seek(val timeMs: Long) : WatchPartyAction()
    object Play : WatchPartyAction()
    object Pause : WatchPartyAction()
    data class Navigate(
        val tmdbId: String,
        val mediaType: String,
        val season: Int?,
        val episode: Int?,
        val seasonId: String?,
        val episodeId: String?,
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
    val isLoading: Boolean,
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
    private var joinJob: Job? = null
    private var manualSyncJob: Job? = null

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
    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _contentMismatch = MutableStateFlow(false)
    val contentMismatch: StateFlow<Boolean> = _contentMismatch.asStateFlow()

    private val _selfUserId = MutableStateFlow<String?>(null)
    val selfUserId: StateFlow<String?> = _selfUserId.asStateFlow()

    private val _actions = MutableSharedFlow<WatchPartyAction>(replay = 0)
    val actions: SharedFlow<WatchPartyAction> = _actions.asSharedFlow()

    // --- Engine Internal Refs ---
    private var consecutiveErrors = 0
    private var consecutiveReportingErrors = 0
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
    private var localStateOwner: String? = null

    /**
     * Start hosting a new watch party.
     */
    fun hostRoom() {
        joinJob?.cancel()
        joinJob = null
        manualSyncJob?.cancel()
        manualSyncJob = null
        val chars = ('A'..'Z') + ('0'..'9')
        val code = (1..6).map { chars.random() }.joinToString("")
        _roomCode.value = code
        _isHost.value = true
        _enabled.value = true
        _isRegistering.value = true
        resetEngineState()
        refreshSelfUserId()
        startEngine()
        startReporter()
    }

    /**
     * Update the active room code.
     */
    fun updateRoomCode(code: String) {
        val normalized = code.uppercase()
        if (_roomCode.value == normalized) return
        _roomCode.value = normalized
        // Reset reporter state to force immediate update to new code
        lastFingerprint = ""
        lastReportTime = 0L
    }

    /**
     * Join an existing watch party.
     */
    fun joinRoom(code: String) {
        val normalized = code.uppercase()
        manualSyncJob?.cancel()
        manualSyncJob = null
        joinJob?.cancel()
        _isRegistering.value = true
        _isOffline.value = false
        joinJob = scope.launch {
            repository.getRoomStatuses(normalized).fold(
                onSuccess = { response ->
                    val cutoff = System.currentTimeMillis() - STALE_USER_MS
                    val hasHost = response.users.values.any { statuses ->
                        statuses.maxByOrNull { it.timestamp }?.let { it.isHost && it.timestamp >= cutoff } == true
                    }
                    if (hasHost) beginJoinRoom(normalized) else _isOffline.value = true
                },
                onFailure = { _isOffline.value = true },
            )
            _isRegistering.value = false
        }
    }

    private fun beginJoinRoom(code: String) {
        val sameRoom = _roomCode.value == code
        _roomCode.value = code
        _isHost.value = false
        _enabled.value = true

        if (!sameRoom) resetEngineState()

        // Force host content to be evaluated even when jumping back into the same room.
        lastLocalContent = null
        lastLocalState = null
        localStateOwner = null
        lastFollowKey = null
        hasInitialSynced = false

        refreshSelfUserId()
        startEngine()
        startReporter()
    }

    /**
     * Leave current watch party.
     */
    fun leaveRoom() {
        joinJob?.cancel()
        joinJob = null
        manualSyncJob?.cancel()
        manualSyncJob = null
        _enabled.value = false
        _roomCode.value = null
        _isHost.value = false
        _participants.value = emptyList()
        _isOffline.value = false
        _isRegistering.value = false
        _isSyncing.value = false
        syncInProgress = false
        _contentMismatch.value = false
        _selfUserId.value = null
        lastLocalContent = null
        lastLocalState = null
        localStateOwner = null
        lastFollowKey = null
        stopEngine()
        stopReporter()
    }

    /**
     * Force a manual sync with the host.
     */
    fun manualSync() {
        if (_isHost.value) return
        val host = _participants.value.find { it.isHost } ?: return

        manualSyncJob?.cancel()
        manualSyncJob = scope.launch {
            val hostIsPlayingMotion = host.isPlaying && !host.isPaused
            val hostIsPlayingIntent = !host.isPaused
            val elapsed = Math.max(0L, System.currentTimeMillis() - host.lastUpdate) / 1000.0
            val predicted = if (hostIsPlayingMotion) host.time + elapsed else host.time

            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()
            try {
                _actions.emit(WatchPartyAction.Seek((predicted * 1000).toLong()))
                delay(SEEK_SETTLE_MS)

                if (hostIsPlayingIntent) _actions.emit(WatchPartyAction.Play)
                else _actions.emit(WatchPartyAction.Pause)

                delay(PLAY_SETTLE_MS)
                hasInitialSynced = true
                lastHostPlaying = hostIsPlayingIntent
                pendingHostPlaying = hostIsPlayingIntent
                confirmedHostPlaying = hostIsPlayingIntent
                Log.i(TAG, "Manual Sync complete")
            } finally {
                _isSyncing.value = false
                syncInProgress = false
            }
        }
    }

    /**
     * Called by PlayerViewModel to update local state for reporting and sync comparison.
     */
    fun updateLocalState(owner: String, content: WatchPartyContentDto, player: WatchPartyPlayerDto) {
        localStateOwner = owner
        lastLocalContent = content
        lastLocalState = player
    }

    /**
     * Clear local state when the player is closed.
     */
    fun clearLocalState(owner: String) {
        if (localStateOwner != owner) return
        localStateOwner = null
        lastLocalContent = null
        lastLocalState = null
        // We do NOT clear lastFollowKey here to prevent "Aggressive Auto-Follow" loops.
        // Auto-follow will only trigger again if the host changes content.
    }

    private fun resetEngineState() {
        consecutiveErrors = 0
        consecutiveReportingErrors = 0
        syncInProgress = false
        _isSyncing.value = false
        hasInitialSynced = false
        lastHostPlaying = null
        pendingHostPlaying = null
        confirmedHostPlaying = null
        lastSyncAt = 0L
        lastFollowKey = null
        lastFingerprint = ""
        lastReportTime = 0L
        // DO NOT reset lastLocalContent/lastLocalState here.
        // Doing so makes the engine think we have no content and immediately emits a Navigate action
        // even if we are already in the player watching that content.
    }

    private fun startEngine() {
        stopEngine()
        engineJob = scope.launch {
            while (isActive && _enabled.value) {
                val code = _roomCode.value ?: break
                
                refreshRoomData(code)

                val interval = if (consecutiveErrors > 0) {
                    minOf(POLL_INTERVAL_MS * (1L shl consecutiveErrors.coerceAtMost(3)), BACKOFF_MAX_MS)
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
                    // Guard: Don't report while a sync is in progress
                    if (syncInProgress || _isSyncing.value) {
                        delay(MIN_CHANGE_INTERVAL_MS)
                        continue
                    }

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
                        val userId = repository.getEffectiveUserId()
                        _selfUserId.value = userId
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
                            consecutiveReportingErrors = 0
                            _isOffline.value = false
                            _isRegistering.value = false
                        }.onFailure {
                            consecutiveReportingErrors++
                            if (consecutiveReportingErrors >= 3) {
                                _isOffline.value = true
                            }
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
                    isLoading = latest.player.isLoading,
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

        // 1. Content Validation & Auto-Follow (Global - works on Home screen)
        val hostType = if (host.type.equals("tv", true)) "show" else "movie"
        val hostKey = if (host.tmdbId != null) {
            if (hostType == "show") {
                "show:${host.tmdbId}:${host.seasonId ?: host.seasonNumber}:${host.episodeId ?: host.episodeNumber}"
            } else {
                "movie:${host.tmdbId}"
            }
        } else null
        
        val localMeta = lastLocalContent
        val localType = if (localMeta?.type?.equals("TV Show", true) == true || localMeta?.type?.equals("tv", true) == true) "show" else "movie"
        val localKey = if (localMeta?.tmdbId != null) {
            if (localType == "show") {
                "show:${localMeta.tmdbId}:${localMeta.seasonId ?: localMeta.seasonNumber}:${localMeta.episodeId ?: localMeta.episodeNumber}"
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
                    tmdbId = targetTmdbId,
                    mediaType = host.type ?: "movie",
                    season = host.seasonNumber,
                    episode = host.episodeNumber,
                    seasonId = host.seasonId,
                    episodeId = host.episodeId,
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

        // 2. Player-Specific Logic (Requires local playback state)
        val local = lastLocalState ?: return
        if (localMeta == null) return

        // Check for sync block conditions
        if (syncInProgress) {
            return
        }
        if (System.currentTimeMillis() - lastSyncAt < SYNC_COOLDOWN_MS) {
            return
        }

        // 3. Play State Confirmation (Prevents jitter)
        // Use Host Intent (isPaused) for sync, but Host Motion (isPlaying && !isPaused) for prediction
        val hostIsPlayingMotion = host.isPlaying && !host.isPaused
        val hostIsPlayingIntent = !host.isPaused
        
        if (hasInitialSynced) {
            if (pendingHostPlaying == hostIsPlayingIntent) {
                confirmedHostPlaying = hostIsPlayingIntent
            } else {
                pendingHostPlaying = hostIsPlayingIntent
                return // Wait for next tick to confirm
            }
        } else {
            // First sync: bypass jitter protection
            pendingHostPlaying = hostIsPlayingIntent
            confirmedHostPlaying = hostIsPlayingIntent
        }

        // 4. Latency Compensation
        val elapsed = Math.max(0L, System.currentTimeMillis() - host.lastUpdate) / 1000.0
        val predicted = if (hostIsPlayingMotion) host.time + elapsed else host.time
        
        // 5. Host & Local Loading Guards
        // If host is loading, their time is unstable. Ignore drift but process state changes.
        if (host.isLoading) {
            return
        }

        // The player collector is installed only after Media3 is ready.
        if (local.isLoading) return

        val drift = local.time - predicted
        val needsInitial = !hasInitialSynced
        val needsDrift = hasInitialSynced && Math.abs(drift) > DRIFT_THRESHOLD_SECONDS
        
        // Fix: needsPlayState should be true if we don't have a lastHostPlaying yet (first sync)
        val needsPlayState = confirmedHostPlaying != null && (lastHostPlaying == null || confirmedHostPlaying != lastHostPlaying)

        // 6. Duration Guard
        if (host.duration > 0 && local.duration > 0 && Math.abs(host.duration - local.duration) > 30.0) {
            return
        }

        // 7. Start-of-Video Sync Guard (Rubber-band protection)
        if (hostIsPlayingMotion && predicted < 0.5 && host.duration > 30 && local.time > 1.0) {
            return
        }

        if (needsInitial || needsDrift || needsPlayState) {
            val targetPlaying = if (needsInitial) hostIsPlayingIntent else confirmedHostPlaying ?: hostIsPlayingIntent
            
            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()
            try {
                _actions.emit(WatchPartyAction.Seek((predicted * 1000).toLong()))
                delay(SEEK_SETTLE_MS)

                if (targetPlaying) _actions.emit(WatchPartyAction.Play)
                else _actions.emit(WatchPartyAction.Pause)

                delay(PLAY_SETTLE_MS)
                hasInitialSynced = true
                lastHostPlaying = targetPlaying
            } finally {
                _isSyncing.value = false
                syncInProgress = false
            }
        }
    }

    private fun refreshSelfUserId() {
        scope.launch {
            _selfUserId.value = repository.getEffectiveUserId()
        }
    }
}
