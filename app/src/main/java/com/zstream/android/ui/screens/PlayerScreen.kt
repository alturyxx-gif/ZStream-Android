package com.zstream.android.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.TextureView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.util.TypedValue
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.OnPictureInPictureModeChangedProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.provider.WebViewDataSource
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.EntryPointAccessors
import com.zstream.android.Urls
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import coil.compose.AsyncImage

private val RedProgress = Color(0xFFE53935)

//  Layout constants 
private val SCRUBBER_SIDE_PADDING = 36.dp      // horizontal padding on progress bar
private val SCRUBBER_SLIDER_OFFSET = (-6).dp   // how far invisible slider overlaps above bottom bar
private val CENTER_ICON_SPACING = 40.dp        // gap between center play/skip buttons
private val CENTER_BUTTON_SIZE = 64.dp         // tap area for center buttons
private val CENTER_ICON_HEIGHT = 44.dp         // visual height of center icons

// Top bar
private val TOP_BAR_BUTTON_SIZE = 36.dp        // tap area for top bar icon buttons
private val TOP_BAR_ICON_SIZE = 16.dp          // visual size of top bar icons
private val TOP_BAR_LEFT_PADDING = 36.dp        // extra padding before first top-left icon
private val TOP_BAR_RIGHT_PADDING = 36.dp       // extra padding after last top-right element

// Bottom bar — left group (play, skip, volume, time)
private val BOTTOM_LEFT_START_PADDING = 36.dp   // padding before play button on left
private val BOTTOM_BAR_ICON_SIZE = 20.dp       // visual size of bottom bar icons
private val BOTTOM_BAR_BUTTON_SIZE = 36.dp     // tap area for bottom bar icons

// Bottom bar — right group (captions, expand)
private val BOTTOM_RIGHT_END_PADDING = 36.dp    // padding after last right icon

private val BOTTOM_BAR_PADDING_V = 8.dp        // vertical padding in bottom controls row
private const val NATIVE_SUBTITLE_BASE_OFFSET_DP = 20f
private const val NATIVE_SUBTITLE_OVERLAY_MULTIPLIER = 5f
private val MENU_PANEL_WIDTH = 360.dp
private val MENU_PANEL_HEIGHT = 430.dp
private val OVERLAY_PANEL_SHAPE = RoundedCornerShape(24.dp)
private val BOTTOM_BAR_MENU_BUTTON_SIZE = 42.dp
private val BOTTOM_BAR_MENU_ICON_SIZE = 22.dp
private val PLAYER_DETAIL_SHEET_CORNER_RADIUS = 28.dp
private const val PLAYER_DETAIL_SHEET_HEIGHT_FRACTION = 0.82f
private val PLAYER_DETAIL_SHEET_SIDE_MARGIN = 18.dp
private val PLAYER_DETAIL_SHEET_BOTTOM_MARGIN = 18.dp
private val PLAYER_DETAIL_SHEET_BACKDROP_HEIGHT = 360.dp
private val PLAYER_DETAIL_SHEET_CONTENT_PADDING = 32.dp
private val PLAYER_DETAIL_SHEET_BOTTOM_SPACER = 36.dp
private val PLAYER_DETAIL_SHEET_MIN_SCROLL_EXTRA = 1.dp
private val PLAYER_DETAIL_SHEET_SECTION_GAP = 18.dp
private val PLAYER_DETAIL_SHEET_CARD_COLOR = Color(0xFF141414).copy(alpha = 0.98f)
private val PLAYER_DETAIL_SHEET_OUTLINE = Color.White.copy(alpha = 0.08f)
private const val PLAYBACK_SPEED_MIN = 0.25f
private const val PLAYBACK_SPEED_MAX = 5f

private enum class PlayerMenuPage {
    Root, Captions, Playback, AdvancedColor, VideoMode, Source, Quality, Audio, Download, WatchParty, SkipSegments
}

private sealed class PlayerInfoState {
    object Loading : PlayerInfoState()
    data class Movie(val detail: MovieDetail) : PlayerInfoState()
    data class Tv(val detail: TvDetail, val selectedSeason: Season? = null) : PlayerInfoState()
    data class Error(val message: String) : PlayerInfoState()
}

private data class QualityOption(
    val label: String,
    val height: Int,
    val group: androidx.media3.common.TrackGroup,
    val trackIndex: Int,
    val selected: Boolean,
)

