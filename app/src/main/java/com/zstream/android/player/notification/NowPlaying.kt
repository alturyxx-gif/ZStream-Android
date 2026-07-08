package com.zstream.android.player.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingInfo(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

interface NowPlayingControls {
    fun play()
    fun pause()
    fun stop()
}

/**
 * Bridges the Compose player screen (which owns the MpvPlayer instance) and
 * PlaybackNotificationService (which owns the MediaSession + notification), since
 * neither side can hold a direct reference to the other's lifecycle.
 */
object NowPlayingController {
    private val _state = MutableStateFlow<NowPlayingInfo?>(null)
    val state = _state.asStateFlow()

    @Volatile
    var controls: NowPlayingControls? = null

    fun update(info: NowPlayingInfo) {
        _state.value = info
    }

    fun clear() {
        _state.value = null
        controls = null
    }
}
