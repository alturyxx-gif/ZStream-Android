package com.zstream.android.ui.screens

import android.os.Build
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DevPlaybackSource {
    data class Ready(
        val url: String,
        val type: String,
        val headers: Map<String, String>,
    ) : DevPlaybackSource()
    object Invalid : DevPlaybackSource()
}

/**
 * Backs the dev-only stream tester (see DevVideoScreen). No progress tracking, no downloads, no
 * skip segments, no source list -- this exists purely to point ExoPlayer at a URL/type/headers
 * combo someone typed in or picked from a preset, mirroring the website's /dev/video tool.
 */
@HiltViewModel
class DevPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settingsPrefs: SettingsPreferences,
) : ViewModel() {
    val settings = settingsPrefs.settings

    val source: DevPlaybackSource = run {
        val url = savedState.get<String>("url")?.let { android.net.Uri.decode(it) }
        val type = savedState.get<String>("type")?.let { android.net.Uri.decode(it) } ?: "mp4"
        val headersJson = savedState.get<String>("headers")?.let { android.net.Uri.decode(it) }
        val headers = headersJson?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching {
                val obj = org.json.JSONObject(json)
                obj.keys().asSequence().associateWith { key -> obj.getString(key) }
            }.getOrNull()
        } ?: emptyMap()
        if (url.isNullOrBlank()) DevPlaybackSource.Invalid else DevPlaybackSource.Ready(url, type, headers)
    }

    fun updateSettings(settings: SettingsEntity) {
        viewModelScope.launch { settingsPrefs.updateSettings(settings, syncToRemote = false) }
    }
}

@OptIn(UnstableApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DevPlayerScreen(nav: NavController, vm: DevPlayerViewModel = hiltViewModel()) {
    val ready = vm.source as? DevPlaybackSource.Ready
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val settings by vm.settings.collectAsState(initial = SettingsEntity())
    var controlsVisible by remember { mutableStateOf(true) }
    var menuPage by remember { mutableStateOf<PlayerMenuPage?>(null) }
    val menuBackstack = remember { mutableStateListOf<PlayerMenuPage>() }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    val player = remember(ready) {
        ready?.let { r ->
            val mimeType = when (r.type.lowercase()) {
                "hls" -> MimeTypes.APPLICATION_M3U8
                else -> MimeTypes.VIDEO_MP4
            }
            val mediaSourceFactory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(r.headers)
                        .setConnectTimeoutMs(30_000)
                        .setReadTimeoutMs(30_000),
                )
            )
            ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    val mediaItem = MediaItem.Builder().setUri(r.url).setMimeType(mimeType).build()
                    setMediaItem(mediaItem)
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.e("DevPlayback", "${error.errorCodeName}: ${error.message}", error)
                            playbackError = error.message ?: error.errorCodeName
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state != Player.STATE_IDLE) playbackError = null
                        }
                    })
                    playWhenReady = true
                    prepare()
                }
        }
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ready == null -> {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ZsStatusBanner(
                        message = "No stream URL was provided.",
                        variant = ZsStatusBannerVariant.Error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
            player == null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = nativeResizeMode(settings.videoScaleMode)
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            playerViewRef.value = this
                        }
                    },
                    update = { view -> view.resizeMode = nativeResizeMode(settings.videoScaleMode) },
                    modifier = Modifier.fillMaxSize(),
                )
                LaunchedEffect(settings.videoScaleMode, playerViewRef.value) {
                    playerViewRef.value?.resizeMode = nativeResizeMode(settings.videoScaleMode)
                }
                VideoBrightnessOverlay(playerViewRef.value, settings.videoBrightness, Modifier.fillMaxSize())

                val readyState = PlayerState.Ready(
                    streamUrl = ready.url,
                    streamType = ready.type,
                    headers = ready.headers,
                    subtitles = emptyList(),
                    sources = emptyList(),
                    sourceId = "dev",
                )
                PlayerControls(
                    player = player,
                    title = "Dev stream: ${ready.type.uppercase()}",
                    episodeLabel = null,
                    readyState = readyState,
                    settings = settings,
                    selectedSubtitleLanguage = null,
                    selectedSubtitleId = null,
                    isBookmarked = false,
                    onToggleBookmark = {},
                    controlsVisible = controlsVisible,
                    onControlsVisibilityChanged = { controlsVisible = it },
                    subtitlesEnabled = false,
                    onToggleSubtitles = {},
                    onSelectSubtitle = {},
                    onAutoSelectSubtitle = {},
                    onDisableSubtitles = {},
                    onUpdateSettings = vm::updateSettings,
                    onSetSubtitleDelay = {},
                    onSetOverrideCasing = {},
                    subtitleDelay = 0f,
                    overrideCasing = false,
                    onSetEnableAutoplay = {},
                    onSetVideoBrightness = { vm.updateSettings(settings.copy(videoBrightness = it)) },
                    onSetVolumeBoost = { vm.updateSettings(settings.copy(volumeBoost = it)) },
                    onSetVideoScaleMode = { vm.updateSettings(settings.copy(videoScaleMode = it)) },
                    // Dev tool always plays exactly the URL that was typed/pasted in -- there is no
                    // plugin source list to switch between, so these are permanently no-ops.
                    onSelectSource = {},
                    onUseSource = {},
                    onSwitchVariant = {},
                    skipSegments = emptyList(),
                    canSubmitSkipSegments = false,
                    hasTidbKey = false,
                    onSubmitSkipSegment = { Result.success(Unit) },
                    tmdbId = 0,
                    mediaType = "movie",
                    seasonNumber = null,
                    episodeNumber = null,
                    seasonId = null,
                    episodeId = null,
                    onInfo = {},
                    onBack = onBack,
                    onPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(
                                android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9))
                                    .build()
                            )
                        }
                    },
                    roomCode = null,
                    participants = emptyList(),
                    myUserId = null,
                    isSyncing = false,
                    isRegistering = false,
                    contentMismatch = false,
                    durationMismatch = false,
                    hostGraceDeadlineMs = null,
                    isOffline = false,
                    isHost = false,
                    onHostWatchParty = {},
                    onLeaveWatchParty = {},
                    onJoinWatchParty = {},
                    onUpdateRoomCode = {},
                    onManualSync = {},
                    showInfoSheet = false,
                    menuPage = menuPage,
                    onMenuBack = {
                        if (menuBackstack.isNotEmpty()) menuBackstack.removeAt(menuBackstack.lastIndex)
                        menuPage = menuBackstack.lastOrNull()
                    },
                    onMenuPageChange = { page ->
                        if (page == null) {
                            menuBackstack.clear()
                            menuPage = null
                        } else {
                            menuBackstack += page
                            menuPage = page
                        }
                    },
                    allProgress = emptyList(),
                    currentSeason = null,
                    currentEpisode = null,
                    onLoadSeason = {},
                    onSwitchEpisode = { _, _ -> },
                    poster = null,
                    nav = nav,
                    tvDetail = null,
                    currentSeasonDetail = null,
                    pauseMetadata = null,
                )
                if (playbackError != null) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ZsStatusBanner(
                                message = playbackError ?: "Playback error",
                                variant = ZsStatusBannerVariant.Error,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                playbackError = null
                                player.prepare()
                                player.playWhenReady = true
                            }) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}
