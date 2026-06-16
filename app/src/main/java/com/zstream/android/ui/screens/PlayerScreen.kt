package com.zstream.android.ui.screens

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.provider.WebViewDataSource

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(nav: NavController, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is PlayerState.Idle, is PlayerState.Scraping -> {
                val sources = (s as? PlayerState.Scraping)?.sources ?: emptyList()
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Finding sources…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    sources.forEach { SourceRow(it) }
                }
            }
            is PlayerState.Error -> {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    s.sources.forEach { SourceRow(it) }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::load) { Text("Retry") }
                }
            }
            is PlayerState.Ready -> {
                val player = remember {
                    val mediaSource = HlsMediaSource.Factory(WebViewDataSource.Factory(vm.getProxyPort()))
                        .createMediaSource(MediaItem.fromUri(s.streamUrl))
                    ExoPlayer.Builder(context).build().apply {
                        setMediaSource(mediaSource)
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) { onDispose { player.release() } }
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
    }
}

@Composable
private fun SourceRow(source: SourceResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        when (source.status) {
            SourceStatus.TRYING -> CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            SourceStatus.SUCCESS -> Text("✓", color = Color.Green)
            SourceStatus.FAILED -> Text("✕", color = Color.Red)
        }
        Spacer(Modifier.width(8.dp))
        Text(source.id, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
