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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val RedProgress = Color(0xFFE53935)

// ── Layout constants ──────────────────────────────────────────────────────────
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
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaSource(HlsMediaSource.Factory(WebViewDataSource.Factory(vm.getProxyPort()))
                            .createMediaSource(MediaItem.fromUri(s.streamUrl)))
                        prepare(); playWhenReady = true
                    }
                }
                DisposableEffect(Unit) { onDispose { player.release() } }

                AndroidView(factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player; useController = false
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                }, modifier = Modifier.fillMaxSize())

                PlayerControls(player, vm.title, onBack = { nav.popBackStack() }, onPip = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                })
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

                // ── Bottom bar — scrubber flush against controls row ──────
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    // Scrubber with horizontal padding, zero vertical spacing
                    Box(Modifier.fillMaxWidth().padding(horizontal = SCRUBBER_SIDE_PADDING)) {
                        LinearProgressIndicator(
                            progress = { if (isDragging) scrubPosition else progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                            color = RedProgress, trackColor = Color.White.copy(0.25f),
                        )
                        Slider(
                            value = if (isDragging) scrubPosition else progress,
                            onValueChange = { v -> isDragging = true; scrubPosition = v },
                            onValueChangeFinished = {
                                player.seekTo((scrubPosition * durationMs).toLong())
                                positionMs = (scrubPosition * durationMs).toLong()
                                isDragging = false
                            },
                            modifier = Modifier.fillMaxWidth().offset(y = SCRUBBER_SLIDER_OFFSET),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                        )
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