private data class AudioOption(
    val label: String,
    val language: String?,
    val group: androidx.media3.common.TrackGroup,
    val trackIndex: Int,
    val selected: Boolean,
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(nav: NavController, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val prevOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            prevOrientation?.let { activity.requestedOrientation = it }
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is PlayerState.Idle, is PlayerState.Scraping -> {
                val sources = (s as? PlayerState.Scraping)?.sources ?: emptyList()
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Finding stream…", color = Color.White, fontSize = 14.sp)
                    sources.forEach { src ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(when (src.status) { SourceStatus.TRYING -> "⟳"; SourceStatus.SUCCESS -> "✓"; SourceStatus.FAILED -> "✕" },
                                color = when (src.status) { SourceStatus.SUCCESS -> Color.Green; SourceStatus.FAILED -> Color.Red; else -> Color.Gray },
                                fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(src.id, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
            is PlayerState.Error -> {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = vm::load,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry")
                    }
                }
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
            is PlayerState.Ready -> {
                val accountVm: AccountViewModel = hiltViewModel()
                val session by accountVm.session.collectAsState()
                val progressList by accountVm.progress.collectAsState()
                val settings by vm.settings.collectAsState()
                val appRepos = remember(context) {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.zstream.android.di.RepositoryEntryPoint::class.java
                    )
                }
                val activity = LocalContext.current as? ComponentActivity
                val pipProvider = activity as? OnPictureInPictureModeChangedProvider
                var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
                val configuration = LocalConfiguration.current
                DisposableEffect(pipProvider) {
                    if (pipProvider == null) return@DisposableEffect onDispose {}
                    val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                        isInPip = info.isInPictureInPictureMode
                    }
                    pipProvider.addOnPictureInPictureModeChangedListener(listener)
                    onDispose {
                        pipProvider.removeOnPictureInPictureModeChangedListener(listener)
                    }
                }
                val tmdbRepo = appRepos.tmdbRepository()
                val playerScope = rememberCoroutineScope()
                var showInfoSheet by remember { mutableStateOf(false) }
                var infoState by remember { mutableStateOf<PlayerInfoState?>(null) }

                // Find existing progress for this tmdbId (mirroring p-stream ResumePart)
                val existingProgress = remember(progressList) {
                    progressList.firstOrNull { p ->
                        p.tmdbId == vm.tmdbId &&
                        (vm.episodeId == null || p.episodeId == vm.episodeId)
                    }
                }
                val resumeWatched = remember(existingProgress) {
                    existingProgress?.watched?.toLong() ?: 0L
                }
                val resumeDuration = remember(existingProgress) {
                    existingProgress?.duration?.toLong() ?: 0L
                }
                var resumeHandled by remember { mutableStateOf(false) }
                var showResumeDialog by remember { mutableStateOf(false) }
                var pendingResumeMs by remember { mutableLongStateOf(-1L) }

                // One-time snapshot of initial progress — prevents dialog appearing mid-playback
                // when the first sync creates a progress entry during a new watch.
                // Small delay ensures Room data is loaded before the 3s sync triggers.
                LaunchedEffect(Unit) {
                    delay(100)
                    val found = progressList.firstOrNull { p ->
                        p.tmdbId == vm.tmdbId && (vm.episodeId == null || p.episodeId == vm.episodeId)
                    }
                    val w = found?.watched?.toLong() ?: 0L
                    val d = found?.duration?.toLong() ?: 0L
                    if (w >= 20 && (d <= 0 || (d - w) >= 120)) {
                        showResumeDialog = true
                    }
                }

                val player = remember {
                    val subtitleConfigs = if (settings.enableNativeSubtitles) {
                        s.subtitles.mapNotNull { track ->
                            val mimeType = when (track.type.lowercase()) {
                                "srt" -> MimeTypes.APPLICATION_SUBRIP
                                "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                                else -> MimeTypes.TEXT_VTT
                            }
                            runCatching {
                                SubtitleConfiguration.Builder(Uri.parse(track.url))
                                    .setMimeType(mimeType)
                                    .setLanguage(track.language)
                                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                    .build()
                            }.getOrNull()
                        }
                    } else {
                        emptyList()
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(s.streamUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()

                    ExoPlayer.Builder(context).build().apply {
                        setMediaSource(DefaultMediaSourceFactory(WebViewDataSource.Factory(vm.getProxyPort())).createMediaSource(mediaItem))
                        videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                        prepare()
                        playWhenReady = true  // always start loading immediately
                    }
                }

                var currentPositionMs by remember { mutableLongStateOf(0L) }

                // Collect player position for subtitle timing
                LaunchedEffect(player) {
                    while (true) {
                        currentPositionMs = player.currentPosition.coerceAtLeast(0)
                        kotlinx.coroutines.delay(250)
                    }
                }

                // Seek to resume position once player is ready
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && pendingResumeMs >= 0) {
                                player.seekTo(pendingResumeMs)
                                pendingResumeMs = -1L
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                DisposableEffect(Unit) { onDispose { player.release() } }

                // Progress sync — mirrors p-stream ProgressSaver (3s interval, same guards)
                DisposableEffect(player, session) {
                    val s2 = session ?: return@DisposableEffect onDispose {}
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                    val job = scope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(3000)
                            // Player must be read on main thread
                            if (!player.isPlaying) continue
                            val watchedSec = player.currentPosition / 1000
                            val durationSec = player.duration.let { if (it > 0) it / 1000 else 0L }
                            if (watchedSec < 20) continue
                            if (durationSec > 0 && (durationSec - watchedSec) < 120) continue
                            // Network call can run on IO via accountVm's viewModelScope
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val poster = existingProgress?.posterPath ?: vm.poster
                                    accountVm.syncProgress(
                                        s2, vm.tmdbId, watchedSec, durationSec,
                                        vm.title, vm.year, vm.mediaType,
                                        vm.seasonId, vm.episodeId,
                                        vm.season, vm.episode,
                                        poster,
                                    )
                                }
                            }
                        }
                    }
                    onDispose {
                        job.cancel()
                        val watchedSec = player.currentPosition / 1000
                        val durationSec = player.duration.let { if (it > 0) it / 1000 else 0L }
                        if (watchedSec >= 20 && (durationSec <= 0 || (durationSec - watchedSec) >= 120)) {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        val poster = existingProgress?.posterPath ?: vm.poster
                                        accountVm.syncProgress(
                                            s2, vm.tmdbId, watchedSec, durationSec,
                                            vm.title, vm.year, vm.mediaType,
                                            vm.seasonId, vm.episodeId,
                                            vm.season, vm.episode,
                                            poster,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Re-apply RESIZE_MODE_FIT when video size changes (codec resolution updates)
                val playerViewRef = remember { androidx.compose.runtime.mutableStateOf<PlayerView?>(null) }
                val videoTextureRef = remember { androidx.compose.runtime.mutableStateOf<TextureView?>(null) }
                var currentVideoSize by remember { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }
                val latestSettings by rememberUpdatedState(settings)
                val latestVideoSize by rememberUpdatedState(currentVideoSize)
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            currentVideoSize = videoSize
                            val pv = playerViewRef.value ?: return
                            pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            videoTextureRef.value?.let { textureView ->
                                textureView.post {
                                    applyVideoAdjustments(textureView, settings, videoSize)
                                }
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                var controlsVisible by remember { mutableStateOf(true) }
                var audioSessionId by remember(player) { mutableIntStateOf(player.audioSessionId) }

                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onAudioSessionIdChanged(audioSessionIdValue: Int) {
                            audioSessionId = audioSessionIdValue
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                DisposableEffect(player, audioSessionId, settings.volumeBoost) {
                    if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
                        return@DisposableEffect onDispose {}
                    }

                    val enhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
                    if (enhancer == null) {
                        return@DisposableEffect onDispose {}
                    }

                    val enabled = settings.volumeBoost > 100
                    enhancer.setEnabled(enabled)
                    if (enabled) {
                        enhancer.setTargetGain(volumeBoostToMillibels(settings.volumeBoost))
                    }

                    onDispose {
                        runCatching { enhancer.release() }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                val textureView = TextureView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                }
                                textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                    applyVideoAdjustments(textureView, latestSettings, latestVideoSize)
                                }
                                addView(textureView, 0)
                                player.setVideoTextureView(textureView)
                                post {
                                    hidePlayerVideoSurface(this)
                                    applyVideoAdjustments(textureView, settings, currentVideoSize)
                                }
                                applyNativeSubtitleStyle(subtitleView, settings, controlsVisible, isInPip)
                                playerViewRef.value = this
                                videoTextureRef.value = textureView
                            }
                        },
                        update = { view ->
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            applyNativeSubtitleStyle(view.subtitleView, settings, controlsVisible, isInPip)
                            videoTextureRef.value?.let { applyVideoAdjustments(it, settings, currentVideoSize) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Custom Subtitle Overlay — using downloaded + parsed cues with timing
                val vmCues by vm.subtitleCues.collectAsState()
                val isBookmarked by vm.isBookmarked.collectAsState()
                val visibleCues = remember(vmCues, currentPositionMs) {
                    if (vmCues.isEmpty()) emptyList()
                    else vmCues.filter { cue ->
                        currentPositionMs in cue.startMs..<cue.endMs
                    }
                }
                val selectedLang by vm.selectedSubtitleLang.collectAsState()
                val subtitlesEnabled = settings.subtitlesEnabled

                LaunchedEffect(player, settings.enableNativeSubtitles, subtitlesEnabled, selectedLang) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.enableNativeSubtitles || !subtitlesEnabled)
                        .setPreferredTextLanguage(
                            if (settings.enableNativeSubtitles && subtitlesEnabled) {
                                selectedLang ?: settings.defaultSubtitleLanguage
                            } else {
                                null
                            }
                        )
                        .build()
                }

                if (!settings.enableNativeSubtitles && subtitlesEnabled && selectedLang != null && visibleCues.isNotEmpty()) {
                    // Move subtitles up when controls overlay is shown
                    val controlsBottom = if (isInPip) 0.dp else if (controlsVisible) 80.dp else 0.dp
                    Log.d("PlayerScreen", "rendering ${visibleCues.size} cues at ${currentPositionMs}ms, " +
                        "color=${settings.subtitleColor} size=${settings.subtitleSize} " +
                        "fontStyle=${settings.subtitleFontStyle} bgOpacity=${settings.subtitleBackgroundOpacity} " +
                        "bold=${settings.subtitleBold}")
                    val textColor = Color(android.graphics.Color.parseColor(settings.subtitleColor))
                    val bgAlpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f)
                    val pipSubtitleScale = if (isInPip) 0.72f else 1f
                    val fontSize = (settings.subtitleSize * 18 * pipSubtitleScale).sp
                    val lineHeight = (fontSize.value * settings.subtitleLineHeight).sp
                    val subtitleShadow = when (settings.subtitleFontStyle) {
                        "raised" -> Shadow(Color.Black.copy(alpha = 0.8f), offset = androidx.compose.ui.geometry.Offset(0f, -4f), blurRadius = 0f)
                        "depressed" -> Shadow(Color.Black.copy(alpha = 0.8f), offset = androidx.compose.ui.geometry.Offset(0f, 4f), blurRadius = 0f)
                        "dropShadow" -> Shadow(Color.Black.copy(alpha = 0.9f), offset = androidx.compose.ui.geometry.Offset(6f, 6f), blurRadius = 12f)
                        "Border" -> Shadow(Color.Black.copy(alpha = 1f), offset = androidx.compose.ui.geometry.Offset(0f, 0f), blurRadius = settings.subtitleBorderThickness * 2)
                        else -> Shadow(Color.Black.copy(alpha = 0.5f), offset = androidx.compose.ui.geometry.Offset(0f, 4f), blurRadius = 8f)
                    }
                    val fontFamily = when (settings.subtitleFont) {
                        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                        "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                        "sans-serif-condensed" -> androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
                        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = if (isInPip) 20.dp else 48.dp,
                                end = if (isInPip) 20.dp else 48.dp,
                                bottom = if (isInPip) 18.dp else (48 + settings.subtitleVerticalPosition * 6f).dp + controlsBottom
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            visibleCues.forEach { cue ->
                                Box(
                                    Modifier
                                        .background(
                                            Color.Black.copy(alpha = bgAlpha),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cue.text,
                                        color = textColor,
                                        fontSize = fontSize,
                                        fontWeight = if (settings.subtitleBold) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = fontFamily,
                                        lineHeight = lineHeight,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(shadow = subtitleShadow)
                                    )
                                }
                            }
                        }
                    }
                }

                PlayerControls(
                    player = player,
                    title = vm.title,
                    episodeLabel = if (vm.mediaType == "tv" && vm.season != null && vm.episode != null) {
                        "S${vm.season} E${vm.episode}"
                    } else null,
                    readyState = s,
                    settings = settings,
                    selectedSubtitleLanguage = selectedLang,
                    isBookmarked = isBookmarked != null,
                    onToggleBookmark = vm::toggleBookmark,
                    controlsVisible = controlsVisible,
                    onControlsVisibilityChanged = { controlsVisible = it },
                    subtitlesEnabled = subtitlesEnabled,
                    onToggleSubtitles = {
                        if (subtitlesEnabled) {
                            Log.d("PlayerScreen", "toggling subtitles OFF")
                            vm.disableSubtitles()
                        } else {
                            Log.d("PlayerScreen", "toggling subtitles ON")
                            vm.enableSubtitles()
                        }
                    },
                    onSelectSubtitle = vm::selectSubtitle,
                    onDisableSubtitles = vm::disableSubtitles,
                    onSetEnableAutoplay = vm::setEnableAutoplay,
                    onSetVideoBrightness = vm::setVideoBrightness,
                    onSetVideoContrast = vm::setVideoContrast,
                    onSetVideoSaturation = vm::setVideoSaturation,
                    onSetVideoHueRotate = vm::setVideoHueRotate,
                    onResetAdvancedColor = vm::resetAdvancedColor,
                    onSetVolumeBoost = vm::setVolumeBoost,
                    onSetVideoScaleMode = vm::setVideoScaleMode,
                    onInfo = {
                        showInfoSheet = true
                        playerScope.launch {
                            infoState = PlayerInfoState.Loading
                            infoState = runCatching {
                                if (vm.mediaType == "tv") {
                                    val detail = tmdbRepo.tvDetail(vm.tmdbId.toInt())
                                    val firstSeason = detail.seasons
                                        ?.firstOrNull { it.seasonNumber > 0 }
                                        ?.let { tmdbRepo.season(vm.tmdbId.toInt(), it.seasonNumber) }
                                    PlayerInfoState.Tv(detail, firstSeason)
                                } else {
                                    PlayerInfoState.Movie(tmdbRepo.movieDetail(vm.tmdbId.toInt()))
                                }
                            }.getOrElse { PlayerInfoState.Error(it.message ?: "Failed to load details") }
                        }
                    },
                    onBack = { nav.popBackStack() },
                    onPip = {
                        showInfoSheet = false
                        showResumeDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            isInPip = true
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            )
                        }
                    })

                AnimatedVisibility(
                    visible = showInfoSheet,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.34f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showInfoSheet = false }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(PLAYER_DETAIL_SHEET_HEIGHT_FRACTION)
                                .padding(
                                    start = PLAYER_DETAIL_SHEET_SIDE_MARGIN,
                                    end = PLAYER_DETAIL_SHEET_SIDE_MARGIN,
                                    bottom = PLAYER_DETAIL_SHEET_BOTTOM_MARGIN
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {}
                                .clip(RoundedCornerShape(PLAYER_DETAIL_SHEET_CORNER_RADIUS))
                        ) {
                            PlayerInfoSheet(
                                state = infoState,
                                nav = nav,
                                isBookmarked = isBookmarked != null,
                                onToggleBookmark = vm::toggleBookmark,
                                onClose = { showInfoSheet = false },
                                onSelectSeason = { seasonNumber ->
                                    playerScope.launch {
                                        val current = infoState as? PlayerInfoState.Tv ?: return@launch
                                        infoState = current.copy(
                                            selectedSeason = tmdbRepo.season(vm.tmdbId.toInt(), seasonNumber)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Resume dialog — mirrors p-stream ResumePart
                if (showResumeDialog) {
                    val pct = if (resumeDuration > 0) ((resumeWatched * 100) / resumeDuration).toInt() else 0
                    AlertDialog(
                        onDismissRequest = {},
                        containerColor = Color(0xFF1A1A2E),
                        title = { Text("Continue Watching?", color = Color.White) },
                        text = { Text("You're $pct% through. Resume from where you left off?", color = Color.White.copy(alpha = 0.7f)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    // If already ready, seek now; otherwise queue for STATE_READY
                                    if (player.playbackState == Player.STATE_READY) {
                                        player.seekTo(resumeWatched * 1000)
                                    } else {
                                        pendingResumeMs = resumeWatched * 1000
                                    }
                                    showResumeDialog = false
                                    resumeHandled = true
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Resume", color = Color(0xFF7C3AED)) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showResumeDialog = false
                                    resumeHandled = true
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Restart", color = Color.White.copy(alpha = 0.6f)) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun applyNativeSubtitleStyle(
    subtitleView: androidx.media3.ui.SubtitleView?,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    controlsVisible: Boolean,
    isInPip: Boolean,
) {
    if (subtitleView == null) return

    subtitleView.visibility = if (settings.enableNativeSubtitles) android.view.View.VISIBLE else android.view.View.GONE
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setUserDefaultStyle()
    subtitleView.setUserDefaultTextSize()
    subtitleView.setBottomPaddingFraction(androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    subtitleView.scaleX = if (isInPip) 0.78f else 1f
    subtitleView.scaleY = if (isInPip) 0.78f else 1f
    subtitleView.pivotX = subtitleView.width / 2f
    subtitleView.pivotY = subtitleView.height.toFloat()
    val baseOffsetPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        NATIVE_SUBTITLE_BASE_OFFSET_DP,
        Resources.getSystem().displayMetrics,
    )
    subtitleView.translationY = when {
        isInPip -> 0f
        controlsVisible -> -baseOffsetPx * NATIVE_SUBTITLE_OVERLAY_MULTIPLIER
        else -> -baseOffsetPx
    }
}

private fun volumeBoostToMillibels(volumeBoost: Int): Int {
    val over = (volumeBoost - 100).coerceAtLeast(0)
    return over * 45
}

private fun hidePlayerVideoSurface(view: View) {
    if (view is TextureView) return
    if (view is android.view.SurfaceView) {
        view.visibility = View.GONE
        return
    }
    if (view is android.view.ViewGroup) {
        for (index in 0 until view.childCount) {
            hidePlayerVideoSurface(view.getChildAt(index))
        }
    }
}

private fun buildVideoColorMatrix(
    settings: com.zstream.android.data.local.entity.SettingsEntity,
): ColorMatrix? {
    val brightness = settings.videoBrightness
    val contrast = settings.videoContrast
    val saturation = settings.videoSaturation
    val hueRotate = settings.videoHueRotate

    if (brightness == 100 && contrast == 100 && saturation == 100 && hueRotate == 0) {
        return null
    }

    val result = ColorMatrix()

    if (brightness != 100) {
        val brightnessScale = brightness / 100f
        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    brightnessScale, 0f, 0f, 0f, 0f,
                    0f, brightnessScale, 0f, 0f, 0f,
                    0f, 0f, brightnessScale, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (contrast != 100) {
        val contrastScale = contrast / 100f
        val translate = (-0.5f * contrastScale + 0.5f) * 255f
        result.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    contrastScale, 0f, 0f, 0f, translate,
                    0f, contrastScale, 0f, 0f, translate,
                    0f, 0f, contrastScale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (saturation != 100) {
        val saturationMatrix = android.graphics.ColorMatrix()
        saturationMatrix.setSaturation(saturation / 100f)
        result.postConcat(saturationMatrix)
    }

    if (hueRotate != 0) {
        val hueMatrix = ColorMatrix().apply { setRotate(0, hueRotate.toFloat()) }
        val tempMatrix = ColorMatrix().apply { setRotate(1, hueRotate.toFloat()) }
        hueMatrix.postConcat(tempMatrix)
        tempMatrix.setRotate(2, hueRotate.toFloat())
        hueMatrix.postConcat(tempMatrix)
        result.postConcat(hueMatrix)
    }

    return result
}

private fun applyVideoAdjustments(
    textureView: TextureView,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    videoSize: androidx.media3.common.VideoSize,
) {
    textureView.post {
        val transform = android.graphics.Matrix()
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        val videoWidth = videoSize.width.takeIf { it > 0 }?.toFloat()
        val videoHeight = videoSize.height.takeIf { it > 0 }?.toFloat()
        val pixelRatio = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f

        if (viewWidth > 0f && viewHeight > 0f && videoWidth != null && videoHeight != null) {
            val contentWidth = videoWidth * pixelRatio
            val contentHeight = videoHeight
            val widthRatio = contentWidth / viewWidth
            val heightRatio = contentHeight / viewHeight
            val fitScaleX: Float
            val fitScaleY: Float

            if (widthRatio > heightRatio) {
                fitScaleX = 1f
                fitScaleY = heightRatio / widthRatio
            } else {
                fitScaleX = widthRatio / heightRatio
                fitScaleY = 1f
            }

            when (settings.videoScaleMode.lowercase()) {
                "stretch" -> Unit
                "fill" -> {
                    val minFitScale = minOf(fitScaleX, fitScaleY).coerceAtLeast(0.0001f)
                    val fillScaleX = fitScaleX / minFitScale
                    val fillScaleY = fitScaleY / minFitScale
                    transform.setScale(fillScaleX, fillScaleY, viewWidth / 2f, viewHeight / 2f)
                }
                else -> {
                    transform.setScale(fitScaleX, fitScaleY, viewWidth / 2f, viewHeight / 2f)
                }
            }
        }
        textureView.setTransform(transform)
    }

    val matrix = buildVideoColorMatrix(settings)
    if (matrix == null) {
        textureView.setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        textureView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}

@Composable
private fun PlayerControls(
    player: ExoPlayer,
    title: String,
    episodeLabel: String?,
    readyState: PlayerState.Ready,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    selectedSubtitleLanguage: String?,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    subtitlesEnabled: Boolean = false,
    onToggleSubtitles: () -> Unit = {},
    onSelectSubtitle: (String) -> Unit,
    onDisableSubtitles: () -> Unit,
    onSetEnableAutoplay: (Boolean) -> Unit,
    onSetVideoBrightness: (Int) -> Unit,
    onSetVideoContrast: (Int) -> Unit,
    onSetVideoSaturation: (Int) -> Unit,
    onSetVideoHueRotate: (Int) -> Unit,
    onResetAdvancedColor: () -> Unit,
    onSetVolumeBoost: (Int) -> Unit,
    onSetVideoScaleMode: (String) -> Unit,
    onInfo: () -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var isDragging by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var menuPage by remember { mutableStateOf<PlayerMenuPage?>(null) }
    var tracksSnapshot by remember { mutableStateOf(player.currentTracks) }
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val menuOpen = menuPage != null

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(tracks: Tracks) { tracksSnapshot = tracks }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }
        }
        player.addListener(listener); onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0)
            if (!isDragging) positionMs = player.currentPosition.coerceAtLeast(0)
            delay(500)
        }
    }

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val qualityOptions = remember(tracksSnapshot) { collectQualityOptions(tracksSnapshot) }
    val audioOptions = remember(tracksSnapshot) { collectAudioOptions(tracksSnapshot) }
    val selectedQualityLabel = qualityOptions.firstOrNull { it.selected }?.label ?: "Auto"
    val selectedAudioLabel = audioOptions.firstOrNull { it.selected }?.label ?: "Default"

    LaunchedEffect(menuOpen) {
        if (menuOpen && !controlsVisible) {
            onControlsVisibilityChanged(true)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, menuOpen) {
        if (controlsVisible && isPlaying && !menuOpen) {
            delay(3000)
            if (!menuOpen) {
                onControlsVisibilityChanged(false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            if (!menuOpen) {
                onControlsVisibilityChanged(!controlsVisible)
            }
        }
    ) {
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
                        .padding(start = TOP_BAR_LEFT_PADDING, end = TOP_BAR_RIGHT_PADDING, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(TOP_BAR_ICON_SIZE))
                        }
                        Text("Back to home", color = Color.White.copy(0.7f), fontSize = 13.sp, modifier = Modifier.clickable(onClick = onBack))
                        Text("  /  ", color = Color.White.copy(0.35f), fontSize = 13.sp)
                        Column(modifier = Modifier.widthIn(max = 320.dp)) {
                            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (episodeLabel != null) {
                                Text(episodeLabel, color = Color.White.copy(0.62f), fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = onInfo, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(Icons.Default.Info, "Info", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(TOP_BAR_ICON_SIZE))
                        }
                        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(
                                if (isBookmarked) Icons.Filled.Check else Icons.Filled.BookmarkBorder,
                                "Bookmark",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(TOP_BAR_ICON_SIZE)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }

                Row(modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CENTER_ICON_SPACING)) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                        modifier = Modifier.size(CENTER_BUTTON_SIZE)) {
                        Icon(painterResource(R.drawable.ic_player_skip_back), null, tint = Color.White,
                            modifier = Modifier.height(CENTER_ICON_HEIGHT).wrapContentWidth())
                    }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(CENTER_BUTTON_SIZE)) {
                        Icon(painterResource(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                            null, tint = Color.White,
                            modifier = Modifier.height(CENTER_ICON_HEIGHT).wrapContentWidth())
                    }
                    IconButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(durationMs)) },
                        modifier = Modifier.size(CENTER_BUTTON_SIZE)) {
                        Icon(painterResource(R.drawable.ic_player_skip_fwd), null, tint = Color.White,
                            modifier = Modifier.height(CENTER_ICON_HEIGHT).wrapContentWidth())
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = SCRUBBER_SIDE_PADDING)
                            .height(28.dp)
                            .pointerInput(durationMs) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume() // prevent parent controls-toggle from firing
                                    val fraction = { x: Float -> (x / size.width).coerceIn(0f, 1f) }
                                    isDragging = true
                                    scrubPosition = fraction(down.position.x)
                                    drag(down.id) { change ->
                                        scrubPosition = fraction(change.position.x)
                                        change.consume()
                                    }
                                    player.seekTo((scrubPosition * durationMs).toLong())
                                    positionMs = (scrubPosition * durationMs).toLong()
                                    isDragging = false
                                }
                            }
                    ) {
                        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.Center).background(Color.White.copy(0.25f)))
                        Box(Modifier.fillMaxWidth(if (isDragging) scrubPosition else progress).height(3.dp).align(Alignment.CenterStart).background(RedProgress))
                        if (isDragging) {
                            val thumbFraction = scrubPosition
                            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(thumbFraction).wrapContentWidth(Alignment.End)) {
                                Box(Modifier.size(12.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.75f))
                        .padding(vertical = BOTTOM_BAR_PADDING_V),
                        verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(BOTTOM_LEFT_START_PADDING))
                        DrawableControlIcon(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        DrawableControlIcon(R.drawable.ic_player_skip_back) {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        }
                        DrawableControlIcon(R.drawable.ic_player_skip_fwd) {
                            player.seekTo((player.currentPosition + 10_000).coerceAtMost(durationMs))
                        }
                        DrawableControlIcon(if (isMuted) R.drawable.ic_player_mute else R.drawable.ic_player_volume) {
                            isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("${formatTime(positionMs)} / ${formatTime(durationMs)}", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            onControlsVisibilityChanged(true)
                            menuPage = PlayerMenuPage.Captions
                        }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                            Icon(Icons.Filled.ClosedCaption, null, tint = if (subtitlesEnabled) Color.White else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                        }
                        IconButton(onClick = {
                            onControlsVisibilityChanged(true)
                            menuPage = PlayerMenuPage.Root
                        }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                            Icon(Icons.Filled.Tune, null, tint = Color.White, modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                        }
                        IconButton(onClick = {
                            menuPage = null
                            onControlsVisibilityChanged(false)
                            onPip()
                        }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                            Icon(Icons.Filled.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                        }
                        Spacer(Modifier.width(BOTTOM_RIGHT_END_PADDING))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 86.dp)
        ) {
            Surface(
                color = Color(0xFF141414).copy(alpha = 0.96f),
                shape = OVERLAY_PANEL_SHAPE,
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
                modifier = Modifier
                    .widthIn(max = MENU_PANEL_WIDTH)
                    .heightIn(max = MENU_PANEL_HEIGHT)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), OVERLAY_PANEL_SHAPE)
            ) {
                PlayerMenuContent(
                    page = menuPage ?: PlayerMenuPage.Root,
                    title = title,
                    settings = settings,
                    sourceId = readyState.sourceId,
                    embedId = readyState.embedId,
                    sourceResults = readyState.sources,
                    selectedSubtitleLanguage = selectedSubtitleLanguage,
                    subtitlesEnabled = subtitlesEnabled,
                    subtitleTracks = readyState.subtitles,
                    playbackSpeed = playbackSpeed,
                    selectedQualityLabel = selectedQualityLabel,
                    selectedAudioLabel = selectedAudioLabel,
                    qualityOptions = qualityOptions,
                    audioOptions = audioOptions,
                    onClose = { menuPage = null },
                    onBack = {
                        menuPage = if (menuPage == PlayerMenuPage.Root) null else PlayerMenuPage.Root
                    },
                    onOpenPage = { menuPage = it },
                    onToggleSubtitles = onToggleSubtitles,
                    onDisableSubtitles = onDisableSubtitles,
                    onSelectSubtitle = onSelectSubtitle,
                    onSetEnableAutoplay = onSetEnableAutoplay,
                    onSetVideoBrightness = onSetVideoBrightness,
                    onSetVideoContrast = onSetVideoContrast,
                    onSetVideoSaturation = onSetVideoSaturation,
                            onSetVideoHueRotate = onSetVideoHueRotate,
                            onResetAdvancedColor = onResetAdvancedColor,
                            onSetVolumeBoost = onSetVolumeBoost,
                            onSetVideoScaleMode = onSetVideoScaleMode,
                            onSetPlaybackSpeed = { speed ->
                        player.playbackParameters = PlaybackParameters(speed)
                        playbackSpeed = speed
                    },
                    onSelectQuality = { option ->
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .addOverride(TrackSelectionOverride(option.group, option.trackIndex))
                            .build()
                    },
                    onSelectAutoQuality = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                    },
                    onSelectAudio = { option ->
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .addOverride(TrackSelectionOverride(option.group, option.trackIndex))
                            .setPreferredAudioLanguage(option.language)
                            .build()
                    },
                    onPip = onPip,
                )
            }
        }
    }
}

@Composable
private fun DrawableControlIcon(@DrawableRes res: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(BOTTOM_BAR_BUTTON_SIZE)) {
        Icon(painterResource(res), null, tint = Color.White, modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE))
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun advancedColorSummary(settings: com.zstream.android.data.local.entity.SettingsEntity): String {
    return if (
        settings.videoBrightness == 100 &&
        settings.videoContrast == 100 &&
        settings.videoSaturation == 100 &&
        settings.videoHueRotate == 0
    ) {
        "Default"
    } else {
        "Adjusted"
    }
}

private fun videoScaleModeLabel(mode: String): String = when (mode.lowercase()) {
    "fill" -> "Fill"
    "stretch" -> "Stretch"
    else -> "Fit"
}

@Composable
private fun PlayerMenuContent(
    page: PlayerMenuPage,
    title: String,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    sourceId: String?,
    embedId: String?,
    sourceResults: List<SourceResult>,
    selectedSubtitleLanguage: String?,
    subtitlesEnabled: Boolean,
    subtitleTracks: List<SubtitleTrack>,
    playbackSpeed: Float,
    selectedQualityLabel: String,
    selectedAudioLabel: String,
    qualityOptions: List<QualityOption>,
    audioOptions: List<AudioOption>,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onOpenPage: (PlayerMenuPage) -> Unit,
    onToggleSubtitles: () -> Unit,
    onDisableSubtitles: () -> Unit,
    onSelectSubtitle: (String) -> Unit,
    onSetEnableAutoplay: (Boolean) -> Unit,
    onSetVideoBrightness: (Int) -> Unit,
    onSetVideoContrast: (Int) -> Unit,
    onSetVideoSaturation: (Int) -> Unit,
    onSetVideoHueRotate: (Int) -> Unit,
    onResetAdvancedColor: () -> Unit,
    onSetVolumeBoost: (Int) -> Unit,
    onSetVideoScaleMode: (String) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSelectQuality: (QualityOption) -> Unit,
    onSelectAutoQuality: () -> Unit,
    onSelectAudio: (AudioOption) -> Unit,
    onPip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(18.dp)
            .widthIn(max = MENU_PANEL_WIDTH)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != PlayerMenuPage.Root) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = when (page) {
                    PlayerMenuPage.Root -> "Player Settings"
                    PlayerMenuPage.Captions -> "Subtitles"
                    PlayerMenuPage.Playback -> "Playback"
                    PlayerMenuPage.AdvancedColor -> "Advanced Color"
                    PlayerMenuPage.VideoMode -> "Video Mode"
                    PlayerMenuPage.Source -> "Source"
                    PlayerMenuPage.Quality -> "Quality"
                    PlayerMenuPage.Audio -> "Audio"
                    PlayerMenuPage.Download -> "Download"
                    PlayerMenuPage.WatchParty -> "Watch Party"
                    PlayerMenuPage.SkipSegments -> "Skip Segments"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(12.dp))

        when (page) {
            PlayerMenuPage.Root -> {
                PlayerMenuSummaryCard("Now Playing", title, "Media controls mirror the frontend shell.")
                Spacer(Modifier.height(10.dp))
                PlayerMenuNavRow(Icons.Filled.ClosedCaption, "Subtitles", selectedSubtitleLanguage ?: if (subtitlesEnabled) "On" else "Off") { onOpenPage(PlayerMenuPage.Captions) }
                PlayerMenuNavRow(Icons.Filled.Speed, "Playback", "${playbackSpeed}x") { onOpenPage(PlayerMenuPage.Playback) }
                PlayerMenuNavRow(Icons.Filled.Tune, "Source", sourceId ?: "Auto selected") { onOpenPage(PlayerMenuPage.Source) }
                PlayerMenuNavRow(Icons.Filled.Settings, "Quality", selectedQualityLabel) { onOpenPage(PlayerMenuPage.Quality) }
                PlayerMenuNavRow(Icons.Filled.VolumeUp, "Audio", selectedAudioLabel) { onOpenPage(PlayerMenuPage.Audio) }
                PlayerMenuNavRow(Icons.Filled.Download, "Download", "Stub") { onOpenPage(PlayerMenuPage.Download) }
                PlayerMenuNavRow(Icons.Filled.PictureInPictureAlt, "Watch Party", "Stub") { onOpenPage(PlayerMenuPage.WatchParty) }
                PlayerMenuNavRow(Icons.Filled.Tune, "Skip Segments", "Stub") { onOpenPage(PlayerMenuPage.SkipSegments) }
            }
            PlayerMenuPage.Captions -> {
                PlayerMenuToggleRow("Enable subtitles", subtitlesEnabled, onToggleSubtitles)
                Spacer(Modifier.height(10.dp))
                PlayerMenuActionRow("Off", selected = !subtitlesEnabled || selectedSubtitleLanguage == null) {
                    onDisableSubtitles()
                }
                subtitleTracks.forEach { track ->
                    PlayerMenuActionRow(
                        title = track.label.ifBlank { track.language },
                        subtitle = track.type.uppercase(),
                        selected = subtitlesEnabled && selectedSubtitleLanguage == track.language
                    ) {
                        onSelectSubtitle(track.language)
                    }
                }
                if (subtitleTracks.isEmpty()) {
                    PlayerMenuStubCard("No subtitles were returned for this stream.")
                }
            }
            PlayerMenuPage.Playback -> {
                Text("Speed", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        FilterChip(
                            selected = playbackSpeed == speed,
                            onClick = { onSetPlaybackSpeed(speed) },
                            label = { Text("${speed}x") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PlayerMenuSliderRow(
                    label = "Custom speed",
                    value = playbackSpeed,
                    valueText = "${String.format("%.2f", playbackSpeed)}x",
                    range = PLAYBACK_SPEED_MIN..PLAYBACK_SPEED_MAX,
                    steps = 94,
                    onValueChange = { onSetPlaybackSpeed((it * 20).toInt() / 20f) },
                    onReset = { onSetPlaybackSpeed(1f) },
                    isDefault = playbackSpeed == 1f
                )
                Spacer(Modifier.height(14.dp))
                PlayerMenuToggleRow("Autoplay next episode", settings.enableAutoplay) {
                    onSetEnableAutoplay(!settings.enableAutoplay)
                }
                Spacer(Modifier.height(10.dp))
                PlayerMenuSliderRow(
                    label = "Brightness",
                    value = settings.videoBrightness.toFloat(),
                    valueText = "${settings.videoBrightness}%",
                    range = 10f..200f,
                    steps = 37,
                    onValueChange = { onSetVideoBrightness((it / 5).toInt() * 5) },
                    onReset = { onSetVideoBrightness(100) },
                    isDefault = settings.videoBrightness == 100
                )
                Spacer(Modifier.height(10.dp))
                PlayerMenuToggleRow("Volume Boost", settings.volumeBoost > 100) {
                    onSetVolumeBoost(if (settings.volumeBoost > 100) 100 else 150)
                }
                if (settings.volumeBoost > 100) {
                    Spacer(Modifier.height(10.dp))
                    PlayerMenuSliderRow(
                        label = "Boost level",
                        value = settings.volumeBoost.toFloat(),
                        valueText = "${settings.volumeBoost}%",
                        range = 100f..300f,
                        steps = 19,
                        onValueChange = { onSetVolumeBoost((it / 10).toInt() * 10) },
                        onReset = { onSetVolumeBoost(100) },
                        isDefault = settings.volumeBoost == 100
                    )
                }
                Spacer(Modifier.height(10.dp))
                PlayerMenuNavRow(Icons.Filled.Tune, "Advanced Color", advancedColorSummary(settings)) {
                    onOpenPage(PlayerMenuPage.AdvancedColor)
                }
                Spacer(Modifier.height(10.dp))
                PlayerMenuNavRow(Icons.Filled.PictureInPictureAlt, "Video Mode", videoScaleModeLabel(settings.videoScaleMode)) {
                    onOpenPage(PlayerMenuPage.VideoMode)
                }
                Spacer(Modifier.height(10.dp))
                PlayerMenuActionRow("Picture in Picture", subtitle = "Use native Android PiP flow") {
                    onClose()
                    onPip()
                }
            }
            PlayerMenuPage.AdvancedColor -> {
                PlayerMenuSliderRow(
                    label = "Brightness",
                    value = settings.videoBrightness.toFloat(),
                    valueText = "${settings.videoBrightness}%",
                    range = 10f..200f,
                    steps = 37,
                    onValueChange = { onSetVideoBrightness((it / 5).toInt() * 5) },
                    onReset = { onSetVideoBrightness(100) },
                    isDefault = settings.videoBrightness == 100
                )
                Spacer(Modifier.height(10.dp))
                PlayerMenuSliderRow(
                    label = "Contrast",
                    value = settings.videoContrast.toFloat(),
                    valueText = "${settings.videoContrast}%",
                    range = 50f..200f,
                    steps = 29,
                    onValueChange = { onSetVideoContrast((it / 5).toInt() * 5) },
                    onReset = { onSetVideoContrast(100) },
                    isDefault = settings.videoContrast == 100
                )
                Spacer(Modifier.height(10.dp))
                PlayerMenuSliderRow(
                    label = "Saturation",
                    value = settings.videoSaturation.toFloat(),
                    valueText = "${settings.videoSaturation}%",
                    range = 0f..200f,
                    steps = 39,
                    onValueChange = { onSetVideoSaturation((it / 5).toInt() * 5) },
                    onReset = { onSetVideoSaturation(100) },
                    isDefault = settings.videoSaturation == 100
                )
                Spacer(Modifier.height(10.dp))
                PlayerMenuSliderRow(
                    label = "Hue",
                    value = settings.videoHueRotate.toFloat(),
                    valueText = "${settings.videoHueRotate}°",
                    range = -180f..180f,
                    steps = 71,
                    onValueChange = { onSetVideoHueRotate((it / 5).toInt() * 5) },
                    onReset = { onSetVideoHueRotate(0) },
                    isDefault = settings.videoHueRotate == 0
                )
                Spacer(Modifier.height(10.dp))
                PlayerMenuActionRow("Reset all color adjustments", selected = false, onClick = onResetAdvancedColor)
                PlayerMenuStubCard("Color adjustments persist locally and apply to the active player surface in real time.")
            }
            PlayerMenuPage.VideoMode -> {
                listOf(
                    "fit" to "Fit",
                    "fill" to "Fill",
                    "stretch" to "Stretch",
                ).forEach { (value, label) ->
                    PlayerMenuActionRow(
                        title = label,
                        subtitle = when (value) {
                            "fit" -> "Preserve aspect ratio with black bars if needed"
                            "fill" -> "Zoom to fill the screen and crop overflow"
                            else -> "Stretch to fill the screen"
                        },
                        selected = settings.videoScaleMode == value
                    ) {
                        onSetVideoScaleMode(value)
                    }
                }
            }
            PlayerMenuPage.Source -> {
                PlayerMenuSummaryCard("Selected Source", sourceId ?: "Unknown", embedId ?: "No embed metadata")
                Spacer(Modifier.height(10.dp))
                sourceResults.forEach { source ->
                    PlayerMenuSourceRow(source)
                }
                if (sourceResults.isEmpty()) {
                    PlayerMenuStubCard("Manual source switching is not wired yet. Current provider metadata is ready for it.")
                }
            }
            PlayerMenuPage.Quality -> {
                PlayerMenuActionRow("Auto", subtitle = "Adaptive streaming", selected = qualityOptions.none { it.selected }) {
                    onSelectAutoQuality()
                }
                qualityOptions.forEach { option ->
                    PlayerMenuActionRow(
                        title = option.label,
                        subtitle = "${option.height}p",
                        selected = option.selected
                    ) {
                        onSelectQuality(option)
                    }
                }
                if (qualityOptions.isEmpty()) {
                    PlayerMenuStubCard("No manual quality tracks exposed by Media3 for this stream.")
                }
            }
            PlayerMenuPage.Audio -> {
                audioOptions.forEach { option ->
                    PlayerMenuActionRow(
                        title = option.label,
                        subtitle = option.language ?: "Unknown",
                        selected = option.selected
                    ) {
                        onSelectAudio(option)
                    }
                }
                if (audioOptions.isEmpty()) {
                    PlayerMenuStubCard("No selectable audio tracks exposed by Media3 for this stream.")
                }
            }
            PlayerMenuPage.Download -> PlayerMenuStubCard("Download UI is prepared. Source-specific download API wiring is still stubbed.")
            PlayerMenuPage.WatchParty -> PlayerMenuStubCard("Watch party surface is prepared. Session APIs and state sync are still stubbed.")
            PlayerMenuPage.SkipSegments -> PlayerMenuStubCard("Skip segment controls are prepared. Segment metadata and auto-skip APIs are still stubbed.")
        }
    }
}

@Composable
private fun PlayerMenuSummaryCard(title: String, value: String, subtitle: String) {
    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerMenuNavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerMenuToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun PlayerMenuSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    isDefault: Boolean,
) {
    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(valueText, color = Color.White, fontSize = 13.sp)
                if (!isDefault) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Reset", fontSize = 11.sp)
                    }
                }
            }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                )
            )
        }
    }
}

