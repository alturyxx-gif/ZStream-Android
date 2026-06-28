package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WatchPartyManager"

private const val POLL_INTERVAL_MS = 500L
private const val BACKOFF_MAX_MS = 15000L
private const val STALE_USER_MS = 12000L
private const val HOST_GRACE_PERIOD_MS = 5000L
private const val DRIFT_THRESHOLD_SECONDS = 5.0
private const val SYNC_COOLDOWN_MS = 1000L
private const val SEEK_SETTLE_MS = 500L
private const val PLAY_SETTLE_MS = 200L

private const val HOST_REPORT_INTERVAL_MS = 250L
private const val GUEST_REPORT_INTERVAL_MS = 1500L
private const val MIN_CHANGE_INTERVAL_MS = 150L
private const val ACTION_DEDUP_WINDOW_MS = 750L

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
    val isStale: Boolean = false,
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
    private val _durationMismatch = MutableStateFlow(false)
    val durationMismatch: StateFlow<Boolean> = _durationMismatch.asStateFlow()
    private val _hostGraceDeadlineMs = MutableStateFlow<Long?>(null)
    val hostGraceDeadlineMs: StateFlow<Long?> = _hostGraceDeadlineMs.asStateFlow()

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
    private var lastSyncAt = 0L
    private var lastFollowKey: String? = null
    private var lastKnownHost: Participant? = null

    // --- Reporter Internal Refs ---
    private var lastReportTime = 0L
    private var lastFingerprint = ""
    private var lastLocalState: WatchPartyPlayerDto? = null
    private var lastLocalContent: WatchPartyContentDto? = null
    private var localStateOwner: String? = null
    private var lastActionFingerprint = ""
    private var lastActionAt = 0L

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
        _durationMismatch.value = false
        _hostGraceDeadlineMs.value = null
        _selfUserId.value = null
        lastLocalContent = null
        lastLocalState = null
        localStateOwner = null
        lastFollowKey = null
        lastKnownHost = null
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
                emitAction(WatchPartyAction.Seek((predicted * 1000).toLong()))
                delay(SEEK_SETTLE_MS)

                if (hostIsPlayingIntent) emitAction(WatchPartyAction.Play)
                else emitAction(WatchPartyAction.Pause)

                delay(PLAY_SETTLE_MS)
                hasInitialSynced = true
                lastHostPlaying = hostIsPlayingIntent
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
        lastLocalContent = mergeLocalContent(content)
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
        lastSyncAt = 0L
        lastFollowKey = null
        lastFingerprint = ""
        lastReportTime = 0L
        lastActionFingerprint = ""
        lastActionAt = 0L
        lastKnownHost = null
        _durationMismatch.value = false
        _hostGraceDeadlineMs.value = null
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
            val freshUsers = response.users.mapNotNull { (_, statuses) ->
                val latest = statuses.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                if ((now - latest.timestamp) > STALE_USER_MS) return@mapNotNull null
                
                Participant(
                    userId = latest.userId,
                    isHost = latest.isHost,
                    isStale = false,
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
            }.toMutableList()

            val freshHost = freshUsers.find { it.isHost }
            if (freshHost != null) {
                lastKnownHost = freshHost
                _hostGraceDeadlineMs.value = null
            } else if (!_isHost.value) {
                val cachedHost = lastKnownHost
                val graceDeadline = cachedHost?.lastUpdate?.plus(STALE_USER_MS + HOST_GRACE_PERIOD_MS)
                if (cachedHost != null && graceDeadline != null && now < graceDeadline) {
                    _hostGraceDeadlineMs.value = graceDeadline
                    freshUsers.removeAll { it.userId == cachedHost.userId }
                    freshUsers.add(cachedHost.copy(isStale = true))
                } else {
                    _hostGraceDeadlineMs.value = null
                    lastKnownHost = null
                }
            } else {
                _hostGraceDeadlineMs.value = null
            }

            val users = freshUsers.sortedWith(compareByDescending<Participant> { it.isHost }.thenByDescending { it.lastUpdate })

            _participants.value = users

            if (!_isHost.value && freshHost != null) {
                processGuestEngine(users, freshHost)
            }
        }.onFailure {
            consecutiveErrors++
            if (consecutiveErrors >= 3) {
                _isOffline.value = true
            }
            Log.e(TAG, "Failed to refresh room data", it)
        }
    }

    private suspend fun processGuestEngine(users: List<Participant>, host: Participant) {
        // 1. Content Validation & Auto-Follow (Global - works on Home screen)
        val hostKey = buildContentKey(host.tmdbId, host.type, host.seasonId, host.episodeId, host.seasonNumber, host.episodeNumber)
        val localMeta = lastLocalContent
        val localKey = localMeta?.let {
            buildContentKey(it.tmdbId, it.type, it.seasonId, it.episodeId, it.seasonNumber, it.episodeNumber)
        }

        if (!contentMatches(host, localMeta)) {
            if (lastFollowKey != hostKey) {
                lastFollowKey = hostKey
                val targetTmdbId = host.tmdbId ?: return
                hasInitialSynced = false // Reset sync for new content
                emitAction(WatchPartyAction.Navigate(
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

        // 3. Sync decisions
        // Use Host Intent (isPaused) for play/pause, but Host Motion (isPlaying && !isPaused) for prediction.
        val hostIsPlayingMotion = host.isPlaying && !host.isPaused
        val hostIsPlayingIntent = !host.isPaused

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
        val needsPlayState = lastHostPlaying == null || hostIsPlayingIntent != lastHostPlaying
        val recentlySynced = System.currentTimeMillis() - lastSyncAt < SYNC_COOLDOWN_MS

        // 6. Duration Guard
        val hasDurationMismatch = host.duration > 0 && local.duration > 0 && Math.abs(host.duration - local.duration) > 30.0
        _durationMismatch.value = hasDurationMismatch

        // 7. Start-of-Video Sync Guard (Rubber-band protection)
        if (hostIsPlayingMotion && predicted < 0.5 && host.duration > 30 && local.time > 1.0) {
            return
        }

        if (recentlySynced && !needsPlayState) {
            return
        }

        if (!needsInitial && !needsDrift && needsPlayState) {
            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()
            try {
                if (hostIsPlayingIntent) emitAction(WatchPartyAction.Play)
                else emitAction(WatchPartyAction.Pause)

                delay(PLAY_SETTLE_MS)
                hasInitialSynced = true
                lastHostPlaying = hostIsPlayingIntent
            } finally {
                _isSyncing.value = false
                syncInProgress = false
            }
            return
        }

        if (hasDurationMismatch) {
            return
        }

        if (needsInitial || needsDrift || needsPlayState) {
            val targetPlaying = hostIsPlayingIntent
            
            syncInProgress = true
            _isSyncing.value = true
            lastSyncAt = System.currentTimeMillis()
            try {
                emitAction(WatchPartyAction.Seek((predicted * 1000).toLong()))
                delay(SEEK_SETTLE_MS)

                if (targetPlaying) emitAction(WatchPartyAction.Play)
                else emitAction(WatchPartyAction.Pause)

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

    private fun mergeLocalContent(content: WatchPartyContentDto): WatchPartyContentDto {
        val previous = lastLocalContent ?: return content
        if (!content.tmdbId.isNullOrBlank()) return content
        if (!contentLooksLikeSameSelection(content, previous)) return content
        return content.copy(
            tmdbId = previous.tmdbId,
            seasonId = content.seasonId ?: previous.seasonId,
            episodeId = content.episodeId ?: previous.episodeId,
            seasonNumber = content.seasonNumber ?: previous.seasonNumber,
            episodeNumber = content.episodeNumber ?: previous.episodeNumber,
            year = content.year ?: previous.year,
            poster = content.poster ?: previous.poster
        )
    }

    private fun contentLooksLikeSameSelection(
        current: WatchPartyContentDto,
        previous: WatchPartyContentDto
    ): Boolean {
        if (!current.title.equals(previous.title, ignoreCase = true)) return false
        if (!current.type.equals(previous.type, ignoreCase = true)) return false
        if (current.seasonId != null && previous.seasonId != null && current.seasonId != previous.seasonId) return false
        if (current.episodeId != null && previous.episodeId != null && current.episodeId != previous.episodeId) return false
        if (current.seasonNumber != null && previous.seasonNumber != null && current.seasonNumber != previous.seasonNumber) return false
        if (current.episodeNumber != null && previous.episodeNumber != null && current.episodeNumber != previous.episodeNumber) return false
        return true
    }

    private fun buildContentKey(
        tmdbId: String?,
        type: String?,
        seasonId: String?,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): String? {
        val normalizedTmdbId = tmdbId ?: return null
        val normalizedType = if (type.equals("tv", true) || type.equals("show", true) || type.equals("TV Show", true)) "show" else "movie"
        return if (normalizedType == "show") {
            "show:$normalizedTmdbId:${seasonId ?: seasonNumber}:${episodeId ?: episodeNumber}"
        } else {
            "movie:$normalizedTmdbId"
        }
    }

    private fun contentMatches(host: Participant, local: WatchPartyContentDto?): Boolean {
        if (local == null) return false
        if (host.tmdbId == null || local.tmdbId == null) return false
        if (host.tmdbId != local.tmdbId) return false
        val hostIsShow = host.type.equals("tv", true) || host.type.equals("show", true) || host.type.equals("TV Show", true)
        val localIsShow = local.type.equals("tv", true) || local.type.equals("show", true) || local.type.equals("TV Show", true)
        if (hostIsShow != localIsShow) return false
        if (!hostIsShow) return true
        if (host.seasonId != null && local.seasonId != null && host.seasonId != local.seasonId) return false
        if (host.episodeId != null && local.episodeId != null && host.episodeId != local.episodeId) return false
        if (host.seasonNumber != null && local.seasonNumber != null && host.seasonNumber != local.seasonNumber) return false
        if (host.episodeNumber != null && local.episodeNumber != null && host.episodeNumber != local.episodeNumber) return false
        return host.seasonId != null || local.seasonId != null || host.episodeId != null || local.episodeId != null ||
            (host.seasonNumber != null && local.seasonNumber != null && host.episodeNumber != null && local.episodeNumber != null)
    }

    private suspend fun emitAction(action: WatchPartyAction) {
        val fingerprint = when (action) {
            is WatchPartyAction.Seek -> "seek:${action.timeMs / 250L}"
            WatchPartyAction.Play -> "play"
            WatchPartyAction.Pause -> "pause"
            is WatchPartyAction.Navigate -> "nav:${action.mediaType}:${action.tmdbId}:${action.seasonId ?: action.season}:${action.episodeId ?: action.episode}"
        }
        val now = System.currentTimeMillis()
        if (fingerprint == lastActionFingerprint && now - lastActionAt < ACTION_DEDUP_WINDOW_MS) {
            return
        }
        lastActionFingerprint = fingerprint
        lastActionAt = now
        _actions.emit(action)
    }
}
