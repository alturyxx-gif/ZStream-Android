package com.zstream.android.ui.screens

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.data.LocalMediaRepository
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.download.DownloadStorage
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LocalPlaybackSource {
    data class Ready(val title: String, val videoUri: android.net.Uri, val subtitles: List<Pair<String, android.net.Uri>>) : LocalPlaybackSource()
    object NotFound : LocalPlaybackSource()
}

@HiltViewModel
class LocalPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
    private val localMediaRepository: LocalMediaRepository,
) : ViewModel() {
    private val downloadId: Long? = savedState.get<String>("downloadId")?.toLongOrNull()
    private val localMediaId: Long? = savedState.get<String>("localMediaId")?.toLongOrNull()

    private val _source = MutableStateFlow<LocalPlaybackSource?>(null)
    val source: StateFlow<LocalPlaybackSource?> = _source.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                loadDownload() ?: loadLocalMedia() ?: LocalPlaybackSource.NotFound
            }
            _source.value = loaded
        }
    }

    private suspend fun loadDownload(): LocalPlaybackSource.Ready? {
        val id = downloadId ?: return null
        val entity = downloadDao.getById(id)
        val filePath = entity?.filePath
        val videoUri = filePath?.let { storage.resolvePlayableUri(it) } ?: return null
        val subtitles = entity.subtitlePaths.orEmpty().mapNotNull { path ->
            storage.resolvePlayableUri(path)?.let { uri -> path to uri }
        }
        val title = if (entity.type == "show") {
            "${entity.title} S${entity.season.toString().padStart(2, '0')}E${entity.episode.toString().padStart(2, '0')}"
        } else {
            entity.title
        }
        return LocalPlaybackSource.Ready(title, videoUri, subtitles)
    }

    private suspend fun loadLocalMedia(): LocalPlaybackSource.Ready? {
        val media = localMediaRepository.getMedia(localMediaId ?: return null) ?: return null
        val title = if (media.mediaKind == "show") {
            "${media.groupTitle} S${media.season.toString().padStart(2, '0')}E${media.episode.toString().padStart(2, '0')}"
        } else {
            media.groupTitle
        }
        return LocalPlaybackSource.Ready(title, android.net.Uri.parse(media.documentUri), localMediaRepository.siblingSubtitles(media))
    }
}

private fun subtitleMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    else -> MimeTypes.APPLICATION_SUBRIP
}

/** Best-effort language tag guess from "...{lang}.srt" style filenames our own downloader writes. */
private fun subtitleLanguageTag(path: String): String {
    val name = path.substringAfterLast('/').substringBeforeLast('.')
    return name.substringAfterLast('.', "").ifBlank { "und" }
}

@Composable
fun LocalPlayerScreen(nav: NavController, vm: LocalPlayerViewModel = hiltViewModel()) {
    val isTv = LocalIsTv.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val source by vm.source.collectAsState()

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    val backFocusRequester = remember { FocusRequester() }

    val ready = source as? LocalPlaybackSource.Ready

    val player = remember(ready) {
        ready?.let { r ->
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(r.videoUri)
                    .setSubtitleConfigurations(
                        r.subtitles.map { (path, uri) ->
                            MediaItem.SubtitleConfiguration.Builder(uri)
                                .setMimeType(subtitleMimeType(path))
                                .setLanguage(subtitleLanguageTag(path))
                                .build()
                        }
                    )
                    .build()
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "Playback failed"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        if (isTv) runCatching { backFocusRequester.requestFocus() }
    }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible }
    ) {
        when {
            source == null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            source is LocalPlaybackSource.NotFound -> ZsStatusBanner(
                message = "This download's file could not be found — it may have been removed outside the app.",
                variant = ZsStatusBannerVariant.Error,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
            errorMessage != null -> ZsStatusBanner(
                message = errorMessage ?: "Playback failed",
                variant = ZsStatusBannerVariant.Error,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
            player != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (isBuffering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || isTv,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.focusRequester(backFocusRequester),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        (source as? LocalPlaybackSource.Ready)?.let { r ->
                            Spacer(Modifier.width(4.dp))
                            Text(r.title, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }

                if (player != null && errorMessage == null) {
                    var playPauseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { player.playWhenReady = !player.playWhenReady },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .onFocusChanged { playPauseFocused = it.isFocused }
                            .then(if (isTv && playPauseFocused) Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape) else Modifier),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}