@Composable
private fun PlayerMenuActionRow(title: String, subtitle: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp)
                }
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PlayerMenuStubCard(message: String) {
    Surface(color = Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(message, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PlayerMenuSourceRow(source: SourceResult) {
    val color = when (source.status) {
        SourceStatus.SUCCESS -> Color(0xFF4ADE80)
        SourceStatus.FAILED -> Color(0xFFF87171)
        SourceStatus.TRYING -> Color(0xFFFBBF24)
    }
    Surface(color = Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(source.id, color = Color.White, modifier = Modifier.weight(1f))
            Text(source.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerInfoSheet(
    state: PlayerInfoState?,
    nav: NavController,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onClose: () -> Unit,
    onSelectSeason: (Int) -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalZStreamTheme.current
    when (state) {
        null, PlayerInfoState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        is PlayerInfoState.Error -> Box(Modifier.fillMaxSize().padding(PLAYER_DETAIL_SHEET_CONTENT_PADDING), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = Color.White)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        is PlayerInfoState.Movie -> {
            PlayerInfoSheetScaffold(
                title = state.detail.title,
                backdropUrl = state.detail.backdropUrl(),
                logoUrl = state.detail.logoUrl(),
                posterUrl = state.detail.posterUrl(),
                year = state.detail.releaseDate?.take(4),
                rating = state.detail.voteAverage?.let { String.format("%.1f", it) },
                theme = theme,
                onClose = onClose
            ) {
                PlayerMovieInfoContent(
                    detail = state.detail,
                    context = context,
                    nav = nav,
                    theme = theme,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                )
            }
        }
        is PlayerInfoState.Tv -> {
            PlayerInfoSheetScaffold(
                title = state.detail.name,
                backdropUrl = state.detail.backdropUrl(),
                logoUrl = state.detail.logoUrl(),
                posterUrl = state.detail.posterUrl(),
                year = state.detail.firstAirDate?.take(4),
                rating = state.detail.voteAverage?.let { String.format("%.1f", it) },
                theme = theme,
                onClose = onClose
            ) {
                PlayerTvInfoContent(
                    detail = state.detail,
                    selectedSeason = state.selectedSeason,
                    context = context,
                    nav = nav,
                    theme = theme,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    onSelectSeason = onSelectSeason,
                )
            }
        }
    }
}

@Composable
private fun PlayerInfoSheetScaffold(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    posterUrl: String?,
    year: String?,
    rating: String?,
    theme: ZStreamTheme,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                color = theme.colors.background.main,
                shape = RoundedCornerShape(PLAYER_DETAIL_SHEET_CORNER_RADIUS),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = this@BoxWithConstraints.maxHeight + PLAYER_DETAIL_SHEET_MIN_SCROLL_EXTRA)
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.2f), RoundedCornerShape(PLAYER_DETAIL_SHEET_CORNER_RADIUS))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PLAYER_DETAIL_SHEET_BACKDROP_HEIGHT)
                    ) {
                        AsyncImage(
                            model = backdropUrl ?: posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.18f),
                                            theme.colors.background.main
                                        )
                                    )
                                )
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(start = 24.dp, end = 24.dp, top = 48.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, null, tint = Color.White)
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = PLAYER_DETAIL_SHEET_CONTENT_PADDING, vertical = 0.dp)
                        ) {
                            if (logoUrl != null) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = title,
                                    modifier = Modifier
                                        .height(100.dp)
                                        .fillMaxWidth(0.6f),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                Text(
                                    title.uppercase(),
                                    color = theme.colors.type.emphasis,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 4.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            MetadataRow(
                                content = listOfNotNull(
                                    rating?.toDoubleOrNull()?.let { numericRating -> { TmdbRating(numericRating, theme) } },
                                    year?.let { { Text(it, fontSize = 12.sp, color = Color.White) } }
                                ),
                                theme = theme,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Alignment.CenterVertically
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    ) {
                        content()
                        Spacer(Modifier.height(PLAYER_DETAIL_SHEET_BOTTOM_SPACER))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PlayerMovieInfoContent(
    detail: MovieDetail,
    context: android.content.Context,
    nav: NavController,
    theme: ZStreamTheme,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val genres = detail.genres.orEmpty()
    val cast = detail.credits?.cast.orEmpty().take(8)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerSheetActionPill(Icons.Filled.Share, theme) {
            openPlayerInfoShareSheet(context, detail.title, detail.id, "movie")
        }
        PlayerSheetActionPill(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, theme) {
            onToggleBookmark()
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { PlayerSheetGenreChip(it.name, theme) }
            }
        }
        Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerSheetDetailSpec("Runtime", detail.runtime?.let { "$it min" } ?: "-", theme)
            PlayerSheetDetailSpec("Language", "EN", theme)
            PlayerSheetDetailSpec("Release Date", detail.releaseDate ?: "-", theme)
            PlayerSheetDetailSpec("Rating", "PG-13", theme)
        }
    }

    PlayerSheetSectionHeader("Cast", theme)
    PlayerSheetCastRow(cast, theme, context)
    PlayerSheetSectionHeader("Trailers", theme)
    PlayerSheetTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    PlayerSheetSectionHeader("Similar", theme)
    PlayerSheetSimilarGrid(detail.similar?.results.orEmpty(), theme, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PlayerTvInfoContent(
    detail: TvDetail,
    selectedSeason: Season?,
    context: android.content.Context,
    nav: NavController,
    theme: ZStreamTheme,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onSelectSeason: (Int) -> Unit,
) {
    val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }

    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlayerSheetActionPill(Icons.Filled.Share, theme) {
            openPlayerInfoShareSheet(context, detail.name, detail.id, "tv")
        }
        PlayerSheetActionPill(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, theme) {
            onToggleBookmark()
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.genres.orEmpty().forEach { PlayerSheetGenreChip(it.name, theme) }
            }
        }
        Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerSheetDetailSpec("Seasons", detail.numberOfSeasons?.toString() ?: "—", theme)
            PlayerSheetDetailSpec("Language", "EN", theme)
            PlayerSheetDetailSpec("Release Date", detail.firstAirDate ?: "—", theme)
            PlayerSheetDetailSpec("Rating", "TV-14", theme)
        }
    }

    PlayerSheetSectionHeader("Seasons", theme)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 32.dp)) {
        items(seasons) { season ->
            FilterChip(
                selected = season.seasonNumber == selectedSeason?.seasonNumber,
                onClick = { onSelectSeason(season.seasonNumber) },
                label = { Text("S${season.seasonNumber}") }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    selectedSeason?.episodes?.let { episodes ->
        PlayerSheetSectionHeader("Episodes", theme)
        episodes.forEach { episode ->
            PlayerSheetEpisodeRow(episode, theme)
        }
    }

    PlayerSheetSectionHeader("Cast", theme)
    PlayerSheetCastRow(detail.credits?.cast.orEmpty().take(8), theme, context)
    PlayerSheetSectionHeader("Trailers", theme)
    PlayerSheetTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    PlayerSheetSectionHeader("Similar", theme)
    PlayerSheetSimilarGrid(detail.similar?.results.orEmpty(), theme, nav)
}

@Composable
private fun PlayerSheetCastRow(cast: List<com.zstream.android.data.model.CastMember>, theme: ZStreamTheme, context: android.content.Context) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
        items(cast) { member ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(105.dp).clickable {
                openPlayerInfoCastProfile(context, member.id, member.externalIds?.imdbId)
            }) {
                AsyncImage(
                    model = member.profilePath?.let { "${Urls.TMDB_IMAGE}w185$it" },
                    contentDescription = member.name,
                    modifier = Modifier.size(96.dp).clip(CircleShape).border(1.dp, theme.colors.type.text.copy(alpha = 0.08f), CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Spacer(Modifier.height(10.dp))
                Text(member.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                Text(member.character.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
            }
        }
    }
}

@Composable
private fun PlayerSheetSectionHeader(title: String, theme: ZStreamTheme) {
    Text(title, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 0.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = theme.colors.type.emphasis)
}

@Composable
private fun PlayerSheetDetailSpec(label: String, value: String, theme: ZStreamTheme) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = theme.colors.type.emphasis)
    }
}

