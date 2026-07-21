package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.data.model.ShortItem
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant

private const val PLAYER_POOL_SIZE = 3
private const val MAX_ERROR_RETRIES = 2
private const val PREFETCH_AHEAD = 3

@Composable
fun ShortsScreen(nav: NavController, vm: ShortsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { state.items.size })

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
            .build()
    }
    val retryCounts = remember { mutableMapOf<ExoPlayer, Int>() }
    val players = remember {
        List(PLAYER_POOL_SIZE) { index ->
            ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("ShortsPlayer", "player[$index] error", error)
                        val retries = retryCounts.getOrDefault(this@apply, 0)
                        if (retries < MAX_ERROR_RETRIES) {
                            retryCounts[this@apply] = retries + 1
                            this@apply.prepare()
                        }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        android.util.Log.d("ShortsPlayer", "player[$index] state=$state")
                    }
                })
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { players.forEach { it.release() } }
    }

    val loadedVideoId = remember { mutableMapOf<ExoPlayer, String>() }

    LaunchedEffect(pagerState.currentPage, state.items.size, state.endReached) {
        if (state.items.isNotEmpty() && pagerState.currentPage >= state.items.size - 3) {
            vm.loadNextPage()
        }
    }

    LaunchedEffect(pagerState.settledPage, state.items) {
        val items = state.items
        if (items.isEmpty()) return@LaunchedEffect
        val settled = pagerState.settledPage
        val settledPlayer = players[settled % PLAYER_POOL_SIZE]

        players.forEach { p -> if (p !== settledPlayer) p.playWhenReady = false }

        items.getOrNull(settled)?.let { item ->
            retryCounts[settledPlayer] = 0
            loadIntoPlayer(settledPlayer, item.videoId, vm, autoPlay = true, loadedVideoId)
        }

        val nextItem = items.getOrNull(settled + 1)
        if (nextItem != null) {
            val nextPlayer = players[(settled + 1) % PLAYER_POOL_SIZE]
            if (nextPlayer !== settledPlayer) {
                retryCounts[nextPlayer] = 0
                loadIntoPlayer(nextPlayer, nextItem.videoId, vm, autoPlay = false, loadedVideoId)
            }
        }

        for (ahead in 2..PREFETCH_AHEAD) {
            items.getOrNull(settled + ahead)?.let { vm.prefetchStream(it.videoId) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.items.isEmpty() && state.loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            val item = state.items.getOrNull(index) ?: return@VerticalPager
            ShortsPlayerItem(item = item, player = players[index % PLAYER_POOL_SIZE], nav = nav)
        }
    }
}

@Composable
private fun ShortsPlayerItem(item: ShortItem, player: ExoPlayer, nav: NavController) {
    val theme = LocalZStreamTheme.current

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
        ) {
            Text(item.channelName, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                color = theme.colors.type.secondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.tmdbId != null && item.mediaType != null) {
                Spacer(Modifier.height(10.dp))
                ZsButton(
                    text = "Watch full movie/series",
                    onClick = { nav.navigate("detail/${item.mediaType}/${item.tmdbId}") },
                    variant = ZsButtonVariant.Primary,
                    leadingIcon = Icons.Filled.PlayArrow,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private suspend fun loadIntoPlayer(
    player: ExoPlayer,
    videoId: String,
    vm: ShortsViewModel,
    autoPlay: Boolean,
    loadedVideoId: MutableMap<ExoPlayer, String>,
) {
    if (loadedVideoId[player] == videoId) {
        player.playWhenReady = autoPlay
        return
    }
    val stream = vm.streamFor(videoId) ?: return

    val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(stream.userAgent)

    fun clippedItem(uri: String): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        val end = stream.clipEndMs
        if (end != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(stream.clipStartMs)
                    .setEndPositionMs(end)
                    .build()
            )
        }
        return builder.build()
    }

    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(clippedItem(stream.videoUrl))
    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(clippedItem(stream.audioUrl))

    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
    player.prepare()
    player.playWhenReady = autoPlay
    loadedVideoId[player] = videoId
}
