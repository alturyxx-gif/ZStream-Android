package com.zstream.android.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.util.TypedValue
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
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

private enum class PlayerMenuPage {
    Root, Captions, Playback, Source, Quality, Audio, Download, WatchParty, SkipSegments
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
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            val pv = playerViewRef.value ?: return
                            pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            // Fix emulator goldfish codec forcing VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING.
                            // Find the TextureView and reset its transform to identity so it doesn't crop.
                            fun findTextureView(v: android.view.View): android.graphics.SurfaceTexture? {
                                if (v is android.view.TextureView) {
                                    v.setTransform(android.graphics.Matrix())
                                    return v.surfaceTexture
                                }
                                if (v is android.view.ViewGroup) {
                                    for (i in 0 until v.childCount) findTextureView(v.getChildAt(i))
                                }
                                return null
                            }
                            pv.post { findTextureView(pv) }
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                var controlsVisible by remember { mutableStateOf(true) }

                // Automatically hide controls after 3s of playing
                LaunchedEffect(controlsVisible, player.isPlaying) {
                    if (controlsVisible && player.isPlaying) { delay(3000); controlsVisible = false }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            applyNativeSubtitleStyle(subtitleView, settings, controlsVisible)
                            playerViewRef.value = this
                        }
                    },
                    update = { view ->
                        view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        applyNativeSubtitleStyle(view.subtitleView, settings, controlsVisible)
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
                    val controlsBottom = if (controlsVisible) 80.dp else 0.dp
                    Log.d("PlayerScreen", "rendering ${visibleCues.size} cues at ${currentPositionMs}ms, " +
                        "color=${settings.subtitleColor} size=${settings.subtitleSize} " +
                        "fontStyle=${settings.subtitleFontStyle} bgOpacity=${settings.subtitleBackgroundOpacity} " +
                        "bold=${settings.subtitleBold}")
                    val textColor = Color(android.graphics.Color.parseColor(settings.subtitleColor))
                    val bgAlpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f)
                    val fontSize = (settings.subtitleSize * 18).sp
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
                            .padding(bottom = (48 + settings.subtitleVerticalPosition * 6f).dp + controlsBottom)
                            .padding(horizontal = 48.dp),
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
                    onBack = { nav.popBackStack() },
                    onPip = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                })

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

private fun applyNativeSubtitleStyle(
    subtitleView: androidx.media3.ui.SubtitleView?,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    controlsVisible: Boolean,
) {
    if (subtitleView == null) return

    subtitleView.visibility = if (settings.enableNativeSubtitles) android.view.View.VISIBLE else android.view.View.GONE
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setUserDefaultStyle()
    subtitleView.setUserDefaultTextSize()
    subtitleView.setBottomPaddingFraction(androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    val baseOffsetPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        NATIVE_SUBTITLE_BASE_OFFSET_DP,
        Resources.getSystem().displayMetrics,
    )
    subtitleView.translationY = if (controlsVisible) -baseOffsetPx * NATIVE_SUBTITLE_OVERLAY_MULTIPLIER else -baseOffsetPx
}

@Composable
private fun PlayerControls(
    player: ExoPlayer,
    title: String,
    episodeLabel: String?,
    readyState: PlayerState.Ready,
    selectedSubtitleLanguage: String?,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    subtitlesEnabled: Boolean = false,
    onToggleSubtitles: () -> Unit = {},
    onSelectSubtitle: (String) -> Unit,
    onDisableSubtitles: () -> Unit,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                    }
                    Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(0.55f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { /* info stub */ }, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
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
                        IconButton(onClick = { menuPage = PlayerMenuPage.Root }, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(Icons.Filled.Settings, "Settings", tint = Color.White, modifier = Modifier.size(TOP_BAR_ICON_SIZE))
                        }
                    }
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
                        IconButton(onClick = { menuPage = PlayerMenuPage.Captions }, modifier = Modifier.size(BOTTOM_BAR_BUTTON_SIZE)) {
                            Icon(Icons.Filled.ClosedCaption, null, tint = if (subtitlesEnabled) Color.White else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE))
                        }
                        IconButton(onClick = { menuPage = PlayerMenuPage.Root }, modifier = Modifier.size(BOTTOM_BAR_BUTTON_SIZE)) {
                            Icon(Icons.Filled.Tune, null, tint = Color.White, modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE))
                        }
                        DrawableControlIcon(R.drawable.ic_player_expand) {
                            onPip()}
                        Spacer(Modifier.width(BOTTOM_RIGHT_END_PADDING))
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

@Composable
private fun PlayerMenuContent(
    page: PlayerMenuPage,
    title: String,
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
                Spacer(Modifier.height(14.dp))
                PlayerMenuActionRow("Picture in Picture", subtitle = "Use native Android PiP flow") { onPip() }
                PlayerMenuStubCard("Brightness, volume boost, and advanced playback preferences are not wired yet.")
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