@Composable
private fun PlayerSheetGenreChip(label: String, theme: ZStreamTheme) {
    Surface(
        color = theme.colors.type.text.copy(alpha = 0.08f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.15f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = theme.colors.type.text)
    }
}

@Composable
private fun MetadataRow(
    content: List<@Composable () -> Unit>,
    theme: ZStreamTheme,
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Alignment.Vertical
) {
    Row(horizontalArrangement = horizontalArrangement, verticalAlignment = verticalArrangement) {
        content.forEachIndexed { index, contentItem ->
            if (index > 0) Text("•", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
            contentItem()
        }
    }
}

@Composable
private fun TmdbRating(rating: Double, theme: ZStreamTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.tmdb_logo),
            contentDescription = "TMDB Logo",
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "%.1f".format(rating), color = theme.colors.type.emphasis, fontSize = 12.sp)
    }
}

@Composable
private fun PlayerSheetActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: ZStreamTheme,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = theme.colors.type.text.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = theme.colors.type.text)
        }
    }
}

@Composable
private fun PlayerSheetTrailerGrid(trailers: List<com.zstream.android.data.model.TrailerData>, theme: ZStreamTheme, context: android.content.Context) {
    if (trailers.isEmpty()) {
        Text("No trailers available", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), color = theme.colors.type.secondary)
        return
    }

    Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trailers.take(3)) { trailer ->
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.modal.background)
                        .clickable { openPlayerInfoYoutubeTrailer(context, trailer.key) }
                ) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${trailer.key}/0.jpg",
                        contentDescription = trailer.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(trailer.name, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerSheetSimilarGrid(similar: List<com.zstream.android.data.model.Media>, theme: ZStreamTheme, nav: NavController) {
    if (similar.isEmpty()) {
        Text("No similar movies available", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), color = theme.colors.type.secondary)
        return
    }

    Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(similar.take(10)) { movie ->
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable {
                            val mediaType = movie.type
                            nav.navigate("detail/$mediaType/${movie.id}")
                        }
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(8.dp)).background(theme.colors.modal.background)) {
                        movie.posterUrl()?.let {
                            AsyncImage(model = it, contentDescription = movie.displayTitle, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(movie.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                    val year = (movie.releaseDate ?: movie.firstAirDate)?.take(4) ?: "—"
                    val capitalizedMovie = movie.type.replaceFirstChar { it.uppercase() }
                    Text("$capitalizedMovie • $year", style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerSheetEpisodeRow(episode: com.zstream.android.data.model.Episode, theme: ZStreamTheme) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.modal.background)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = episode.stillPath?.let { "${Urls.TMDB_IMAGE}w780$it" },
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2D2D3D))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "E${episode.episodeNumber}",
                        color = Color(0xFFC4C4D4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(
                    episode.name.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = theme.colors.type.emphasis,
                    fontWeight = FontWeight.Medium
                )

                episode.overview?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(Modifier.height(4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { expanded = !expanded }
                    ) {
                        Text(
                            desc,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.colors.type.text
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun openPlayerInfoShareSheet(context: android.content.Context, title: String, id: Int, mediaType: String) {
    val url = "https://www.themoviedb.org/$mediaType/$id"
    val shareText = "$title on ZStream!\n\n$url"
    val intent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share"))
}

private fun openPlayerInfoYoutubeTrailer(context: android.content.Context, youtubeKey: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")
    )
    context.startActivity(intent)
}

private fun openPlayerInfoCastProfile(context: android.content.Context, castId: Int, imdbId: String?) {
    val url = if (!imdbId.isNullOrEmpty()) {
        "https://www.imdb.com/name/$imdbId/"
    } else {
        "https://www.themoviedb.org/person/$castId"
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

private fun collectQualityOptions(tracks: Tracks): List<QualityOption> {
    return tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            buildList {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val height = group.getTrackFormat(i).height
                    if (height == Format.NO_VALUE) continue
                    add(
                        QualityOption(
                            label = when {
                                height >= 2000 -> "4K"
                                height >= 1080 -> "1080p"
                                height >= 720 -> "720p"
                                height >= 480 -> "480p"
                                else -> "${height}p"
                            },
                            height = height,
                            group = group.mediaTrackGroup,
                            trackIndex = i,
                            selected = group.isTrackSelected(i),
                        )
                    )
                }
            }
        }
        .distinctBy { "${it.group.id}-${it.height}-${it.trackIndex}" }
        .sortedByDescending { it.height }
}

private fun collectAudioOptions(tracks: Tracks): List<AudioOption> {
    return tracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            buildList {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    add(
                        AudioOption(
                            label = format.label ?: format.language ?: "Track ${i + 1}",
                            language = format.language,
                            group = group.mediaTrackGroup,
                            trackIndex = i,
                            selected = group.isTrackSelected(i),
                        )
                    )
                }
            }
        }
        .distinctBy { "${it.group.id}-${it.trackIndex}" }
}
