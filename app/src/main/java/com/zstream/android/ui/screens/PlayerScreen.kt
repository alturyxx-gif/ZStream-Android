package com.zstream.android.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.provider.WebViewDataSource
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
                    Button(onClick = vm::load) { Text("Retry") }
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
                // shouldShowProgress: watched >= 20s AND not within 120s of end
                val shouldResume = remember(resumeWatched, resumeDuration) {
                    resumeWatched >= 20 && (resumeDuration - resumeWatched) >= 120
                }
                var resumeHandled by remember { mutableStateOf(false) }
                var showResumeDialog by remember { mutableStateOf(false) }
                var pendingResumeMs by remember { mutableLongStateOf(-1L) }

                // One-time snapshot of initial progress — prevents dialog appearing mid-playback
                // when the first sync creates a progress entry during a new watch
                var initialResumeCheckDone by remember { mutableStateOf(false) }
                var initialShouldResume by remember { mutableStateOf(false) }
                if (!initialResumeCheckDone && progressList.isNotEmpty()) {
                    initialResumeCheckDone = true
                    val found = progressList.firstOrNull { p ->
                        p.tmdbId == vm.tmdbId && (vm.episodeId == null || p.episodeId == vm.episodeId)
                    }
                    val w = found?.watched?.toLong() ?: 0L
                    val d = found?.duration?.toLong() ?: 0L
                    initialShouldResume = w >= 20 && (d <= 0 || (d - w) >= 120)
                }

                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaSource(HlsMediaSource.Factory(WebViewDataSource.Factory(vm.getProxyPort()))
                            .createMediaSource(MediaItem.fromUri(s.streamUrl)))
                        videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                        prepare()
                        playWhenReady = true  // always start loading immediately
                    }
                }

                // Show resume dialog based on initial snapshot only
                LaunchedEffect(initialShouldResume) {
                    if (initialShouldResume && !resumeHandled) showResumeDialog = true
                }

                var subtitleCues by remember { mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList()) }

                // Seek to resume position once player is ready
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && pendingResumeMs >= 0) {
                                player.seekTo(pendingResumeMs)
                                pendingResumeMs = -1L
                            }
                        }

                        override fun onCues(cues: List<androidx.media3.common.text.Cue>) {
                            subtitleCues = cues
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

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            if (!settings.enableNativeSubtitles) {
                                subtitleView?.visibility = android.view.View.GONE
                            }
                            playerViewRef.value = this
                        }
                    },
                    update = { view ->
                        view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        view.subtitleView?.visibility = if (settings.enableNativeSubtitles) android.view.View.VISIBLE else android.view.View.GONE
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Custom Subtitle Overlay
                if (!settings.enableNativeSubtitles && subtitleCues.isNotEmpty()) {
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
                            .padding(bottom = (48 + settings.subtitleVerticalPosition * 6f).dp)
                            .padding(horizontal = 48.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subtitleCues.forEach { cue ->
                                cue.text?.let { cueText ->
                                    Box(
                                        Modifier
                                            .background(
                                                Color.Black.copy(alpha = bgAlpha),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = cueText.toString(),
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
                }

                PlayerControls(player, vm.title, onBack = { nav.popBackStack() }, onPip = {
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
                            TextButton(onClick = {
                                // If already ready, seek now; otherwise queue for STATE_READY
                                if (player.playbackState == Player.STATE_READY) {
                                    player.seekTo(resumeWatched * 1000)
                                } else {
                                    pendingResumeMs = resumeWatched * 1000
                                }
                                showResumeDialog = false
                                resumeHandled = true
                            }) { Text("Resume", color = Color(0xFF7C3AED)) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showResumeDialog = false
                                resumeHandled = true
                            }) { Text("Restart", color = Color.White.copy(alpha = 0.6f)) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(player: ExoPlayer, title: String, onBack: () -> Unit, onPip: () -> Unit) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener); onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0)
            if (!isDragging) positionMs = player.currentPosition.coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3000); controlsVisible = false }
    }

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Box(modifier = Modifier.fillMaxSize()
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            controlsVisible = !controlsVisible
        }
    ) {
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {

                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
                        .padding(start = TOP_BAR_LEFT_PADDING, end = TOP_BAR_RIGHT_PADDING, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Smaller hit target for top bar buttons
                        IconButton(onClick = onBack, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(TOP_BAR_ICON_SIZE))
                        }
                        Text("Back to home", color = Color.White.copy(0.7f), fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onBack))
                        Text("  /  ", color = Color.White.copy(0.35f), fontSize = 13.sp)
                        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                        IconButton(onClick = {}, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(Icons.Default.Info, "Info", tint = Color.White.copy(0.8f), modifier = Modifier.size(TOP_BAR_ICON_SIZE))
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(TOP_BAR_BUTTON_SIZE)) {
                            Icon(painterResource(R.drawable.ic_player_bookmark_outline), "Bookmark",
                                tint = Color.White.copy(0.8f),
                                modifier = Modifier.height(TOP_BAR_ICON_SIZE).wrapContentWidth())
                        }
                    }
                    Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        coil.compose.AsyncImage(model = R.mipmap.ic_launcher, contentDescription = null,
                            modifier = Modifier.size(20.dp).clip(CircleShape))
                        Text("Z-Stream", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

                //  Bottom bar — scrubber flush against controls row 
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    // Scrubber with horizontal padding, zero vertical spacing
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
                        // Track background
                        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.Center)
                            .background(Color.White.copy(0.25f)))
                        // Filled portion
                        Box(Modifier.fillMaxWidth(if (isDragging) scrubPosition else progress)
                            .height(3.dp).align(Alignment.CenterStart)
                            .background(RedProgress))
                        // Thumb — only visible while dragging
                        if (isDragging) {
                            val thumbFraction = scrubPosition
                            Box(Modifier.align(Alignment.CenterStart)
                                .fillMaxWidth(thumbFraction)
                                .wrapContentWidth(Alignment.End)) {
                                Box(Modifier.size(12.dp)
                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape))
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
                        DrawableControlIcon(R.drawable.ic_player_captions) {}
                        DrawableControlIcon(R.drawable.ic_player_expand) {}
                        Spacer(Modifier.width(BOTTOM_RIGHT_END_PADDING))
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
