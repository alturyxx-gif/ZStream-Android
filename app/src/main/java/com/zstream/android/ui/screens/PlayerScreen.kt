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
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.EventListener
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.provider.WebViewDataSource
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dagger.hilt.android.EntryPointAccessors
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsBottomSheetSectionCard
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
import com.zstream.android.ui.components.themed.ZsChip
import com.zstream.android.ui.components.themed.ZsChipVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.components.themed.ZsTextButton
import com.zstream.android.ui.components.themed.ZsTextField
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import com.zstream.android.data.WatchPartyAction
import coil.compose.AsyncImage

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
@Composable
private fun playerMenuSectionDivider(): Color = LocalZStreamTheme.current.colors.type.divider.copy(alpha = 0.25f)

@Composable
private fun playerMenuMutedText(): Color = LocalZStreamTheme.current.colors.type.secondary

@Composable
private fun playerMenuDimText(): Color = LocalZStreamTheme.current.colors.type.dimmed

@Composable
private fun playerMenuCardFill(): Color = LocalZStreamTheme.current.colors.background.secondary.copy(alpha = 0.45f)

@Composable
private fun playerMenuCardActiveFill(): Color = LocalZStreamTheme.current.colors.background.secondaryHover.copy(alpha = 0.7f)
private val PLAYER_MENU_BOX_TILE_HEIGHT = 80.dp
private val PLAYER_MENU_INNER_HORIZONTAL_PADDING = 24.dp
private val PLAYER_MENU_INNER_BOTTOM_PADDING = 18.dp

private data class PlayerMenuTileItem(
    val title: String,
    val value: String,
    val onClick: () -> Unit,
)
private val MENU_PANEL_WIDTH = 360.dp
private val MENU_PANEL_HEIGHT = 430.dp
private val OVERLAY_PANEL_SHAPE = RoundedCornerShape(24.dp)
private val BOTTOM_BAR_MENU_BUTTON_SIZE = 42.dp
private val BOTTOM_BAR_MENU_ICON_SIZE = 22.dp
private val PLAYER_DETAIL_SHEET_CORNER_RADIUS = 28.dp
private const val PLAYER_DETAIL_SHEET_HEIGHT_FRACTION = 1f
private val PLAYER_DETAIL_SHEET_SIDE_MARGIN = 24.dp
private val PLAYER_DETAIL_SHEET_BOTTOM_MARGIN = 0.dp
private const val PLAYBACK_SPEED_MIN = 0.25f
private const val PLAYBACK_SPEED_MAX = 5f
private const val RESUME_DIALOG_COMPLETION_THRESHOLD = 0.95f
private val MANUAL_SOURCE_PANEL_WIDTH = 343.dp
private val MANUAL_SOURCE_PANEL_HEIGHT = 431.dp
private val SKIP_SEGMENT_BUTTON_WIDTH = 160.dp
private val SKIP_SEGMENT_BAR_COLORS = mapOf(
    "intro" to Color(0xBF6366F1),
    "recap" to Color(0xBFF59E0B),
    "credits" to Color(0xBF22C55E),
    "preview" to Color(0xBFEC4899),
)

private fun sourceStatusColor(theme: ZStreamTheme, status: SourceStatus): Color = when (status) {
    SourceStatus.IDLE -> theme.colors.type.dimmed
    SourceStatus.SUCCESS -> theme.colors.type.success
    SourceStatus.FAILED -> theme.colors.type.danger
    SourceStatus.TRYING -> theme.colors.dropdown.highlightHover
}

private fun subtitleLanguageName(code: String): String =
    java.util.Locale.forLanguageTag(code.replace('_', '-')).getDisplayLanguage(java.util.Locale.getDefault())
        .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?: code.ifBlank { "Unknown language" }

@Composable
private fun SubtitleTrackBadges(track: SubtitleTrack) {
    val theme = LocalZStreamTheme.current
    val source = track.source.orEmpty()
    val sourceColor = when {
        source.contains("wyzie", true) -> Color(0xFF3B82F6)
        source.equals("opensubs", true) -> Color(0xFFF97316)
        source.equals("granite", true) -> Color(0xFF22C55E)
        else -> theme.colors.background.secondaryHover
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(track.type.uppercase() to theme.colors.background.secondaryHover)
            .plus(if (source.isNotBlank()) listOf(source.uppercase() to sourceColor) else emptyList())
            .plus(if (track.hearingImpaired) listOf("HI" to theme.colors.background.secondaryHover) else emptyList())
            .forEach { (label, color) ->
                Text(
                    label,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 5.dp, vertical = 3.dp),
                )
            }
    }
}

private enum class PlayerMenuPage {
    Root, Captions, CaptionLanguage, Playback, AdvancedColor, Sources, Quality, Audio, Download, WatchParty, SkipSegments, Seasons, Episodes
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

private const val PLAYER_AUTO_HIDE_DURATION = 7000L

@OptIn(UnstableApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(nav: NavController, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val roomCode by vm.watchPartyManager.roomCode.collectAsState()
    val watchPartyEnabled by vm.watchPartyManager.enabled.collectAsState()
    val participants by vm.watchPartyManager.participants.collectAsState()
    val isSyncing by vm.watchPartyManager.isSyncing.collectAsState()
    val isRegistering by vm.watchPartyManager.isRegistering.collectAsState()
    val contentMismatch by vm.watchPartyManager.contentMismatch.collectAsState()
    val durationMismatch by vm.watchPartyManager.durationMismatch.collectAsState()
    val hostGraceDeadlineMs by vm.watchPartyManager.hostGraceDeadlineMs.collectAsState()
    val isOffline by vm.watchPartyManager.isOffline.collectAsState()
    val isHost by vm.watchPartyManager.isHost.collectAsState()
    val watchPartyUserId by vm.watchPartyManager.selfUserId.collectAsState()
    val tvDetail by vm.tvDetail.collectAsState()
    val currentSeasonDetail by vm.currentSeasonDetail.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val playerScope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, playerScope)

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
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val playerFocusRequester = remember { FocusRequester() }
    var isInAppPip by remember { mutableStateOf(false) }
    var isRestoringPip by remember { mutableStateOf(false) }
    var isClosingPip by remember { mutableStateOf(false) }
    var pipIsPlaying by remember { mutableStateOf(false) }
    var pipTogglePlaybackRequest by remember { mutableIntStateOf(0) }
    var isPipDrawerExpanded by remember { mutableStateOf(false) }
    var pipAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    val pipExitProgress by animateFloatAsState(
        targetValue = if (isClosingPip) 1f else 0f,
        animationSpec = tween(300),
        label = "TV PiP exit",
    )
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val configuration = LocalConfiguration.current
    val fullWidth = configuration.screenWidthDp.dp
    val fullHeight = configuration.screenHeightDp.dp
    val compactWidth = fullWidth * 0.38f
    val compactHeight = compactWidth / pipAspectRatio
    val compact = isInAppPip && !isRestoringPip
    val boundsAnimation = if (settings.enableLowPerformanceMode) snap<Dp>() else tween<Dp>(350)
    val pipWidth by animateDpAsState(if (compact) compactWidth else fullWidth, boundsAnimation, label = "PiP width")
    val pipHeight by animateDpAsState(if (compact) compactHeight else fullHeight, boundsAnimation, label = "PiP height")
    val compactOffsetX = (fullWidth - compactWidth) / 2 - 32.dp
    val compactOffsetY = (fullHeight - compactHeight) / 2 - 32.dp
    val pipOffsetX by animateDpAsState(
        if (!compact) 0.dp else if (settings.tvPipPosition.endsWith("start")) -compactOffsetX else compactOffsetX,
        boundsAnimation,
        label = "PiP x",
    )
    val pipOffsetY by animateDpAsState(
        if (!compact) 0.dp else if (settings.tvPipPosition.startsWith("top")) -compactOffsetY else compactOffsetY,
        boundsAnimation,
        label = "PiP y",
    )

    LaunchedEffect(isRestoringPip) {
        if (!isRestoringPip) return@LaunchedEffect
        delay(350)
        isInAppPip = false
        isRestoringPip = false
    }

    LaunchedEffect(state, watchPartyEnabled, isHost) {
        val s = state
        if (watchPartyEnabled && isHost && (s is PlayerState.Scraping || s is PlayerState.ManualSourceSelection)) {
            vm.reportLoadingState()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isInAppPip) {
            HomeScreen(
                nav = nav,
                isTvPipActive = true,
                isTvPipDrawerExpanded = isPipDrawerExpanded,
                onTvPipDrawerExpandedChange = { isPipDrawerExpanded = it },
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(pipOffsetX, pipOffsetY)
                .width(pipWidth)
                .height(pipHeight)
                .graphicsLayer {
                    translationX = pipExitProgress * screenWidthPx *
                        if (settings.tvPipPosition.endsWith("start")) -1f else 1f
                    alpha = 1f - pipExitProgress
                }
                .then(if (compact) Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (settings.enableLowPerformanceMode) isInAppPip = false else isRestoringPip = true
                    } else Modifier)
                .background(Color.Black)
        ) {
        when (val s = state) {
            is PlayerState.Idle, is PlayerState.Scraping -> {
                val sources = (s as? PlayerState.Scraping)?.sources ?: emptyList()
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    // sources list
                    CircularProgressIndicator(color = theme.colors.type.emphasis, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Finding stream…", color = theme.colors.type.emphasis, fontSize = 14.sp)
                    sources.forEach { src ->
                        val statusColor = sourceStatusColor(theme, src.status)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(when (src.status) { SourceStatus.IDLE -> "•"; SourceStatus.TRYING -> "⟳"; SourceStatus.SUCCESS -> "✓"; SourceStatus.FAILED -> "✕" },
                                color = statusColor,
                                fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(src.id, color = theme.colors.type.dimmed, fontSize = 11.sp)
                        }
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.type.emphasis)
                }
            }
            is PlayerState.ManualSourceSelection -> {
                val theme = LocalZStreamTheme.current
                Surface(
                    Modifier
                        .align(Alignment.Center)
                        .width(MANUAL_SOURCE_PANEL_WIDTH)
                        .heightIn(min = MANUAL_SOURCE_PANEL_HEIGHT),
                    color = theme.colors.modal.background.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.emphasis.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        PlayerMenuHeader(title = "Sources", showBack = false, onBack = {})
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PLAYER_MENU_INNER_HORIZONTAL_PADDING)
                                .padding(top = 10.dp)
                        ) {
                            s.sources.forEach { source ->
                                PlayerMenuSelectableRow(
                                    title = sourceDisplayName(source.id),
                                    selected = source.id == s.selectedSourceId,
                                    onClick = { vm.probeSource(source.id) },
                                    rightContent = {
                                        ManualSourceStatusContent(
                                            source = source,
                                            onUse = vm::confirmManualSourceSelection
                                        )
                                    }
                                )
                                if (source != s.sources.last()) {
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                            s.message?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(16.dp))
                                Text(it, color = playerMenuMutedText(), fontSize = 12.sp)
                            }
                        }
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.type.emphasis)
                }
            }
            is PlayerState.Error -> {
                Column(Modifier
                    .align(Alignment.Center)
                    .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = theme.colors.type.emphasis, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = vm::load,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.emphasis.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry")
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.type.emphasis)
                }
            }
            is PlayerState.Ready -> {
                val accountVm: AccountViewModel = hiltViewModel()
                val session by accountVm.session.collectAsState()
                val progressList by accountVm.progress.collectAsState()
                val appRepos = remember(context) {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.zstream.android.di.RepositoryEntryPoint::class.java
                    )
                }
                val activity = LocalActivity.current
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
                var showInfoSheet by remember { mutableStateOf(false) }
                var infoState by remember { mutableStateOf<PlayerInfoState?>(null) }

                val matchesCurrentMedia: (com.zstream.android.data.local.entity.ProgressEntity) -> Boolean = { p ->
                    p.tmdbId == vm.tmdbId && if (vm.mediaType == "tv") {
                        vm.episodeId?.takeIf(String::isNotBlank)?.let { p.episodeId == it }
                            ?: (p.seasonNumber == vm.season && p.episodeNumber == vm.episode)
                    } else {
                        true
                    }
                }
                // Find existing progress for this exact movie or episode (mirroring p-stream ResumePart)
                val existingProgress = remember(progressList) {
                    progressList.firstOrNull(matchesCurrentMedia)
                }
                var showResumeDialog by remember { mutableStateOf(false) }
                var resumeWatched by remember { mutableLongStateOf(0L) }
                var resumeDuration by remember { mutableLongStateOf(0L) }
                var pendingResumeMs by remember { mutableLongStateOf(-1L) }

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
                                    .setId(track.id)
                                    .setLabel(track.label)
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

                    val cacheDataSourceFactory = CacheDataSource.Factory()
                        .setCache(vm.playerCache)
                        .setUpstreamDataSourceFactory(WebViewDataSource.Factory(vm.getProxyPort()))
                        .setEventListener(object : EventListener {
                            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                                Log.d("PlayerCache", "cachedBytesRead=$cachedBytesRead cacheSizeBytes=$cacheSizeBytes")
                            }

                            override fun onCacheIgnored(reason: Int) {
                                Log.d("PlayerCache", "cacheIgnored reason=$reason")
                            }
                        })

                    ExoPlayer.Builder(context).build().apply {
                        setMediaSource(DefaultMediaSourceFactory(cacheDataSourceFactory).createMediaSource(mediaItem))
                        videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                        prepare()
                        playWhenReady = true
                    }
                }

                LaunchedEffect(player, pipTogglePlaybackRequest) {
                    if (pipTogglePlaybackRequest == 0) return@LaunchedEffect
                    if (player.isPlaying) player.pause() else player.play()
                }

                LaunchedEffect(isClosingPip) {
                    if (!isClosingPip) return@LaunchedEffect
                    val initialVolume = player.volume
                    repeat(15) { step ->
                        player.volume = initialVolume * (1f - (step + 1) / 15f)
                        delay(20)
                    }
                    onBack()
                }

                // Snapshot initial progress once; playback must not mutate it behind the decision dialog.
                LaunchedEffect(Unit) {
                    delay(100)
                    val found = progressList.firstOrNull(matchesCurrentMedia)
                    val w = found?.watched?.toLong() ?: 0L
                    val d = found?.duration?.toLong() ?: 0L
                    if (w >= 20) {
                        val completion = if (d > 0) w.toFloat() / d.toFloat() else 0f
                        if (d > 0 && completion >= RESUME_DIALOG_COMPLETION_THRESHOLD) {
                            resumeWatched = w
                            resumeDuration = d
                            player.pause()
                            showResumeDialog = true
                        } else {
                            pendingResumeMs = w * 1000
                        }
                    }
                }

                var currentPositionMs by remember { mutableLongStateOf(0L) }
                var watchPartyHasPlayedOnce by remember(player) { mutableStateOf(false) }
                var hasAutoplayAttempted by remember(vm.tmdbId, vm.season, vm.episode) { mutableStateOf(false) }

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

                LaunchedEffect(
                    settings.enableAutoplay,
                    settings.enableSkipCredits,
                    vm.mediaType,
                    currentPositionMs,
                    player.duration,
                ) {
                    if (!settings.enableAutoplay || vm.mediaType != "tv") return@LaunchedEffect

                    val durationMs = player.duration.coerceAtLeast(0L)
                    if (durationMs <= 0L) {
                        hasAutoplayAttempted = false
                        return@LaunchedEffect
                    }

                    val isEnding = if (settings.enableSkipCredits) {
                        currentPositionMs >= durationMs - (durationMs / 100f).toLong()
                    } else {
                        currentPositionMs >= durationMs
                    }

                    if (!isEnding || hasAutoplayAttempted) return@LaunchedEffect

                    hasAutoplayAttempted = true
                    val target = vm.getAutoplayEpisodeTarget() ?: return@LaunchedEffect
                    val encodedTitle = Uri.encode(vm.title)
                    val encodedPoster = Uri.encode(vm.poster.orEmpty())
                    nav.navigate(
                        "player/tv/${vm.tmdbId}?season=${target.seasonNumber}&episode=${target.episodeNumber}" +
                            "&seasonId=${target.seasonId}&episodeId=${target.episodeId}" +
                            "&title=$encodedTitle&year=${vm.year}&poster=$encodedPoster&autoplay=true"
                    ) { popUpTo("home") }
                }

                LaunchedEffect(player, watchPartyEnabled, isHost) {
                    if (!watchPartyEnabled) return@LaunchedEffect
                    
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (player.playbackState == Player.STATE_READY || player.currentPosition > 0) {
                                watchPartyHasPlayedOnce = true
                            }
                            if (events.containsAny(
                                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                                    Player.EVENT_POSITION_DISCONTINUITY,
                                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED
                                )) {
                                vm.reportPlayerState(
                                    isPlaying = player.isPlaying,
                                    isPaused = !player.playWhenReady,
                                    isLoading = player.playbackState == Player.STATE_BUFFERING,
                                    hasPlayedOnce = watchPartyHasPlayedOnce,
                                    timeMs = player.currentPosition,
                                    durationMs = player.duration,
                                    playbackRate = player.playbackParameters.speed,
                                    bufferedMs = player.bufferedPosition,
                                    isHost = isHost
                                )
                            }
                        }
                    }
                    player.addListener(listener)
                    
                    try {
                        // Heartbeat to ensure position is updated regularly even without events
                        while (true) {
                            if (player.playbackState == Player.STATE_READY || player.currentPosition > 0) {
                                watchPartyHasPlayedOnce = true
                            }
                            vm.reportPlayerState(
                                isPlaying = player.isPlaying,
                                isPaused = !player.playWhenReady,
                                isLoading = player.playbackState == Player.STATE_BUFFERING,
                                hasPlayedOnce = watchPartyHasPlayedOnce,
                                timeMs = player.currentPosition,
                                durationMs = player.duration,
                                playbackRate = player.playbackParameters.speed,
                                bufferedMs = player.bufferedPosition,
                                isHost = isHost
                            )
                            delay(500)
                        }
                    } finally {
                        player.removeListener(listener)
                    }
                }

                LaunchedEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(
                                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                                    Player.EVENT_POSITION_DISCONTINUITY,
                                )) {
                                vm.reportTraktPlayback(player.isPlaying, player.currentPosition, player.duration)
                            }
                        }
                    }
                    player.addListener(listener)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        player.removeListener(listener)
                    }
                }

                LaunchedEffect(player) {
                    vm.watchPartyEvent.collect { action ->
                        Log.d("PlayerScreen", "Received WatchPartyAction: $action")
                        when (action) {
                            is WatchPartyAction.Seek -> {
                                player.seekTo(action.timeMs)
                            }
                            is WatchPartyAction.Play -> {
                                player.play()
                            }
                            is WatchPartyAction.Pause -> {
                                player.pause()
                            }
                            is WatchPartyAction.Navigate -> Unit
                        }
                    }
                }

                // Progress sync — mirrors p-stream ProgressSaver (3s interval, same guards)
                DisposableEffect(player, session) {
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                    val job = scope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(3000)
                            // Player must be read on main thread
                            if (!player.isPlaying) continue
                            val watchedSec = player.currentPosition / 1000
                            val durationSec = player.duration.let { if (it > 0) it / 1000 else 0L }
                            if (!vm.isAutoplay && watchedSec < 20) continue
                            if (!vm.isAutoplay && durationSec > 0 && (durationSec - watchedSec) < 120) continue
                            // Network call can run on IO via accountVm's viewModelScope
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val poster = existingProgress?.posterPath ?: vm.poster
                                    accountVm.syncProgress(
                                        session, vm.tmdbId, watchedSec, durationSec,
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
                        if (vm.isAutoplay || watchedSec >= 20 && (durationSec <= 0 || (durationSec - watchedSec) >= 120)) {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        val poster = existingProgress?.posterPath ?: vm.poster
                                        accountVm.syncProgress(
                                            session, vm.tmdbId, watchedSec, durationSec,
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

                var controlsVisible by remember { mutableStateOf(true) }
                var isPlaying by remember { mutableStateOf(player.isPlaying) }
                var menuPage by remember { mutableStateOf<PlayerMenuPage?>(null) }
                var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val updateActivity = { lastInteractionTime = System.currentTimeMillis() }

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
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                pipAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                            }
                            val pv = playerViewRef.value ?: return
                            pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            videoTextureRef.value?.let { textureView ->
                                textureView.post {
                                    applyVideoAdjustments(textureView, settings, videoSize)
                                }
                            }
                        }
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            pipIsPlaying = playing
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                LaunchedEffect(controlsVisible, isPlaying, menuPage, lastInteractionTime, showInfoSheet) {
                    if (controlsVisible && isPlaying && !showInfoSheet) {
                        val waitTime = PLAYER_AUTO_HIDE_DURATION
                        val elapsed = System.currentTimeMillis() - lastInteractionTime
                        if (elapsed < waitTime) {
                            delay(waitTime - elapsed)
                        }
                        if (isPlaying && !showInfoSheet) {
                            controlsVisible = false
                            if (menuPage != null) menuPage = null
                        }
                    }
                }

                var audioSessionId by remember(player) { mutableIntStateOf(player.audioSessionId) }
                val onMenuPageChange: (PlayerMenuPage?) -> Unit = { 
                    menuPage = it
                    updateActivity()
                }
                val enterPip = {
                    val entered = if (isTv) {
                        controlsVisible = false
                        isInAppPip = true
                        false
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching {
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            ) == true
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                    isInPip = entered
                }

                BackHandler(enabled = showInfoSheet || menuPage != null) {
                    updateActivity()
                    if (showInfoSheet) {
                        showInfoSheet = false
                    } else {
                        onMenuPageChange(
                            when (menuPage) {
                                PlayerMenuPage.Root, null -> null
                                PlayerMenuPage.AdvancedColor -> PlayerMenuPage.Playback
                                PlayerMenuPage.Episodes -> PlayerMenuPage.Seasons
                                PlayerMenuPage.CaptionLanguage -> PlayerMenuPage.Captions
                                else -> PlayerMenuPage.Root
                            }
                        )
                    }
                }
                BackHandler(enabled = isTv && roomCode != null && !showInfoSheet && menuPage == null && !isInPip && !isInAppPip) {
                    updateActivity()
                    showInfoSheet = false
                    showResumeDialog = false
                    enterPip()
                }

                if (isTv) {
                    LaunchedEffect(Unit) {
                        playerFocusRequester.requestFocus()
                    }
                }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent {
                        updateActivity()
                        false
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                updateActivity()
                            }
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (isTv && keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_BUTTON_A,
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (!controlsVisible) {
                                        controlsVisible = true
                                        updateActivity()
                                        true
                                    } else false
                                }

                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (!controlsVisible) {
                                        player.seekTo(
                                            (player.currentPosition - 10_000).coerceAtLeast(
                                                0
                                            )
                                        )
                                        controlsVisible = true
                                        updateActivity()
                                        true
                                    } else false
                                }

                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (!controlsVisible) {
                                        player.seekTo(
                                            (player.currentPosition + 10_000).coerceAtMost(
                                                player.duration
                                            )
                                        )
                                        controlsVisible = true
                                        updateActivity()
                                        true
                                    } else false
                                }

                                android.view.KeyEvent.KEYCODE_BACK -> {
                                    if (controlsVisible) {
                                        controlsVisible = false
                                        true
                                    } else false
                                }

                                else -> false
                            }
                        } else false
                    }
                    .focusRequester(playerFocusRequester)
                    .focusable()
                ) {
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
                val selectedSubtitleId by vm.selectedSubtitleId.collectAsState()
                val selectedTrackIsExternal = s.subtitles.firstOrNull { it.id == selectedSubtitleId }?.external == true
                val skipSegments by vm.skipSegments.collectAsState()
                val subtitlesEnabled = settings.subtitlesEnabled

                LaunchedEffect(vm.tmdbId, vm.season, vm.episode, player.duration) {
                    val duration = player.duration.coerceAtLeast(0L)
                    if (duration > 0) {
                        vm.loadSkipSegments(duration)
                    }
                }

                LaunchedEffect(player, settings.enableNativeSubtitles, subtitlesEnabled, selectedLang, selectedSubtitleId, selectedTrackIsExternal) {
                    var subtitleOverride: TrackSelectionOverride? = null
                    if (settings.enableNativeSubtitles && !selectedTrackIsExternal && subtitlesEnabled && selectedSubtitleId != null) {
                        // ponytail: wait briefly for Media3 track discovery; use a listener if subtitle preparation exceeds 5s.
                        for (attempt in 0 until 50) {
                            subtitleOverride = player.currentTracks.groups.firstNotNullOfOrNull { group ->
                                if (group.type != C.TRACK_TYPE_TEXT) return@firstNotNullOfOrNull null
                                (0 until group.length).firstOrNull { group.getTrackFormat(it).id == selectedSubtitleId }
                                    ?.let { TrackSelectionOverride(group.mediaTrackGroup, it) }
                            }
                            if (subtitleOverride != null) break
                            delay(100)
                        }
                    }
                    val builder = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.enableNativeSubtitles || selectedTrackIsExternal || !subtitlesEnabled)
                        .setPreferredTextLanguage(
                            if (settings.enableNativeSubtitles && !selectedTrackIsExternal && subtitlesEnabled) {
                                selectedLang ?: settings.defaultSubtitleLanguage
                            } else {
                                null
                            }
                        )
                    subtitleOverride?.let(builder::addOverride)
                    player.trackSelectionParameters = builder.build()
                }

                if ((!settings.enableNativeSubtitles || selectedTrackIsExternal) && subtitlesEnabled && selectedLang != null && visibleCues.isNotEmpty()) {
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

                CompositionLocalProvider(
                    LocalIndication provides ripple(color = Color.White)
                ) {
                PlayerControls(
                    player = player,
                    title = vm.title,
                    episodeLabel = if (vm.mediaType == "tv" && vm.season != null && vm.episode != null) {
                        "S${vm.season} E${vm.episode}"
                    } else null,
                    readyState = s,
                    settings = settings,
                    selectedSubtitleLanguage = selectedLang,
                    selectedSubtitleId = selectedSubtitleId,
                    isBookmarked = isBookmarked != null,
                    onToggleBookmark = {
                        vm.toggleBookmark()
                        updateActivity()
                    },
                    controlsVisible = controlsVisible,
                    onControlsVisibilityChanged = { 
                        controlsVisible = it
                        if (it) updateActivity()
                    },
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
                    onAutoSelectSubtitle = vm::autoSelectSubtitle,
                    onDisableSubtitles = vm::disableSubtitles,
                    onSetEnableAutoplay = vm::setEnableAutoplay,
                    onSetVideoBrightness = vm::setVideoBrightness,
                    onSetVideoContrast = vm::setVideoContrast,
                    onSetVideoSaturation = vm::setVideoSaturation,
                    onSetVideoHueRotate = vm::setVideoHueRotate,
                    onResetAdvancedColor = vm::resetAdvancedColor,
                    onSetVolumeBoost = vm::setVolumeBoost,
                    onSetVideoScaleMode = { mode ->
                        vm.setVideoScaleMode(mode)
                        val updatedSettings = settings.copy(videoScaleMode = mode.lowercase())
                        videoTextureRef.value?.let { applyVideoAdjustments(it, updatedSettings, currentVideoSize) }
                    },
                    onSelectSource = vm::selectSource,
                    skipSegments = skipSegments,
                    canSubmitSkipSegments = LocalConfiguration.current.smallestScreenWidthDp < 600,
                    hasTidbKey = !settings.tidbKey.isNullOrBlank(),
                    onSubmitSkipSegment = vm::submitSkipSegment,
                    tmdbId = vm.tmdbId.toIntOrNull() ?: 0,
                    mediaType = vm.mediaType,
                    seasonNumber = vm.season,
                    episodeNumber = vm.episode,
                    seasonId = vm.seasonId,
                    episodeId = vm.episodeId,
                    onInfo = {
                        showInfoSheet = true
                        playerScope.launch {
                            infoState = PlayerInfoState.Loading
                            infoState = runCatching {
                                if (vm.mediaType == "tv") {
                                    val detail = tmdbRepo.tvDetail(vm.tmdbId.toInt())
                                    val initialSeasonNumber = vm.season
                                        ?: existingProgress?.seasonNumber
                                        ?: detail.seasons?.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                                    val initialSeason = initialSeasonNumber?.let {
                                        tmdbRepo.season(vm.tmdbId.toInt(), it)
                                    }
                                    PlayerInfoState.Tv(detail, initialSeason)
                                } else {
                                    PlayerInfoState.Movie(tmdbRepo.movieDetail(vm.tmdbId.toInt()))
                                }
                            }.getOrElse { PlayerInfoState.Error(it.message ?: "Failed to load details") }
                        }
                    },
                    onBack = onBack,
                    onPip = {
                        showInfoSheet = false
                        showResumeDialog = false
                        enterPip()
                    },
                    roomCode = roomCode,
                    participants = participants,
                    myUserId = watchPartyUserId,
                    isSyncing = isSyncing,
                    isRegistering = isRegistering,
                    contentMismatch = contentMismatch,
                    durationMismatch = durationMismatch,
                    hostGraceDeadlineMs = hostGraceDeadlineMs,
                    isOffline = isOffline,
                    isHost = isHost,
                    onHostWatchParty = vm.watchPartyManager::hostRoom,
                    onLeaveWatchParty = vm.watchPartyManager::leaveRoom,
                    onJoinWatchParty = { vm.watchPartyManager.joinRoom(it) },
                    onUpdateRoomCode = vm.watchPartyManager::updateRoomCode,
                    onManualSync = vm.watchPartyManager::manualSync,
                    onLegacyWatchParty = {
                        val encoded = Uri.encode(s.streamUrl)
                        val wpUrl = "https://www.watchparty.me/create?video=$encoded"
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.let {
                            it.setPrimaryClip(android.content.ClipData.newPlainText("Legacy Watch Party", wpUrl))
                        }
                    },
                    showInfoSheet = showInfoSheet,
                    menuPage = menuPage,
                    onMenuPageChange = onMenuPageChange,
                    allProgress = progressList,
                    currentSeason = vm.season,
                    currentEpisode = vm.episode,
                    onLoadSeason = vm::loadSeason,
                    onSwitchEpisode = vm::switchEpisode,
                    poster = vm.poster,
                    nav = nav,
                    tvDetail = tvDetail,
                    currentSeasonDetail = currentSeasonDetail
                )
                }

                val infoSheetFocusRequester = remember { FocusRequester() }
                val infoSheetFirstItemFocusRequester = remember { FocusRequester() }

                LaunchedEffect(showInfoSheet) {
                    updateActivity()
                    if (showInfoSheet && isTv) {
                        delay(200)
                        infoSheetFirstItemFocusRequester.requestFocus()
                    }
                }

                AnimatedVisibility(
                    visible = showInfoSheet,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isTv) Modifier.focusProperties {
                                canFocus = false
                            } else Modifier)
                            .onKeyEvent {
                                if (isTv && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                    showInfoSheet = false
                                    true
                                } else false
                            }
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
                        .focusRequester(infoSheetFocusRequester)
                        .focusProperties {
                            onEnter = { infoSheetFirstItemFocusRequester.requestFocus() }
                            // Trap focus inside the info sheet for TV navigation
                            if (isTv) {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                        }
                        .focusable()
                ) {
                    PlayerInfoSheet(
                        state = infoState,
                        nav = nav,
                        allProgress = progressList,
                        showImageLogos = settings.enableImageLogos,
                        isBookmarked = isBookmarked != null,
                        onToggleBookmark = vm::toggleBookmark,
                        onClose = { showInfoSheet = false },
                        firstItemFocusRequester = infoSheetFirstItemFocusRequester,
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
                    val theme = LocalZStreamTheme.current
                    val pct = if (resumeDuration > 0) ((resumeWatched * 100) / resumeDuration).toInt() else 0
                    AlertDialog(
                        onDismissRequest = {},
                        containerColor = theme.colors.modal.background,
                        title = { Text("Continue Watching?", color = theme.colors.type.emphasis) },
                        text = { Text("You're $pct% through. Resume from where you left off?", color = theme.colors.type.secondary) },
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
                                    player.play()
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.buttons.purple.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Resume", color = theme.colors.buttons.purple) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showResumeDialog = false
                                    player.seekTo(0)
                                    player.play()
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.emphasis.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Restart", color = theme.colors.type.secondary) }
                        }
                    )
                }
            }
            }
        }
        if (compact) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, theme.colors.global.accentA, RoundedCornerShape(12.dp))
            )
        }
    }
    if (isInAppPip && isPipDrawerExpanded) {
        TvPipDrawerPanel(
            onOpenPip = {
                isPipDrawerExpanded = false
                if (settings.enableLowPerformanceMode) isInAppPip = false else isRestoringPip = true
            },
            onMovePip = {
                val next = when (settings.tvPipPosition) {
                    "bottom_end" -> "bottom_start"
                    "bottom_start" -> "top_start"
                    "top_start" -> "top_end"
                    else -> "bottom_end"
                }
                vm.setTvPipPosition(next)
            },
            isPipPlaying = pipIsPlaying,
            onTogglePipPlayback = { pipTogglePlaybackRequest++ },
            onClosePip = { isClosingPip = true },
            onDismiss = { isPipDrawerExpanded = false },
            modifier = Modifier.align(Alignment.CenterStart),
        )
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
                "stretch" -> {
                    val stretchScaleX = (1f / fitScaleX.coerceAtLeast(0.0001f))
                    val stretchScaleY = (1f / fitScaleY.coerceAtLeast(0.0001f))
                    transform.setScale(stretchScaleX, stretchScaleY, viewWidth / 2f, viewHeight / 2f)
                }
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

@OptIn(UnstableApi::class)
private fun String.slugify(): String {
    return this.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .replace(Regex("\\s+"), "-")
        .trim('-')
}

@OptIn(ExperimentalComposeUiApi::class, UnstableApi::class)
@Composable
private fun PlayerControls(
    player: ExoPlayer,
    title: String,
    episodeLabel: String?,
    readyState: PlayerState.Ready,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    selectedSubtitleLanguage: String?,
    selectedSubtitleId: String?,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    subtitlesEnabled: Boolean = false,
    onToggleSubtitles: () -> Unit = {},
    onSelectSubtitle: (String) -> Unit,
    onAutoSelectSubtitle: () -> Unit,
    onDisableSubtitles: () -> Unit,
    onSetEnableAutoplay: (Boolean) -> Unit,
    onSetVideoBrightness: (Int) -> Unit,
    onSetVideoContrast: (Int) -> Unit,
    onSetVideoSaturation: (Int) -> Unit,
    onSetVideoHueRotate: (Int) -> Unit,
    onResetAdvancedColor: () -> Unit,
    onSetVolumeBoost: (Int) -> Unit,
    onSetVideoScaleMode: (String) -> Unit,
    onSelectSource: (String) -> Unit,
    skipSegments: List<SkipSegment>,
    canSubmitSkipSegments: Boolean,
    hasTidbKey: Boolean,
    onSubmitSkipSegment: suspend (SkipSegmentSubmission) -> Result<Unit>,
    tmdbId: Int,
    mediaType: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    seasonId: String?,
    episodeId: String?,
    onInfo: () -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
    roomCode: String?,
    participants: List<com.zstream.android.data.Participant>,
    myUserId: String?,
    isSyncing: Boolean,
    isRegistering: Boolean,
    contentMismatch: Boolean,
    durationMismatch: Boolean,
    hostGraceDeadlineMs: Long?,
    isOffline: Boolean,
    isHost: Boolean,
    onHostWatchParty: () -> Unit,
    onLeaveWatchParty: () -> Unit,
    onJoinWatchParty: (String) -> Unit,
    onUpdateRoomCode: (String) -> Unit,
    onManualSync: () -> Unit,
    onLegacyWatchParty: () -> Unit,
    showInfoSheet: Boolean,
    menuPage: PlayerMenuPage?,
    onMenuPageChange: (PlayerMenuPage?) -> Unit,
    allProgress: List<com.zstream.android.data.local.entity.ProgressEntity>,
    currentSeason: Int?,
    currentEpisode: Int?,
    onLoadSeason: (Int) -> Unit,
    onSwitchEpisode: (Int, Int) -> Unit,
    poster: String?,
    nav: NavController,
    tvDetail: com.zstream.android.data.model.TvDetail?,
    currentSeasonDetail: com.zstream.android.data.model.Season?,
) {
    val menuOpen = menuPage != null
    val theme = LocalZStreamTheme.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var isJoiningRoom by remember { mutableStateOf(false) }
    // Auto-switch to room details when joined
    LaunchedEffect(roomCode) {
        if (roomCode != null && isJoiningRoom) {
            isJoiningRoom = false
            onMenuPageChange(PlayerMenuPage.WatchParty)
        }
    }
    val isTv = LocalIsTv.current
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playWhenReady by remember { mutableStateOf(player.playWhenReady) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var isDragging by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    val menuScrollPositions = remember { mutableStateMapOf<PlayerMenuPage, Int>() }
    var tracksSnapshot by remember { mutableStateOf(player.currentTracks) }
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var showSkipSubmissionDialog by remember { mutableStateOf(false) }
    var skipSubmissionSeed by remember { mutableStateOf<SkipSegment?>(null) }
    val enableDoubleTapToSeek = settings.enableDoubleClickToSeek
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var captionLanguage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayWhenReadyChanged(pwr: Boolean, reason: Int) { playWhenReady = pwr }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                // If buffering and we want to play, ensure we're not stuck
                if (state == Player.STATE_BUFFERING && playWhenReady) {
                    player.play()
                }
            }
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
    val rawQualityOptions = remember(tracksSnapshot) { collectQualityOptions(tracksSnapshot) }
    val autoQualitySelected = rawQualityOptions.count { it.selected } != 1
    val qualityOptions = if (autoQualitySelected) rawQualityOptions.map { it.copy(selected = false) } else rawQualityOptions
    val audioOptions = remember(tracksSnapshot) { collectAudioOptions(tracksSnapshot) }
    val selectedQualityLabel = if (autoQualitySelected) "Auto" else qualityOptions.firstOrNull { it.selected }?.label ?: "Auto"
    val selectedAudioLabel = audioOptions.firstOrNull { it.selected }?.label ?: "Default"
    val currentTimeSeconds = positionMs / 1000f
    val activeSkipSegments = remember(skipSegments, currentTimeSeconds) {
        skipSegments.filter { shouldShowSkipButton(currentTimeSeconds, it) != SkipButtonVisibility.None }
    }
    val skippedSegmentIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(readyState.sourceId, skipSegments) {
        skippedSegmentIds.clear()
    }

    LaunchedEffect(settings.enableAutoSkipSegments, settings.enableSkipCredits, currentTimeSeconds, skipSegments) {
        if (!settings.enableAutoSkipSegments) return@LaunchedEffect
        skipSegments.forEach { segment ->
            val isCreditsToEnd = segment.type == "credits" && segment.endMs == null
            val isCreditsDisabled = segment.type == "credits" && !settings.enableSkipCredits
            if (isCreditsDisabled) return@forEach
            if (segment.type != "credits" && segment.endMs == null) return@forEach
            if (isCreditsToEnd && !settings.enableSkipCredits) return@forEach

            val startSeconds = (segment.startMs ?: 0L) / 1000f
            val endSeconds = if (segment.endMs != null) segment.endMs / 1000f else Float.POSITIVE_INFINITY
            val segmentId = skipSegmentId(segment)
            if (currentTimeSeconds >= startSeconds && currentTimeSeconds < endSeconds && skippedSegmentIds[segmentId] != true) {
                val seekTargetMs = segment.endMs ?: (durationMs.takeIf { it > 0 } ?: positionMs + 10_000L)
                player.seekTo(seekTargetMs)
                skippedSegmentIds[segmentId] = true
            }
        }
    }

    LaunchedEffect(menuOpen) {
        if (menuOpen && !controlsVisible) {
            onControlsVisibilityChanged(true)
        }
    }

    val playFocusRequester = remember { FocusRequester() }
    val ccFocusRequester = remember { FocusRequester() }
    val bookmarkFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val pipFocusRequester = remember { FocusRequester() }
    val skipFocusRequester = remember { FocusRequester() }
    val muteFocusRequester = remember { FocusRequester() }
    var menuSourceRequester by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100)
            if (isTv) {
                if (activeSkipSegments.isNotEmpty()) {
                    skipFocusRequester.requestFocus()
                } else {
                    playFocusRequester.requestFocus()
                }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .then(
            if (isTv) {
                Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (!menuOpen) {
                        onControlsVisibilityChanged(!controlsVisible)
                    }
                }
            } else {
                Modifier.pointerInput(
                    menuOpen,
                    controlsVisible,
                    durationMs,
                    roomCode,
                    playbackSpeed,
                    settings.enableHoldToBoost,
                    enableDoubleTapToSeek,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val up = withTimeoutOrNull(300L) {
                            waitForUpOrCancellation()
                        }

                        if (
                            up == null &&
                            settings.enableHoldToBoost &&
                            roomCode == null &&
                            player.isPlaying
                        ) {
                            val previousSpeed = player.playbackParameters.speed
                            player.playbackParameters = PlaybackParameters(2f)
                            playbackSpeed = 2f
                            waitForUpOrCancellation()
                            player.playbackParameters = PlaybackParameters(previousSpeed)
                            playbackSpeed = previousSpeed
                            return@awaitEachGesture
                        }

                        val release = up ?: return@awaitEachGesture
                        val releaseTimeMs = release.uptimeMillis
                        val isDoubleTap = releaseTimeMs - lastTapTimeMs <= 250L
                        lastTapTimeMs = releaseTimeMs

                        if (isDoubleTap) {
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = null

                            if (enableDoubleTapToSeek) {
                                val oneThird = size.width / 3f
                                when {
                                    release.position.x < oneThird -> {
                                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                    }

                                    release.position.x > oneThird * 2f -> {
                                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(durationMs))
                                    }

                                    !menuOpen -> onControlsVisibilityChanged(!controlsVisible)
                                }
                            } else if (!menuOpen) {
                                onControlsVisibilityChanged(!controlsVisible)
                            }

                            return@awaitEachGesture
                        }

                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = scope.launch {
                            delay(250L)
                            if (!menuOpen) {
                                onControlsVisibilityChanged(!controlsVisible)
                            }
                        }
                    }
                }
            }
        )
    ) {
        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                color = theme.colors.global.accentA,
                modifier = Modifier
                    .size(if (isTv) 56.dp else 48.dp)
                    .align(Alignment.Center),
                strokeWidth = 3.dp
            )
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    theme.colors.video.context.background.copy(alpha = 0.9f),
                                    theme.colors.video.context.background.copy(alpha = 0.42f),
                                    Color.Transparent,
                                )
                            )
                        )
                        .padding(
                            start = TOP_BAR_LEFT_PADDING,
                            end = TOP_BAR_RIGHT_PADDING,
                            top = 12.dp,
                            bottom = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZsIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            variant = ZsIconButtonVariant.Ghost,
                            containerSize = TOP_BAR_BUTTON_SIZE,
                            iconSize = TOP_BAR_ICON_SIZE,
                            modifier = if (isTv) Modifier.focusProperties { 
                                down = playFocusRequester
                                if (showInfoSheet || menuOpen) {
                                    canFocus = false
                                }
                            } else Modifier,
                        )
                        if (!isTv) {
                            Text("Back to home", color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onBack))
                            Text("  /  ", color = theme.colors.type.dimmed.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        Column(modifier = Modifier.widthIn(max = 320.dp)) {
                            Text(title, color = theme.colors.type.emphasis, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (episodeLabel != null) {
                                Text(episodeLabel, color = theme.colors.type.secondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        ZsIconButton(
                            onClick = onInfo,
                            icon = Icons.Default.Info,
                            contentDescription = "Info",
                            variant = ZsIconButtonVariant.Ghost,
                            containerSize = TOP_BAR_BUTTON_SIZE,
                            iconSize = TOP_BAR_ICON_SIZE,
                            modifier = if (isTv) Modifier.focusProperties { 
                                down = playFocusRequester
                                if (showInfoSheet || menuOpen) {
                                    canFocus = false
                                }
                            } else Modifier,
                        )
                        ZsIconButton(
                            onClick = onToggleBookmark,
                            icon = if (isBookmarked) ImageVector.vectorResource(R.drawable.ic_player_bookmark_filled) else ImageVector.vectorResource(R.drawable.ic_player_bookmark_outline),
                            contentDescription = "Bookmark",
                            variant = ZsIconButtonVariant.Ghost,
                            selected = isBookmarked,
                            containerSize = TOP_BAR_BUTTON_SIZE,
                            iconSize = TOP_BAR_ICON_SIZE,
                            modifier = if (isTv) Modifier
                                .focusRequester(bookmarkFocusRequester)
                                .focusProperties {
                                    down = playFocusRequester
                                    if (showInfoSheet || menuOpen) {
                                        canFocus = false
                                    }
                                }
                            else Modifier,
                        )
                    }
                }

                Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CENTER_ICON_SPACING)
                    ) {
                        if (!isTv) {
                            IconButton(
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition - 10_000).coerceAtLeast(
                                            0
                                        )
                                    )
                                },
                                modifier = Modifier.size(CENTER_BUTTON_SIZE)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_player_skip_back),
                                    null,
                                    tint = theme.colors.type.emphasis,
                                    modifier = Modifier
                                        .height(CENTER_ICON_HEIGHT)
                                        .wrapContentWidth()
                                )
                            }
                        }
                        var playBtnFocused by remember { mutableStateOf(false) }
                        if (isTv) {
                            ZsOutlinedWrapper(
                                visible = playBtnFocused,
                                shape = RoundedCornerShape(50),
                                outlineColor = Color.White,
                                gap = 4.dp,
                            ) {
                                IconButton(onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                },
                                    modifier = Modifier
                                        .size(CENTER_BUTTON_SIZE)
                                        .background(if (playBtnFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                        .focusRequester(playFocusRequester)
                                        .focusProperties {
                                            up = bookmarkFocusRequester;
                                            down =
                                                if (activeSkipSegments.isNotEmpty()) skipFocusRequester else ccFocusRequester
                                            if (showInfoSheet || menuOpen) {
                                                canFocus = false
                                            }
                                        }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                        player.seekTo(
                                                            (player.currentPosition - 10_000).coerceAtLeast(
                                                                0
                                                            )
                                                        )
                                                        true
                                                    }

                                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                        player.seekTo(
                                                            (player.currentPosition + 10_000).coerceAtMost(
                                                                durationMs
                                                            )
                                                        )
                                                        true
                                                    }

                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .onFocusChanged { playBtnFocused = it.isFocused }
                                ) {
                                    Icon(
                                        painterResource(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                                        null, tint = theme.colors.type.emphasis,
                                        modifier = Modifier
                                            .height(CENTER_ICON_HEIGHT)
                                            .wrapContentWidth()
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { if (player.isPlaying) player.pause() else player.play() },
                                modifier = Modifier.size(CENTER_BUTTON_SIZE)
                            ) {
                                Icon(
                                    painterResource(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                                    null, tint = theme.colors.type.emphasis,
                                    modifier = Modifier
                                        .height(CENTER_ICON_HEIGHT)
                                        .wrapContentWidth()
                                )
                            }
                        }
                        if (!isTv) {
                            IconButton(
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition + 10_000).coerceAtMost(
                                            durationMs
                                        )
                                    )
                                },
                                modifier = Modifier.size(CENTER_BUTTON_SIZE)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_player_skip_fwd),
                                    null,
                                    tint = theme.colors.type.emphasis,
                                    modifier = Modifier
                                        .height(CENTER_ICON_HEIGHT)
                                        .wrapContentWidth()
                                )
                            }
                        }
                    }

                SkipSegmentOverlay(
                    theme = theme,
                    controlsVisible = controlsVisible,
                    currentTimeSeconds = currentTimeSeconds,
                    segments = activeSkipSegments,
                    durationMs = durationMs,
                    showInfoSheet = showInfoSheet,
                    menuOpen = menuOpen,
                    onSkip = { segment ->
                        val seekTargetMs = segment.endMs ?: durationMs
                        if (seekTargetMs > 0) {
                            player.seekTo(seekTargetMs)
                            skippedSegmentIds[skipSegmentId(segment)] = true
                        }
                    },
                    focusRequester = skipFocusRequester,
                    upRequester = playFocusRequester,
                    downRequester = ccFocusRequester,
                    onSeekBack = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                    onSeekFwd = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(durationMs)) },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SCRUBBER_SIDE_PADDING)
                            .height(22.dp)
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
                        val scrubberWidth = maxWidth
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.Center)
                                .background(theme.colors.progress.background.copy(alpha = 0.35f))
                        )
                        if (durationMs > 0 && skipSegments.isNotEmpty()) {
                            skipSegments.forEach { segment ->
                                val startFraction = ((segment.startMs ?: 0L).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                val endFraction = ((segment.endMs ?: durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                val widthFraction = (endFraction - startFraction).coerceAtLeast(0.0025f)
                                Box(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = scrubberWidth * startFraction)
                                        .width(scrubberWidth * widthFraction)
                                        .height(3.dp)
                                        .background(
                                            SKIP_SEGMENT_BAR_COLORS[segment.type]
                                                ?: theme.colors.type.dimmed.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(if (isDragging) scrubPosition else progress)
                                .height(3.dp)
                                .align(Alignment.CenterStart)
                                .background(theme.colors.progress.filled)
                        )
                        if (isDragging) {
                            val thumbFraction = scrubPosition
                            Box(Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(thumbFraction)
                                .wrapContentWidth(Alignment.End)) {
                                Box(Modifier
                                    .size(12.dp)
                                    .background(
                                        theme.colors.type.emphasis,
                                        androidx.compose.foundation.shape.CircleShape
                                    ))
                            }
                        }
                    }
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.22f to theme.colors.video.context.background.copy(alpha = 0.18f),
                                0.58f to theme.colors.video.context.background.copy(alpha = 0.48f),
                                1f to theme.colors.video.context.background.copy(alpha = 0.82f)
                            )
                        )
                        .padding(vertical = BOTTOM_BAR_PADDING_V),
                        verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(BOTTOM_LEFT_START_PADDING))
                        
                        if (!isTv) {
                            DrawableControlIcon(
                                res = if (playWhenReady) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                                theme = theme
                            ) {
                                if (playWhenReady) player.pause() else player.play()
                            }
                            DrawableControlIcon(R.drawable.ic_player_skip_back, theme) {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            }
                            DrawableControlIcon(R.drawable.ic_player_skip_fwd, theme) {
                                player.seekTo((player.currentPosition + 10_000).coerceAtMost(durationMs))
                            }
                        }
                        var muteBtnFocused by remember { mutableStateOf(false) }
                        if (isTv) {
                            ZsOutlinedWrapper(
                                visible = muteBtnFocused,
                                shape = RoundedCornerShape(50),
                                outlineColor = Color.White,
                                gap = 4.dp,
                            ) {
                                IconButton(
                                    onClick = { isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f },
                                    modifier = Modifier
                                        .size(BOTTOM_BAR_BUTTON_SIZE)
                                        .background(if (muteBtnFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                        .focusRequester(muteFocusRequester)
                                        .focusProperties {
                                            up =
                                                if (activeSkipSegments.isNotEmpty()) skipFocusRequester else playFocusRequester
                                            if (showInfoSheet || menuOpen) canFocus = false
                                        }
                                        .onFocusChanged { muteBtnFocused = it.isFocused }
                                ) {
                                    Icon(
                                        painterResource(if (isMuted) R.drawable.ic_player_mute else R.drawable.ic_player_volume),
                                        null,
                                        tint = theme.colors.type.emphasis,
                                        modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE)
                                    )
                                }
                            }
                        } else {
                            DrawableControlIcon(
                                res = if (isMuted) R.drawable.ic_player_mute else R.drawable.ic_player_volume,
                                theme = theme
                            ) {
                                isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("${formatTime(positionMs)} / ${formatTime(durationMs)}", color = theme.colors.type.emphasis, fontSize = 12.sp)

                        Spacer(Modifier.weight(1f))
                        if (roomCode != null && !isHost) {
                            ZsTextButton(
                                text = "Force Sync",
                                onClick = onManualSync,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        var ccBtnFocused by remember { mutableStateOf(false) }
                        var episodesBtnFocused by remember { mutableStateOf(false) }
                        if (isTv) {
                            if (mediaType == "tv") {
                                ZsOutlinedWrapper(
                                    visible = episodesBtnFocused,
                                    shape = RoundedCornerShape(50),
                                    outlineColor = Color.White,
                                    gap = 4.dp,
                                ) {
                                    ZsTextButton(
                                        text = "Episodes",
                                        leadingIcon = ImageVector.vectorResource(R.drawable.ic_player_episodes),
                                        contentColor = Color.White,
                                        customWhiteOverlay = true,
                                        onClick = {
                                            onControlsVisibilityChanged(true)
                                            onLoadSeason(currentSeason ?: 1)
                                            onMenuPageChange(PlayerMenuPage.Episodes)
                                            menuSourceRequester = episodesFocusRequester
                                        },
                                        modifier = Modifier
                                            .focusRequester(episodesFocusRequester)
                                            .onFocusChanged { episodesBtnFocused = it.isFocused }
                                            .focusProperties {
                                                up = if (activeSkipSegments.isNotEmpty()) skipFocusRequester else playFocusRequester
                                                if (showInfoSheet || menuOpen) canFocus = false
                                            },
                                    )
                                }
                            }
                            ZsOutlinedWrapper(
                                visible = ccBtnFocused,
                                shape = RoundedCornerShape(50),
                                outlineColor = Color.White,
                                gap = 4.dp,
                            ) {
                                IconButton(onClick = {
                                    onControlsVisibilityChanged(true)
                                    onMenuPageChange(PlayerMenuPage.Captions)
                                    menuSourceRequester = ccFocusRequester
                                }, modifier = Modifier
                                    .size(BOTTOM_BAR_MENU_BUTTON_SIZE)
                                    .background(if (ccBtnFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                    .focusRequester(ccFocusRequester)
                                    .focusProperties {
                                        up =
                                            if (activeSkipSegments.isNotEmpty()) skipFocusRequester else playFocusRequester
                                        if (showInfoSheet || menuOpen) {
                                            canFocus = false
                                        }
                                    }
                                    .onFocusChanged { ccBtnFocused = it.isFocused }
                                ) {
                                    Icon(Icons.Filled.ClosedCaption, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                                }
                            }
                        }

                        var settingsBtnFocused by remember { mutableStateOf(false) }
                        if (isTv) {
                            ZsOutlinedWrapper(
                                visible = settingsBtnFocused,
                                shape = RoundedCornerShape(50),
                                outlineColor = Color.White,
                                gap = 4.dp,
                            ) {
                                IconButton(onClick = {
                                    onControlsVisibilityChanged(true)
                                    onMenuPageChange(PlayerMenuPage.Root)
                                    menuSourceRequester = settingsFocusRequester
                                }, modifier = Modifier
                                    .size(BOTTOM_BAR_MENU_BUTTON_SIZE)
                                    .background(if (settingsBtnFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                    .focusRequester(settingsFocusRequester)
                                    .focusProperties {
                                        up =
                                            if (activeSkipSegments.isNotEmpty()) skipFocusRequester else playFocusRequester
                                        if (showInfoSheet || menuOpen) {
                                            canFocus = false
                                        }
                                    }
                                    .onFocusChanged { settingsBtnFocused = it.isFocused }
                                ) {
                                    Icon(Icons.Filled.Tune, null, tint = theme.colors.type.emphasis,
                                        modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                                }
                            }
                        }
                        if (isTv) {
                            var pipBtnFocused by remember { mutableStateOf(false) }
                            ZsOutlinedWrapper(
                                visible = pipBtnFocused,
                                shape = RoundedCornerShape(50),
                                outlineColor = Color.White,
                                gap = 4.dp,
                            ) {
                                IconButton(
                                    onClick = {
                                        onControlsVisibilityChanged(false)
                                        onPip()
                                    },
                                    modifier = Modifier
                                        .size(BOTTOM_BAR_MENU_BUTTON_SIZE)
                                        .background(if (pipBtnFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                        .focusRequester(pipFocusRequester)
                                        .focusProperties {
                                            up = if (activeSkipSegments.isNotEmpty()) skipFocusRequester else playFocusRequester
                                            if (showInfoSheet || menuOpen) canFocus = false
                                        }
                                        .onFocusChanged { pipBtnFocused = it.isFocused }
                                ) {
                                    Icon(
                                        Icons.Filled.PictureInPictureAlt,
                                        "Picture in picture",
                                        tint = theme.colors.type.emphasis,
                                        modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE)
                                    )
                                }
                            }
                        }
                        if (!isTv) {
                            if (mediaType == "tv") {
                                ZsTextButton(
                                    text = "Episodes",
                                    leadingIcon = ImageVector.vectorResource(R.drawable.ic_player_episodes),
                                    contentColor = Color.White,
                                    customWhiteOverlay = true,
                                    onClick = {
                                        onControlsVisibilityChanged(true)
                                        onLoadSeason(currentSeason ?: 1)
                                        onMenuPageChange(PlayerMenuPage.Episodes)
                                    },
                                )
                            }
                            IconButton(onClick = {
                                onControlsVisibilityChanged(true)
                                onMenuPageChange(PlayerMenuPage.Captions)
                            }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                                Icon(
                                    Icons.Filled.ClosedCaption,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE)
                                )
                            }
                            IconButton(onClick = {
                                onControlsVisibilityChanged(true)
                                onMenuPageChange(PlayerMenuPage.Root)
                            }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                                Icon(
                                    Icons.Filled.Tune,
                                    null,
                                    tint = theme.colors.type.emphasis,
                                    modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE)
                                )
                            }
                            IconButton(onClick = {
                                onMenuPageChange(null)
                                onControlsVisibilityChanged(false)
                                onPip()
                            }, modifier = Modifier.size(BOTTOM_BAR_MENU_BUTTON_SIZE)) {
                                Icon(Icons.Filled.PictureInPictureAlt, null, tint = theme.colors.type.emphasis, modifier = Modifier.size(BOTTOM_BAR_MENU_ICON_SIZE))
                            }
                        }
                        Spacer(Modifier.width(BOTTOM_RIGHT_END_PADDING))
                    }
                }
            }
        }

        // Watch Party Overlays
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .padding(horizontal = 24.dp)
                .widthIn(max = 450.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = hostGraceDeadlineMs != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                val graceProgress by produceState(initialValue = 0f, key1 = hostGraceDeadlineMs) {
                    while (hostGraceDeadlineMs != null) {
                        val remaining = (hostGraceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        value = (remaining.toFloat() / 5000f).coerceIn(0f, 1f)
                        if (remaining == 0L) break
                        delay(100L)
                    }
                }
                Surface(
                    color = theme.colors.modal.background.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Host connection stale. Holding room for a few seconds...",
                            color = theme.colors.type.emphasis,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        LinearProgressIndicator(
                            progress = { graceProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.colors.buttons.secondary,
                            trackColor = theme.colors.background.secondary
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = contentMismatch,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ZsStatusBanner(
                    message = "Host is watching a different episode. Auto-following...",
                    variant = ZsStatusBannerVariant.Info
                )
            }
            AnimatedVisibility(
                visible = isOffline,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ZsStatusBanner(
                    message = "Connection to Watch Party lost. Reconnecting...",
                    variant = ZsStatusBannerVariant.Error
                )
            }
            AnimatedVisibility(
                visible = durationMismatch,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ZsStatusBanner(
                    message = "Host stream duration differs. Play/pause sync still works, time sync is limited.",
                    variant = ZsStatusBannerVariant.Info
                )
            }
        }

        AnimatedVisibility(
            visible = isSyncing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = theme.colors.type.emphasis,
                strokeWidth = 3.dp
            )
        }

        val menuFocusRequester = remember { FocusRequester() }
        val menuFirstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(menuOpen) {
            if (menuOpen && isTv) {
                android.util.Log.d("PlayerFocus", "Menu opened, requesting focus on menuFirstItemFocusRequester, source=$menuSourceRequester")
                menuFirstItemFocusRequester.requestFocus()
                android.util.Log.d("PlayerFocus", "Menu focus request completed")
            } else if (!menuOpen && isTv && menuSourceRequester != null) {
                android.util.Log.d("PlayerFocus", "Menu closed, restoring focus to source=$menuSourceRequester")
                menuSourceRequester!!.requestFocus()
                menuSourceRequester = null
                android.util.Log.d("PlayerFocus", "Focus restoration completed")
            }
        }
        LaunchedEffect(menuPage) {
            if (menuPage != null && isTv) {
                android.util.Log.d("PlayerFocus", "Menu page changed to $menuPage, requesting focus on first item")
                menuFirstItemFocusRequester.requestFocus()
                android.util.Log.d("PlayerFocus", "Page focus request completed")
            }
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier.fillMaxSize()
        ) {
            val theme = LocalZStreamTheme.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isTv) Modifier.focusProperties { canFocus = false } else Modifier)
                    .onKeyEvent {
                        if (isTv && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            onMenuPageChange(
                                when (menuPage) {
                                    null, PlayerMenuPage.Root -> null
                                    PlayerMenuPage.AdvancedColor -> PlayerMenuPage.Playback
                                    PlayerMenuPage.CaptionLanguage -> PlayerMenuPage.Captions
                                    else -> PlayerMenuPage.Root
                                }
                            )
                            true
                        } else false
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onMenuPageChange(
                            when (menuPage) {
                                null, PlayerMenuPage.Root -> null
                                PlayerMenuPage.AdvancedColor -> PlayerMenuPage.Playback
                                PlayerMenuPage.CaptionLanguage -> PlayerMenuPage.Captions
                                else -> PlayerMenuPage.Root
                            }
                        )
                    }
            ) {
                Surface(
                    color = theme.colors.modal.background.copy(alpha = 0.96f),
                    shape = OVERLAY_PANEL_SHAPE,
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 28.dp, bottom = 86.dp)
                        .widthIn(max = MENU_PANEL_WIDTH)
                        .heightIn(max = MENU_PANEL_HEIGHT)
                        .border(
                            1.dp,
                            theme.colors.type.emphasis.copy(alpha = 0.08f),
                            OVERLAY_PANEL_SHAPE
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {}
                        .focusRequester(menuFocusRequester)
                        .focusProperties {
                            onEnter = { menuFirstItemFocusRequester.requestFocus() }
                            // Trap focus inside the menu for TV navigation
                            if (isTv) {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            }
                        }
                        .focusable()
                        .focusGroup()
                ) {
                    PlayerMenuContent(
                        page = menuPage ?: PlayerMenuPage.Root,
                        title = title,
                        settings = settings,
                        sourceId = readyState.sourceId,
                        embedId = readyState.embedId,
                        sourceResults = readyState.sources,
                        manualSourceSelection = settings.manualSourceSelection,
                        selectedSubtitleLanguage = selectedSubtitleLanguage,
                        selectedSubtitleId = selectedSubtitleId,
                        captionLanguage = captionLanguage,
                        subtitlesEnabled = subtitlesEnabled,
                        subtitleTracks = readyState.subtitles,
                        playbackSpeed = playbackSpeed,
                        selectedQualityLabel = selectedQualityLabel,
                        selectedAudioLabel = selectedAudioLabel,
                        qualityOptions = qualityOptions,
                        audioOptions = audioOptions,
                        skipSegments = skipSegments,
                        canSubmitSkipSegments = canSubmitSkipSegments,
                        hasTidbKey = hasTidbKey,
                        menuScrollPositions = menuScrollPositions,
                        firstItemFocusRequester = menuFirstItemFocusRequester,
                        onClose = { onMenuPageChange(null) },
                        onBack = {
                            onMenuPageChange(
                                when (menuPage) {
                                    null, PlayerMenuPage.Root -> null
                                    PlayerMenuPage.AdvancedColor -> PlayerMenuPage.Playback
                                    PlayerMenuPage.Episodes -> PlayerMenuPage.Seasons
                                    PlayerMenuPage.CaptionLanguage -> PlayerMenuPage.Captions
                                    else -> PlayerMenuPage.Root
                                }
                            )
                        },
                        onOpenPage = { onMenuPageChange(it) },
                        onOpenCaptionLanguage = {
                            captionLanguage = it
                            onMenuPageChange(PlayerMenuPage.CaptionLanguage)
                        },
                        onToggleSubtitles = onToggleSubtitles,
                        onDisableSubtitles = onDisableSubtitles,
                        onSelectSubtitle = onSelectSubtitle,
                        onAutoSelectSubtitle = onAutoSelectSubtitle,
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
                        onSelectSource = onSelectSource,
                        onOpenSkipSubmission = {
                            skipSubmissionSeed = it ?: (skipSegments.firstOrNull() ?: SkipSegment("intro", null, null))
                            showSkipSubmissionDialog = true
                        },
                        onSeekToMs = { player.seekTo(it) },
                        onPip = onPip,
                        roomCode = roomCode,
                        participants = participants,
                        myUserId = myUserId,
                        isSyncing = isSyncing,
                        contentMismatch = contentMismatch,
                        durationMismatch = durationMismatch,
                        hostGraceDeadlineMs = hostGraceDeadlineMs,
                        isOffline = isOffline,
                        isHost = isHost,
                        onHostWatchParty = onHostWatchParty,
                        onLeaveWatchParty = onLeaveWatchParty,
                        onJoinWatchParty = onJoinWatchParty,
                        onUpdateRoomCode = onUpdateRoomCode,
                        onManualSync = onManualSync,
                        onLegacyWatchParty = onLegacyWatchParty,
                        isRegistering = isRegistering,
                        mediaType = mediaType,
                        tmdbId = tmdbId,
                        seasonId = seasonId,
                        episodeId = episodeId,
                        streamUrl = readyState.streamUrl,
                        allProgress = allProgress,
                        currentSeason = currentSeason,
                        currentEpisode = currentEpisode,
                        onLoadSeason = onLoadSeason,
                        onSwitchEpisode = onSwitchEpisode,
                        poster = poster,
                        nav = nav,
                        tvDetail = tvDetail,
                        currentSeasonDetail = currentSeasonDetail
                    )
                }
            }
        }

    }

    if (showSkipSubmissionDialog) {
        SkipSegmentSubmissionDialog(
            seed = skipSubmissionSeed,
            tmdbId = tmdbId,
            mediaType = mediaType,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            videoDurationMs = durationMs.takeIf { it > 0L },
            onDismiss = { showSkipSubmissionDialog = false },
            onSubmit = onSubmitSkipSegment
        )
    }
}

@Composable
private fun DrawableControlIcon(@DrawableRes res: Int, theme: ZStreamTheme, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(BOTTOM_BAR_BUTTON_SIZE)) {
        Icon(painterResource(res), null, tint = theme.colors.type.emphasis, modifier = Modifier.size(BOTTOM_BAR_ICON_SIZE))
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

private enum class SkipButtonVisibility { Always, Hover, None }

private fun shouldShowSkipButton(currentTimeSeconds: Float, segment: SkipSegment): SkipButtonVisibility {
    val currentTimeMs = currentTimeSeconds * 1000f
    val startMs = (segment.startMs ?: 0L).toFloat()
    val endMs = (segment.endMs ?: Long.MAX_VALUE).toFloat()
    if (currentTimeMs < startMs || currentTimeMs > endMs) return SkipButtonVisibility.None
    val timeInSegment = currentTimeMs - startMs
    return if (timeInSegment <= 10_000f) SkipButtonVisibility.Always else SkipButtonVisibility.Hover
}

private fun skipSegmentId(segment: SkipSegment): String =
    "${segment.type}-${segment.startMs ?: "null"}-${segment.endMs ?: "null"}"

private fun skipSegmentLabel(type: String): String = when (type) {
    "intro" -> "Skip Intro"
    "recap" -> "Skip Recap"
    "credits" -> "Skip Credits"
    "preview" -> "Skip Preview"
    else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun segmentTypeLabel(type: String): String = when (type) {
    "intro" -> "Intro"
    "recap" -> "Recap"
    "credits" -> "Credits"
    "preview" -> "Preview"
    else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun parseTimeToSeconds(timeStr: String): Double? {
    val trimmed = timeStr.trim()
    if (trimmed.isEmpty()) return null

    Regex("""^(\d{1,2}):(0?[0-5]\d):(0?[0-5]\d)$""").matchEntire(trimmed)?.let { match ->
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        val seconds = match.groupValues[3].toInt()
        return (hours * 3600 + minutes * 60 + seconds).toDouble()
    }

    Regex("""^(\d{1,3}):(0?[0-5]\d)$""").matchEntire(trimmed)?.let { match ->
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        return (minutes * 60 + seconds).toDouble()
    }

    if (trimmed.contains(":")) return Double.NaN
    return trimmed.toDoubleOrNull()?.takeIf { it >= 0.0 && it <= 21_600_000.0 } ?: Double.NaN
}

@Composable
private fun RowScope.GuideColumn(theme: ZStreamTheme, title: String, body: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title, color = theme.colors.type.emphasis.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(body, color = theme.colors.type.secondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun guideStart(segmentType: String): String = when (segmentType) {
    "intro", "recap" -> "Optional. Leave blank to start from the beginning."
    "credits", "preview" -> "Required. Set where the segment begins."
    else -> "Set where the segment begins."
}

private fun guideEnd(segmentType: String): String = when (segmentType) {
    "intro", "recap" -> "Required. Set where playback should resume."
    "credits" -> "Optional. Leave blank if credits run to the end."
    "preview" -> "Optional. Leave blank if preview runs to the end."
    else -> "Set where playback should resume."
}

private fun guideDuration(segmentType: String): String = when (segmentType) {
    "intro", "recap" -> "Usually short and near the start of the episode."
    "credits" -> "Usually late in the runtime and may continue to the end."
    "preview" -> "Usually after the main content, often near the end."
    else -> "Use timestamps that match the visible segment."
}

private fun guideExclude(segmentType: String): String = when (segmentType) {
    "intro" -> "Do not include studio logos or unrelated cold open content."
    "recap" -> "Only include the recap block, not the opening titles."
    "credits" -> "Do not include post-credit scenes if playback should continue."
    "preview" -> "Only include the next-episode preview block."
    else -> "Avoid overlapping unrelated content."
}

@Composable
private fun outlinedFieldColors(theme: ZStreamTheme): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = theme.colors.type.text,
        unfocusedTextColor = theme.colors.type.text,
        focusedBorderColor = theme.colors.buttons.purple,
        unfocusedBorderColor = theme.colors.background.secondary,
        focusedLabelColor = theme.colors.type.emphasis.copy(alpha = 0.85f),
        unfocusedLabelColor = theme.colors.type.secondary,
        focusedPlaceholderColor = theme.colors.type.dimmed.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = theme.colors.type.dimmed.copy(alpha = 0.6f),
        cursorColor = theme.colors.type.emphasis
    )
}

@Composable
private fun SkipSegmentSubmissionDialog(
    seed: SkipSegment?,
    tmdbId: Int,
    mediaType: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    videoDurationMs: Long?,
    onDismiss: () -> Unit,
    onSubmit: suspend (SkipSegmentSubmission) -> Result<Unit>,
) {
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    var segmentType by remember(seed) { mutableStateOf(seed?.type ?: "intro") }
    var startText by remember(seed) { mutableStateOf(seed?.startMs?.let { (it / 1000).toString() }.orEmpty()) }
    var endText by remember(seed) { mutableStateOf(seed?.endMs?.let { (it / 1000).toString() }.orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun submit() {
        val startSeconds = parseTimeToSeconds(startText)
        val endSeconds = parseTimeToSeconds(endText)
        when {
            (segmentType == "intro" || segmentType == "recap") && (endSeconds == null || endSeconds.isNaN()) ->
                errorText = "End time is required for this segment type."
            (segmentType == "credits" || segmentType == "preview") && (startSeconds == null || startSeconds.isNaN()) ->
                errorText = "Start time is required for this segment type."
            (startSeconds != null && startSeconds.isNaN()) || (endSeconds != null && endSeconds.isNaN()) ->
                errorText = "Invalid time format."
            else -> {
                errorText = null
                scope.launch {
                    isSubmitting = true
                    val result = onSubmit(
                        SkipSegmentSubmission(
                            tmdbId = tmdbId,
                            type = if (mediaType == "tv") "tv" else "movie",
                            segment = segmentType,
                            season = seasonNumber,
                            episode = episodeNumber,
                            startSec = if (segmentType == "intro" || segmentType == "recap") startSeconds else startSeconds ?: 0.0,
                            endSec = if (segmentType == "credits" || segmentType == "preview") endSeconds else endSeconds,
                            videoDurationMs = videoDurationMs,
                        )
                    )
                    isSubmitting = false
                    result
                        .onSuccess { onDismiss() }
                        .onFailure { errorText = it.message ?: "Failed to submit segment." }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.colors.video.context.background.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = theme.colors.modal.background.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.97f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    theme.colors.mediaCard.hoverAccent.copy(alpha = 0.16f),
                                    theme.colors.modal.background
                                ),
                                center = Offset(220f, 140f),
                                radius = 780f
                            )
                        )
                ) {
                    ZsIconButton(
                        onClick = onDismiss,
                        icon = Icons.Filled.Close,
                        contentDescription = null,
                        variant = ZsIconButtonVariant.Secondary,
                        containerSize = 36.dp,
                        iconSize = 18.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                            .padding(top = 28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Download, null, tint = theme.colors.type.emphasis, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Submit Segment", color = theme.colors.type.emphasis, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Submit skip segment timings to TheIntroDB.",
                            color = theme.colors.type.secondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(20.dp))
                        Surface(
                            color = theme.colors.authentication.inputBg.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.background.secondary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Segment Type", color = theme.colors.type.emphasis, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("intro", "recap", "credits", "preview").forEach { type ->
                                        ZsChip(
                                            label = segmentTypeLabel(type),
                                            selected = segmentType == type,
                                            onClick = { segmentType = type },
                                            variant = ZsChipVariant.Selectable,
                                            selectedContainerColor = theme.colors.buttons.purple.copy(alpha = 0.2f),
                                            selectedContentColor = theme.colors.type.emphasis,
                                            selectedBorderColor = theme.colors.buttons.purple,
                                        )
                                    }
                                }
                                Text("Choose the segment type and enter timestamps in `seconds`, `mm:ss`, or `hh:mm:ss`.", color = theme.colors.type.secondary, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    ZsTextField(
                                        value = startText,
                                        onValueChange = { startText = it },
                                        label = "Start time",
                                        placeholder = if (segmentType == "credits" || segmentType == "preview") "2:30 or 150" else "2:30 or 150 (optional)",
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ZsTextField(
                                        value = endText,
                                        onValueChange = { endText = it },
                                        label = "End time",
                                        placeholder = if (segmentType == "intro" || segmentType == "recap") "3:30 or 210" else "3:30 or 210 (optional)",
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                ZsBottomSheetSectionCard(title = "Timing Guide") {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            GuideColumn(theme, "Start", guideStart(segmentType))
                                            GuideColumn(theme, "End", guideEnd(segmentType))
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            GuideColumn(theme, "Duration", guideDuration(segmentType))
                                            GuideColumn(theme, "Exclude", guideExclude(segmentType))
                                        }
                                    }
                                }
                                if (errorText != null) {
                                    ZsStatusBanner(
                                        message = errorText!!,
                                        variant = ZsStatusBannerVariant.Error,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.textButtonColors(contentColor = theme.colors.buttons.secondaryText)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = ::submit,
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.buttons.purple,
                                    contentColor = theme.colors.type.emphasis,
                                    disabledContainerColor = theme.colors.buttons.purple.copy(alpha = 0.45f),
                                    disabledContentColor = theme.colors.type.emphasis.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isSubmitting) "Submitting..." else "Submit")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipSegmentOverlay(
    theme: ZStreamTheme,
    controlsVisible: Boolean,
    currentTimeSeconds: Float,
    segments: List<SkipSegment>,
    durationMs: Long,
    showInfoSheet: Boolean,
    menuOpen: Boolean,
    onSkip: (SkipSegment) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onSeekBack: () -> Unit = {},
    onSeekFwd: () -> Unit = {},
) {
    val isTv = LocalIsTv.current
    if (segments.isEmpty()) return
    val bottomOffset by animateDpAsState(
        targetValue = if (controlsVisible) 96.dp else 48.dp,
        animationSpec = tween(durationMillis = 200),
        label = "skipSegmentBottomOffset"
    )

    Column(
        modifier = modifier.padding(end = 48.dp, bottom = bottomOffset),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            val visibility = shouldShowSkipButton(currentTimeSeconds, segment)
            val show = visibility == SkipButtonVisibility.Always || (visibility == SkipButtonVisibility.Hover && controlsVisible)
            var isFocused by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = show,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            ) {
                ZsOutlinedWrapper(
                    visible = isFocused && isTv,
                    shape = RoundedCornerShape(8.dp),
                    outlineColor = Color.White,
                    gap = 4.dp,
                ) {
                    TextButton(
                        onClick = { onSkip(segment) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = theme.colors.buttons.primary,
                            contentColor = theme.colors.buttons.primaryText,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .width(SKIP_SEGMENT_BUTTON_WIDTH)
                            .height(40.dp)
                            .then(
                                if (isTv && index == 0 && focusRequester != null) Modifier.focusRequester(
                                    focusRequester
                                ) else Modifier
                            )
                            .then(if (isTv) Modifier.focusProperties {
                                if (showInfoSheet || menuOpen) {
                                    canFocus = false
                                }
                                up = upRequester ?: FocusRequester.Default
                                down = downRequester ?: FocusRequester.Default
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            } else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (isTv && keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            onSeekBack()
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            onSeekFwd()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .graphicsLayer(scaleX = 0.95f, scaleY = 0.95f)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_player_skip_fwd),
                            null,
                            tint = theme.colors.buttons.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (segment.type == "credits" && segment.endMs == null && durationMs > 0L) "Next Episode" else skipSegmentLabel(
                                segment.type
                            ),
                            color = theme.colors.buttons.primaryText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, UnstableApi::class)
@Composable
private fun PlayerMenuContent(
    page: PlayerMenuPage,
    title: String,
    settings: com.zstream.android.data.local.entity.SettingsEntity,
    sourceId: String?,
    embedId: String?,
    sourceResults: List<SourceResult>,
    manualSourceSelection: Boolean,
    selectedSubtitleLanguage: String?,
    selectedSubtitleId: String?,
    captionLanguage: String?,
    subtitlesEnabled: Boolean,
    subtitleTracks: List<SubtitleTrack>,
    playbackSpeed: Float,
    selectedQualityLabel: String,
    selectedAudioLabel: String,
    qualityOptions: List<QualityOption>,
    audioOptions: List<AudioOption>,
    skipSegments: List<SkipSegment>,
    canSubmitSkipSegments: Boolean,
    hasTidbKey: Boolean,
    menuScrollPositions: SnapshotStateMap<PlayerMenuPage, Int>,
    firstItemFocusRequester: FocusRequester? = null,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onOpenPage: (PlayerMenuPage) -> Unit,
    onOpenCaptionLanguage: (String) -> Unit,
    onToggleSubtitles: () -> Unit,
    onDisableSubtitles: () -> Unit,
    onSelectSubtitle: (String) -> Unit,
    onAutoSelectSubtitle: () -> Unit,
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
    onSelectSource: (String) -> Unit,
    onOpenSkipSubmission: (SkipSegment?) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onPip: () -> Unit,
    roomCode: String?,
    participants: List<com.zstream.android.data.Participant>,
    myUserId: String?,
    isSyncing: Boolean,
    isRegistering: Boolean,
    contentMismatch: Boolean,
    durationMismatch: Boolean,
    hostGraceDeadlineMs: Long?,
    isOffline: Boolean,
    isHost: Boolean,
    onHostWatchParty: () -> Unit,
    onLeaveWatchParty: () -> Unit,
    onJoinWatchParty: (String) -> Unit,
    onUpdateRoomCode: (String) -> Unit,
    onManualSync: () -> Unit,
    onLegacyWatchParty: () -> Unit,
    mediaType: String,
    tmdbId: Int,
    seasonId: String?,
    episodeId: String?,
    streamUrl: String?,
    allProgress: List<com.zstream.android.data.local.entity.ProgressEntity>,
    currentSeason: Int?,
    currentEpisode: Int?,
    onLoadSeason: (Int) -> Unit,
    onSwitchEpisode: (Int, Int) -> Unit,
    poster: String?,
    nav: NavController,
    tvDetail: com.zstream.android.data.model.TvDetail?,
    currentSeasonDetail: com.zstream.android.data.model.Season?,
) {
    val theme = LocalZStreamTheme.current
    val focusManager = LocalFocusManager.current
    var isJoiningRoom by remember { mutableStateOf(false) }
    var joinRoomCode by remember { mutableStateOf("") }
    val joinFocusRequester = remember { FocusRequester() }
    var isLegacyCopied by remember { mutableStateOf(false) }
    val hostGraceProgress by produceState(initialValue = 0f, key1 = hostGraceDeadlineMs) {
        while (hostGraceDeadlineMs != null) {
            val remaining = (hostGraceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
            value = (remaining.toFloat() / 5000f).coerceIn(0f, 1f)
            if (remaining == 0L) break
            delay(100L)
        }
    }

    // Auto-switch to room details when joined
    LaunchedEffect(roomCode) {
        if (roomCode != null && isJoiningRoom) {
            isJoiningRoom = false
            onOpenPage(PlayerMenuPage.WatchParty)
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = MENU_PANEL_WIDTH)
    ) {
        if (page != PlayerMenuPage.Root) {
            PlayerMenuHeader(
                title = when (page) {
                    PlayerMenuPage.Root -> ""
                    PlayerMenuPage.Captions -> "Subtitles"
                    PlayerMenuPage.CaptionLanguage -> captionLanguage?.let(::subtitleLanguageName) ?: "Subtitles"
                    PlayerMenuPage.Playback -> "Playback"
                    PlayerMenuPage.AdvancedColor -> "Advanced Color"
                    PlayerMenuPage.Sources -> "Source"
                    PlayerMenuPage.Quality -> "Quality"
                    PlayerMenuPage.Audio -> "Audio"
                    PlayerMenuPage.Download -> "Download"
                    PlayerMenuPage.WatchParty -> "Watch Party"
                    PlayerMenuPage.SkipSegments -> "Skip Segments"
                    PlayerMenuPage.Seasons -> "Seasons"
                    PlayerMenuPage.Episodes -> currentSeason?.let { "Season $it" } ?: "Episodes"
                },
                showBack = true,
                onBack = onBack,
            )
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "playerMenuPage"
        ) { currentPage ->
            val pageScrollState = rememberScrollState(menuScrollPositions[currentPage] ?: 0)
            DisposableEffect(currentPage, pageScrollState) {
                onDispose {
                    menuScrollPositions[currentPage] = pageScrollState.value
                }
            }
            Column(
                modifier = Modifier
                    .verticalScroll(pageScrollState)
                    .padding(
                        start = PLAYER_MENU_INNER_HORIZONTAL_PADDING,
                        end = PLAYER_MENU_INNER_HORIZONTAL_PADDING,
                        top = 0.dp,
                        bottom = PLAYER_MENU_INNER_BOTTOM_PADDING
                    )
            ) {
            when (currentPage) {
                PlayerMenuPage.Root -> {
                    PlayerMenuGridSection(
                        firstItemFocusRequester = firstItemFocusRequester,
                        items = listOf(
                            PlayerMenuTileItem("Quality", selectedQualityLabel) { onOpenPage(PlayerMenuPage.Quality) },
                            PlayerMenuTileItem("Source", sourceId ?: "Auto") { onOpenPage(PlayerMenuPage.Sources) },
                            PlayerMenuTileItem("Subtitles", selectedSubtitleLanguage?.let(::subtitleLanguageName) ?: "Off") { onOpenPage(PlayerMenuPage.Captions) },
                            PlayerMenuTileItem("Audio", selectedAudioLabel) { onOpenPage(PlayerMenuPage.Audio) },
                        )
                    )
                    PlayerMenuSection {
                        PlayerMenuLinkRow("Download", rightIcon = Icons.Filled.Download) {
                            onOpenPage(PlayerMenuPage.Download)
                        }
                        PlayerMenuLinkRow("Watch Party", rightIcon = Icons.Filled.Group) {
                            onOpenPage(PlayerMenuPage.WatchParty)
                        }
                    }
                    PlayerMenuSection {
                        PlayerMenuChevronRow("Playback") {
                            onOpenPage(PlayerMenuPage.Playback)
                        }
                        PlayerMenuChevronRow("Skip Segments") {
                            onOpenPage(PlayerMenuPage.SkipSegments)
                        }
                    }
                }
                PlayerMenuPage.Captions -> {
                    PlayerMenuSection {
                        PlayerMenuSelectableRow(
                            title = "Off",
                            selected = !subtitlesEnabled || selectedSubtitleLanguage == null,
                            onClick = onDisableSubtitles,
                            focusRequester = firstItemFocusRequester,
                        )
                        if (subtitleTracks.isNotEmpty()) {
                            PlayerMenuSelectableRow(
                                title = "Automatically select subtitles",
                                subtitle = if (subtitlesEnabled) "Select a different subtitle" else null,
                                selected = subtitlesEnabled && selectedSubtitleId != null,
                                onClick = onAutoSelectSubtitle,
                            )
                        }
                    }
                    if (subtitleTracks.isEmpty()) {
                        PlayerMenuStubCard("No subtitles were returned for this stream.")
                    } else {
                        val groups = subtitleTracks.groupBy { it.language }.entries.sortedWith(
                            compareBy<Map.Entry<String, List<SubtitleTrack>>> {
                                if (it.key == settings.applicationLanguage || it.key.startsWith("${settings.applicationLanguage}-")) 0 else 1
                            }.thenBy { subtitleLanguageName(it.key) }
                        )
                        PlayerMenuSection {
                            groups.forEach { (language, tracks) ->
                                PlayerMenuChevronRow(
                                    title = subtitleLanguageName(language),
                                    value = tracks.size.toString(),
                                    onClick = { onOpenCaptionLanguage(language) },
                                )
                            }
                        }
                    }
                }
                PlayerMenuPage.CaptionLanguage -> {
                    val tracks = subtitleTracks.filter { it.language == captionLanguage }.sortedBy {
                        when {
                            it.source?.contains("natsuki", true) == true -> 0
                            it.source?.contains("wyzie", true) == true -> 1
                            it.source?.contains("opensubs", true) == true -> 2
                            else -> 3
                        }
                    }
                    PlayerMenuSection {
                        tracks.forEachIndexed { index, track ->
                            PlayerMenuSelectableRow(
                                title = track.label.ifBlank { "Subtitle ${index + 1}" },
                                selected = subtitlesEnabled && selectedSubtitleId == track.id,
                                onClick = { onSelectSubtitle(track.id) },
                                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                                rightContent = { SubtitleTrackBadges(track) },
                            )
                        }
                    }
                }
                PlayerMenuPage.Playback -> {
                    PlayerMenuSection {
                        PlayerMenuFieldTitle("Speed")
                        Spacer(Modifier.height(12.dp))
                        PlayerMenuSpeedOptions(
                            playbackSpeed = playbackSpeed, 
                            onSetPlaybackSpeed = onSetPlaybackSpeed,
                            firstItemFocusRequester = firstItemFocusRequester
                        )
                    }
                    PlayerMenuSection {
                        PlayerMenuSliderRow(
                            label = "Custom speed",
                            value = playbackSpeed,
                            valueText = "${String.format("%.2f", playbackSpeed)}x",
                            range = PLAYBACK_SPEED_MIN..PLAYBACK_SPEED_MAX,
                            steps = 0,
                            onValueChange = { onSetPlaybackSpeed((it * 20).toInt() / 20f) },
                            onReset = { onSetPlaybackSpeed(1f) },
                            isDefault = playbackSpeed == 1f,
                            tickStep = 0.05f,
                        )
                        PlayerMenuToggleRow("Autoplay next episode", settings.enableAutoplay) {
                            onSetEnableAutoplay(!settings.enableAutoplay)
                        }
                        PlayerMenuSliderRow(
                            label = "Brightness",
                            value = settings.videoBrightness.toFloat(),
                            valueText = "${settings.videoBrightness}%",
                            range = 10f..200f,
                            steps = 0,
                            onValueChange = { onSetVideoBrightness((it / 5).toInt() * 5) },
                            onReset = { onSetVideoBrightness(100) },
                            isDefault = settings.videoBrightness == 100
                        )
                        PlayerMenuToggleRow("Volume Boost", settings.volumeBoost > 100) {
                            onSetVolumeBoost(if (settings.volumeBoost > 100) 100 else 150)
                        }
                        if (settings.volumeBoost > 100) {
                            PlayerMenuSliderRow(
                                label = "Boost level",
                                value = settings.volumeBoost.toFloat(),
                                valueText = "${settings.volumeBoost}%",
                                range = 100f..300f,
                                steps = 0,
                                onValueChange = { onSetVolumeBoost((it / 10).toInt() * 10) },
                                onReset = { onSetVolumeBoost(100) },
                                isDefault = settings.volumeBoost == 100
                            )
                        }
                        PlayerMenuChevronRow("Advanced color", advancedColorSummary(settings)) {
                            onOpenPage(PlayerMenuPage.AdvancedColor)
                        }
                        PlayerMenuFieldTitle("Video mode")
                        Spacer(Modifier.height(12.dp))
                        PlayerMenuSegmentedOptions(
                            options = listOf("fit" to "Fit", "fill" to "Fill", "stretch" to "Stretch"),
                            selected = settings.videoScaleMode,
                            onSelect = onSetVideoScaleMode
                        )
                    }
                }
                PlayerMenuPage.AdvancedColor -> {
                    PlayerMenuSection {
                        PlayerMenuSliderRow(
                            label = "Brightness",
                            value = settings.videoBrightness.toFloat(),
                            valueText = "${settings.videoBrightness}%",
                            range = 10f..200f,
                            steps = 0,
                            onValueChange = { onSetVideoBrightness((it / 5).toInt() * 5) },
                            onReset = { onSetVideoBrightness(100) },
                            isDefault = settings.videoBrightness == 100,
                            focusRequester = firstItemFocusRequester
                        )
                        PlayerMenuSliderRow(
                            label = "Contrast",
                            value = settings.videoContrast.toFloat(),
                            valueText = "${settings.videoContrast}%",
                            range = 50f..200f,
                            steps = 0,
                            onValueChange = { onSetVideoContrast((it / 5).toInt() * 5) },
                            onReset = { onSetVideoContrast(100) },
                            isDefault = settings.videoContrast == 100
                        )
                        PlayerMenuSliderRow(
                            label = "Saturation",
                            value = settings.videoSaturation.toFloat(),
                            valueText = "${settings.videoSaturation}%",
                            range = 0f..200f,
                            steps = 0,
                            onValueChange = { onSetVideoSaturation((it / 5).toInt() * 5) },
                            onReset = { onSetVideoSaturation(100) },
                            isDefault = settings.videoSaturation == 100
                        )
                        PlayerMenuSliderRow(
                            label = "Hue",
                            value = settings.videoHueRotate.toFloat(),
                            valueText = "${settings.videoHueRotate}°",
                            range = -180f..180f,
                            steps = 0,
                            onValueChange = { onSetVideoHueRotate((it / 5).toInt() * 5) },
                            onReset = { onSetVideoHueRotate(0) },
                            isDefault = settings.videoHueRotate == 0
                        )
                        
                        val isTv = LocalIsTv.current
                        var isResetFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            visible = isResetFocused && isTv,
                            shape = RoundedCornerShape(12.dp),
                            outlineColor = Color.White,
                            gap = 2.dp,
                        ) {
                            TextButton(
                                onClick = onResetAdvancedColor,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .onFocusChanged { isResetFocused = it.isFocused }
                                    .focusProperties {
                                        if (isTv) {
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                    }
                            ) {
                                Text("Reset all", color = playerMenuMutedText())
                            }
                        }
                    }
                }

                PlayerMenuPage.Sources -> {
                    PlayerMenuSection {
                        PlayerMenuSummaryCard(
                            title = sourceId ?: "Unknown source",
                            value = embedId ?: "No embed metadata",
                            subtitle = if (manualSourceSelection) "Pick a source to retry playback through that provider." else "Enable Manual Source Selection in settings to choose providers here."
                        )
                    }
                    if (manualSourceSelection) {
                        PlayerMenuSection {
                            sourceResults.forEachIndexed { index, source ->
                                PlayerMenuSelectableRow(
                                    title = source.id,
                                    subtitle = source.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = source.id == sourceId,
                                    onClick = {
                                        onClose()
                                        onSelectSource(source.id)
                                    },
                                    focusRequester = if (index == 0) firstItemFocusRequester else null
                                )
                            }
                        }
                        if (sourceResults.isEmpty()) {
                            PlayerMenuStubCard("No sources are available for manual selection yet.")
                        }
                    } else {
                        PlayerMenuStubCard("Manual source selection is currently disabled in app settings.")
                    }
                }
                PlayerMenuPage.Quality -> {
                    PlayerMenuSection {
                        PlayerMenuSelectableRow(
                            title = "Auto",
                            subtitle = "Adaptive streaming",
                            selected = qualityOptions.none { it.selected },
                            onClick = onSelectAutoQuality,
                            focusRequester = firstItemFocusRequester
                        )
                        qualityOptions.forEach { option ->
                            PlayerMenuSelectableRow(
                                title = option.label,
                                subtitle = "${option.height}p",
                                selected = option.selected,
                                onClick = { onSelectQuality(option) }
                            )
                        }
                    }
                    if (qualityOptions.isEmpty()) {
                        PlayerMenuStubCard("No manual quality tracks exposed by Media3 for this stream.")
                    }
                }
                PlayerMenuPage.Audio -> {
                    PlayerMenuSection {
                        audioOptions.forEachIndexed { index, option ->
                            PlayerMenuSelectableRow(
                                title = option.label,
                                subtitle = option.language ?: "Unknown",
                                selected = option.selected,
                                onClick = { onSelectAudio(option) },
                                focusRequester = if (index == 0) firstItemFocusRequester else null
                            )
                        }
                    }
                    if (audioOptions.isEmpty()) {
                        PlayerMenuStubCard("No selectable audio tracks exposed by Media3 for this stream.")
                    }
                }
                PlayerMenuPage.Download -> PlayerMenuStubCard("Download UI is prepared. Source-specific download API wiring is still stubbed.")
                PlayerMenuPage.WatchParty -> {
                    if (roomCode == null) {
                        PlayerMenuSection {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ZsButton(
                                    text = "Host a Watch Party",
                                    onClick = {
                                        isJoiningRoom = false
                                        joinRoomCode = ""
                                        focusManager.clearFocus()
                                        onHostWatchParty()
                                    },
                                    variant = ZsButtonVariant.Purple,
                                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                                )

                                if (isOffline) {
                                    ZsStatusBanner(
                                        message = "Failed to connect to room",
                                        variant = ZsStatusBannerVariant.Error,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                if (isJoiningRoom) {
                                    BasicTextField(
                                        value = joinRoomCode,
                                        onValueChange = { input ->
                                            val filtered = input.uppercase().filter { it.isLetterOrDigit() }
                                            if (filtered.length <= 6) joinRoomCode = filtered
                                        },
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .width(160.dp)
                                            .height(48.dp)
                                            .background(theme.colors.modal.background, RoundedCornerShape(8.dp))
                                            .border(1.dp, theme.colors.type.emphasis.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .focusRequester(joinFocusRequester),
                                        textStyle = TextStyle(
                                            color = theme.colors.type.emphasis,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 4.sp
                                        ),
                                        cursorBrush = SolidColor(theme.colors.buttons.purple),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (joinRoomCode.length >= 4) {
                                                    onJoinWatchParty(joinRoomCode)
                                                }
                                                isJoiningRoom = false
                                                focusManager.clearFocus()
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.Center) {
                                                if (joinRoomCode.isEmpty()) {
                                                    Text("CODE", color = playerMenuMutedText(), fontSize = 16.sp)
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    LaunchedEffect(Unit) { joinFocusRequester.requestFocus() }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = {
                                            isJoiningRoom = false
                                            joinRoomCode = ""
                                            focusManager.clearFocus()
                                        }) {
                                            Text("Cancel", color = theme.colors.type.secondary, fontSize = 12.sp)
                                        }

                                        ZsButton(
                                            text = "Join",
                                            onClick = {
                                                if (joinRoomCode.length >= 4) {
                                                    onJoinWatchParty(joinRoomCode)
                                                }
                                                isJoiningRoom = false
                                                focusManager.clearFocus()
                                            },
                                            variant = ZsButtonVariant.Secondary,
                                            modifier = Modifier.height(42.dp)
                                        )
                                    }
                                } else {
                                    ZsButton(
                                        text = "Join Watch Party",
                                        onClick = { isJoiningRoom = true },
                                        variant = ZsButtonVariant.Secondary,
                                        modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                                    )
                                }

                                ZsButton(
                                    text = if (isLegacyCopied) "Link Copied!" else "Use Legacy Watch Party",
                                    onClick = {
                                        onLegacyWatchParty()
                                        isLegacyCopied = true
                                    },
                                    variant = if (isLegacyCopied) ZsButtonVariant.Secondary else ZsButtonVariant.Secondary,
                                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                                )
                                LaunchedEffect(isLegacyCopied) {
                                    if (isLegacyCopied) {
                                        delay(2000)
                                        isLegacyCopied = false
                                    }
                                }
                                Text(
                                    text = "Legacy Watch Party might not be available for some sources",
                                    color = playerMenuMutedText(),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        val clipboard = LocalClipboardManager.current
                        val focusManager = LocalFocusManager.current
                        val focusRequester = remember { FocusRequester() }
                        var isEditing by remember { mutableStateOf(false) }
                        var roomCodeValue by remember { mutableStateOf(TextFieldValue(roomCode ?: "")) }

                        LaunchedEffect(isEditing) {
                            if (isEditing) {
                                focusRequester.requestFocus()
                            }
                        }
                        LaunchedEffect(roomCode) {
                            roomCodeValue = TextFieldValue(roomCode ?: "")
                        }

                        PlayerMenuSection {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Main Action Card
                                Surface(
                                    color = theme.colors.background.secondary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF22C55E), CircleShape)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (isHost) "Hosting" else "Viewing",
                                                    color = theme.colors.type.emphasis,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            if (isHost) {
                                                IconButton(
                                                    onClick = {
                                                        isEditing = !isEditing
                                                        if (isEditing) {
                                                            roomCodeValue = roomCodeValue.copy(
                                                                selection = TextRange(0, roomCodeValue.text.length)
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit",
                                                        tint = if (isEditing) theme.colors.buttons.purple else theme.colors.type.secondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isEditing) {
                                            BasicTextField(
                                                value = roomCodeValue,
                                                onValueChange = { input ->
                                                    val filtered = input.text.uppercase().filter { it.isLetterOrDigit() }
                                                    if (filtered.length <= 6) {
                                                        roomCodeValue = input.copy(text = filtered)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester),
                                                textStyle = TextStyle(
                                                    color = theme.colors.buttons.purple,
                                                    fontSize = 38.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                cursorBrush = SolidColor(theme.colors.type.emphasis),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    onUpdateRoomCode(roomCodeValue.text)
                                                    isEditing = false
                                                    focusManager.clearFocus()
                                                }),
                                                singleLine = true
                                            )
                                        } else if (isRegistering) {
                                            Text(
                                                text = "Registering Watch Party...",
                                                color = theme.colors.buttons.secondary.copy(alpha = 0.7f),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            Text(
                                                text = roomCode ?: "",
                                                color = theme.colors.buttons.purple,
                                                fontSize = 38.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        if (hostGraceDeadlineMs != null) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Host offline grace period",
                                                    color = theme.colors.type.secondary,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                LinearProgressIndicator(
                                                    progress = { hostGraceProgress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = theme.colors.buttons.secondary,
                                                    trackColor = theme.colors.background.main.copy(alpha = 0.4f)
                                                )
                                            }
                                        }

                                        if (durationMismatch) {
                                            Text(
                                                text = "Duration mismatch detected. Drift sync is limited.",
                                                color = theme.colors.type.secondary,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Copy Code Tile
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { clipboard.setText(AnnotatedString(roomCodeValue.text)) },
                                                color = theme.colors.background.main.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = null,
                                                        tint = theme.colors.type.secondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "Copy Code",
                                                        color = theme.colors.type.secondary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            // Copy Link Tile
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { 
                                                        val baseUrl = "https://zstream.mov/media/"
                                                        val slug = title.slugify()
                                                        val typeKey = if (mediaType == "movie") "movie" else "tv"
                                                        val mediaPath = if (mediaType == "movie") {
                                                            "tmdb-$typeKey-$tmdbId-$slug"
                                                        } else {
                                                            "tmdb-$typeKey-$tmdbId-$slug/${seasonId ?: ""}/${episodeId ?: ""}"
                                                        }
                                                        val fullUrl = "$baseUrl$mediaPath?watchparty=${roomCode ?: ""}"
                                                        clipboard.setText(AnnotatedString(fullUrl))
                                                    },
                                                color = theme.colors.background.main.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Link,
                                                        contentDescription = null,
                                                        tint = theme.colors.type.secondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "Copy Link",
                                                        color = theme.colors.type.secondary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                PlayerMenuSectionTitle(if (participants.size <= 1) "ALONE" else "${participants.size} PARTICIPANTS")
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    participants.forEach { participant ->
                                        val isMe = participant.userId == myUserId
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(theme.colors.background.secondary.copy(alpha = 0.3f))
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = Icons.Default.Group,
                                                        contentDescription = null,
                                                        tint = if (participant.isHost) Color(0xFFFFD700) else theme.colors.type.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = buildString {
                                                            if (isMe) append("You") else append(participant.userId.take(12))
                                                            if (participant.isHost) append(" (Host)")
                                                            if (participant.isStale) append(" - reconnecting")
                                                        },
                                                        color = if (participant.isHost) Color(0xFFFFD700) else theme.colors.type.emphasis,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (participant.isHost || isMe) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = if (participant.duration > 0) {
                                                        "${((participant.time * 100) / participant.duration).toInt()}%"
                                                    } else {
                                                        "${participant.time.toInt()}s"
                                                    },
                                                    color = playerMenuMutedText(),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                ZsButton(
                                    text = "Leave Watch Party",
                                    onClick = {
                                        onLeaveWatchParty()
                                        onBack()
                                    },
                                    variant = ZsButtonVariant.Danger,
                                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
                PlayerMenuPage.Seasons -> {
                    PlayerMenuSection {
                        tvDetail?.seasons?.forEachIndexed { index, season ->
                            PlayerMenuSelectableRow(
                                title = if (season.seasonNumber == 0) "Specials" else "Season ${season.seasonNumber}",
                                subtitle = "${season.episodeCount} Episodes",
                                selected = season.seasonNumber == currentSeason,
                                onClick = {
                                    onLoadSeason(season.seasonNumber)
                                    onOpenPage(PlayerMenuPage.Episodes)
                                },
                                focusRequester = if (index == 0) firstItemFocusRequester else null
                            )
                        }
                    }
                    if (tvDetail?.seasons.isNullOrEmpty()) {
                        PlayerMenuStubCard("No seasons found.")
                    }
                }
                PlayerMenuPage.Episodes -> {
                    PlayerMenuSection {
                        val episodes = currentSeasonDetail?.episodes.orEmpty()
                        episodes.forEachIndexed { index, ep ->
                            val progress = allProgress.firstOrNull { it.tmdbId == tmdbId.toString() && it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber }
                            SharedEpisodeRow(
                                ep = ep,
                                showId = tmdbId,
                                title = title,
                                posterPath = poster,
                                nav = nav,
                                theme = theme,
                                episodeProgress = progress,
                                onEpisodeClick = {
                                    onSwitchEpisode(ep.seasonNumber, ep.episodeNumber)
                                },
                                compact = true,
                                horizontalPadding = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .then(if (index == episodes.lastIndex) Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier)
                            )
                        }
                    }
                    if (currentSeasonDetail?.episodes.isNullOrEmpty()) {
                        PlayerMenuStubCard("No episodes found for this season.")
                    }
                }
                PlayerMenuPage.SkipSegments -> {
                    PlayerMenuSection {
                        if (canSubmitSkipSegments) {
                            if (hasTidbKey) {
                                val isTv = LocalIsTv.current
                                var isFocused by remember { mutableStateOf(false) }
                                ZsOutlinedWrapper(
                                    visible = isFocused && isTv,
                                    shape = RoundedCornerShape(12.dp),
                                    outlineColor = Color.White,
                                    gap = 2.dp,
                                ) {
                                    ZsButton(
                                        text = "Submit Segment",
                                        onClick = { onOpenSkipSubmission(null) },
                                        variant = ZsButtonVariant.Purple,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (firstItemFocusRequester != null) Modifier.focusRequester(
                                                    firstItemFocusRequester
                                                ) else Modifier
                                            )
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusProperties {
                                                if (isTv) {
                                                    left = FocusRequester.Cancel
                                                    right = FocusRequester.Cancel
                                                }
                                            },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            } else {
                                PlayerMenuStubCard("To submit new segments, enter your TheIntroDB API key in the connections settings.")
                            }
                        }
                        if (skipSegments.isEmpty()) {
                            PlayerMenuStubCard("No skip segments available.")
                        } else {
                            skipSegments.forEachIndexed { index, segment ->
                                PlayerMenuSkipSegmentRow(
                                    segment = segment,
                                    onSeek = { onSeekToMs(segment.startMs ?: 0L) },
                                    onOpenSubmission = { onOpenSkipSubmission(segment) },
                                    canSubmit = canSubmitSkipSegments && hasTidbKey,
                                    focusRequester = if (index == 0 && !(canSubmitSkipSegments && hasTidbKey)) firstItemFocusRequester else null
                                )
                                if (index < skipSegments.size - 1) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PlayerMenuHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYER_MENU_INNER_HORIZONTAL_PADDING)
            .padding(top = 20.dp, bottom = 14.dp)
            .border(width = 1.dp, color = Color.Transparent)
            .padding(bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .border(width = 0.dp, color = Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                var isFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    visible = isFocused && isTv,
                    shape = CircleShape,
                    outlineColor = Color.White,
                    gap = 2.dp,
                ) {
                    ZsIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        variant = ZsIconButtonVariant.Ghost,
                        containerSize = 32.dp,
                        iconSize = 18.dp,
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusProperties { if (isTv) left = FocusRequester.Cancel }
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = title,
                color = theme.colors.type.emphasis,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYER_MENU_INNER_HORIZONTAL_PADDING)
            .height(1.dp)
            .background(playerMenuSectionDivider())
    )
}

@Composable
private fun PlayerMenuSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun PlayerMenuGridSection(items: List<PlayerMenuTileItem>, firstItemFocusRequester: FocusRequester? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    Box(modifier = Modifier.weight(1f)) {
                        PlayerMenuBoxNavTile(
                            title = item.title,
                            value = item.value,
                            onClick = item.onClick,
                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstItemFocusRequester else null,
                            isLeft = colIndex == 0,
                            isRight = colIndex == rowItems.size - 1
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = playerMenuMutedText(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun PlayerMenuSummaryCard(title: String, value: String, subtitle: String) {
    val theme = LocalZStreamTheme.current
    Surface(color = playerMenuCardFill(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(14.dp)) {
            Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = playerMenuMutedText(), fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = playerMenuDimText(), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerMenuBoxNavTile(
    title: String, 
    value: String, 
    onClick: () -> Unit, 
    focusRequester: FocusRequester? = null,
    isLeft: Boolean = false,
    isRight: Boolean = false
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(18.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        Surface(
            color = playerMenuCardFill(),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(PLAYER_MENU_BOX_TILE_HEIGHT)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties {
                    if (isTv) {
                        if (isLeft) left = FocusRequester.Cancel
                        if (isRight) right = FocusRequester.Cancel
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    color = playerMenuMutedText(),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PlayerMenuFieldTitle(text: String) {
    Text(text, color = LocalZStreamTheme.current.colors.type.emphasis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
}

@Composable
private fun PlayerMenuSpeedOptions(playbackSpeed: Float, onSetPlaybackSpeed: (Float) -> Unit, firstItemFocusRequester: FocusRequester? = null) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(playerMenuCardFill(), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(0.25f, 0.5f, 1f, 1.5f, 2f).forEachIndexed { index, speed ->
            val selected = playbackSpeed == speed
            TextButton(
                onClick = { onSetPlaybackSpeed(speed) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) playerMenuCardActiveFill() else Color.Transparent,
                    contentColor = theme.colors.type.emphasis
                ),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(
                            firstItemFocusRequester
                        ) else Modifier
                    )
                    .focusProperties {
                        if (isTv) {
                            if (index == 0) left = FocusRequester.Cancel
                            if (index == 4) right = FocusRequester.Cancel
                        }
                    }
            ) {
                Text("${speed}x", color = if (selected) theme.colors.type.emphasis else playerMenuMutedText())
            }
        }
    }
}

@Composable
private fun PlayerMenuSegmentedOptions(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(playerMenuCardFill(), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = selected.equals(value, ignoreCase = true)
            TextButton(
                onClick = { onSelect(value) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (isSelected) playerMenuCardActiveFill() else Color.Transparent,
                    contentColor = theme.colors.type.emphasis
                ),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        if (isTv) {
                            if (index == 0) left = FocusRequester.Cancel
                            if (index == options.size - 1) right = FocusRequester.Cancel
                        }
                    }
            ) {
                Text(label, color = if (isSelected) theme.colors.type.emphasis else playerMenuMutedText())
            }
        }
    }
}

@Composable
private fun PlayerMenuChevronRow(title: String, value: String? = null, onClick: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, color = theme.colors.type.emphasis, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                if (!value.isNullOrBlank()) {
                    Text(
                        value,
                        color = playerMenuMutedText(),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(Icons.Filled.ChevronRight, null, tint = theme.colors.type.emphasis, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PlayerMenuLinkRow(title: String, rightIcon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, color = theme.colors.type.emphasis, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(rightIcon, null, tint = theme.colors.type.emphasis, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PlayerMenuToggleRow(title: String, checked: Boolean, focusRequester: FocusRequester? = null, onToggle: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = theme.colors.type.emphasis, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = theme.colors.type.emphasis,
                    checkedTrackColor = theme.colors.global.accentA,
                    uncheckedThumbColor = theme.colors.type.dimmed,
                    uncheckedTrackColor = theme.colors.background.secondary,
                ),
            )
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
    tickStep: Float? = null,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    var isAdjusting by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) { if (!isAdjusting) sliderValue = value }

    val bgColor by animateColorAsState(
        if (isAdjusting) theme.colors.global.accentA.copy(alpha = 0.2f)
        else if (isFocused) theme.colors.background.secondary.copy(alpha = 0.5f)
        else Color.Transparent
    )

    val stepAmount = tickStep ?: (range.endInclusive - range.start) * 0.05f

    ZsOutlinedWrapper(
        shape = RoundedCornerShape(8.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 4.dp,
        vertical = 2.dp,
        visible = (isFocused || isAdjusting) && isTv,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (!it.isFocused) isAdjusting = false
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (isAdjusting) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                sliderValue = (sliderValue + stepAmount).coerceIn(
                                    range.start,
                                    range.endInclusive
                                )
                                onValueChange(sliderValue)
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                sliderValue = (sliderValue - stepAmount).coerceIn(
                                    range.start,
                                    range.endInclusive
                                )
                                onValueChange(sliderValue)
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_BUTTON_A,
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                isAdjusting = false
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> true

                            else -> false
                        }
                    } else {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                                isAdjusting = true
                                true
                            }

                            else -> false
                        }
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = playerMenuMutedText(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(valueText, color = theme.colors.type.emphasis, fontSize = 13.sp)
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isDefault) {
                            TextButton(onClick = onReset, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Filled.Close, null, tint = playerMenuMutedText(), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Slider(
                value = sliderValue.coerceIn(range.start, range.endInclusive),
                onValueChange = { sliderValue = it; onValueChange(it) },
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = if (isAdjusting) Color.White else theme.colors.type.dimmed,
                    activeTrackColor = if (isAdjusting) theme.colors.global.accentA else theme.colors.global.accentA.copy(alpha = 0.6f),
                    inactiveTrackColor = theme.colors.progress.background.copy(alpha = 0.35f),
                ),
                enabled = !isTv || isAdjusting,
            )
        }
    }
}

@Composable
private fun PlayerMenuSelectableRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    rightContent: (@Composable RowScope.() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(8.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 4.dp,
        vertical = 2.dp,
    ) {
        Surface(
            color = if (selected) playerMenuCardActiveFill() else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.emphasis.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (selected) theme.colors.type.emphasis else theme.colors.type.emphasis.copy(alpha = 0.96f))
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, color = playerMenuDimText(), fontSize = 12.sp)
                    }
                }
                if (rightContent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, content = rightContent)
                }
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, null, tint = theme.colors.type.success, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuSkipSegmentRow(
    segment: SkipSegment,
    onSeek: () -> Unit,
    onOpenSubmission: () -> Unit,
    canSubmit: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }

    ZsOutlinedWrapper(
        visible = isFocused && isTv,
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        gap = 2.dp,
    ) {
        ZsCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .focusProperties {
                    if (isTv) {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                }
                .clickable {
                    onSeek()
                    if (canSubmit) {
                        onOpenSubmission()
                    }
                },
            variant = ZsCardVariant.Elevated,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    skipSegmentLabel(segment.type),
                    color = theme.colors.video.context.type.main,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${formatTime(segment.startMs ?: 0L)} - ${segment.endMs?.let(::formatTime) ?: "End of video"}",
                    color = theme.colors.video.context.type.secondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ManualSourceStatusContent(source: SourceResult, onUse: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val statusColor = sourceStatusColor(theme, source.status)
    when (source.status) {
        SourceStatus.IDLE -> Unit
        SourceStatus.TRYING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = theme.colors.type.emphasis,
                strokeWidth = 2.dp
            )
        }
        SourceStatus.FAILED -> {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(2.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(2.dp)
                        .background(statusColor, RoundedCornerShape(999.dp))
                )
            }
        }
        SourceStatus.SUCCESS -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, null, tint = statusColor, modifier = Modifier.size(18.dp))
                TextButton(
                    onClick = onUse,
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.colors.type.emphasis),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.emphasis.copy(alpha = 0.15f)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Use", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun sourceDisplayName(id: String): String = when (id.lowercase()) {
    "vidlink" -> "VidLink"
    else -> id.split('-', '_').joinToString(" ") { token ->
        token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
private fun PlayerMenuStubCard(message: String) {
    val theme = LocalZStreamTheme.current
    Surface(
        color = theme.colors.background.secondary.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(message, color = playerMenuMutedText(), fontSize = 12.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PlayerMenuSourceRow(source: SourceResult) {
    val theme = LocalZStreamTheme.current
    val color = sourceStatusColor(theme, source.status)
    Surface(
        color = theme.colors.background.secondary.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier
                .size(8.dp)
                .background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(source.id, color = theme.colors.type.emphasis, modifier = Modifier.weight(1f))
            Text(source.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = playerMenuMutedText(), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerMenuHintText(text: String) {
    Text(
        text = text,
        color = playerMenuMutedText(),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 18.dp)
    )
}

@Composable
private fun PlayerInfoSheet(
    state: PlayerInfoState?,
    nav: NavController,
    allProgress: List<com.zstream.android.data.local.entity.ProgressEntity>,
    showImageLogos: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onClose: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val theme = LocalZStreamTheme.current
    when (state) {
        null, PlayerInfoState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.colors.type.emphasis)
        }
        is PlayerInfoState.Error -> Box(Modifier
            .fillMaxSize()
            .padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = theme.colors.type.emphasis)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        is PlayerInfoState.Movie -> {
            MovieDetailModal(
                detail = state.detail,
                nav = nav,
                context = context,
                theme = theme,
                isBookmarked = isBookmarked,
                onToggleBookmark = onToggleBookmark,
                hasProgress = false, // Not relevant for player info sheet as we're already playing
                onBack = onClose,
                onMarkMovieWatched = {}, // Stubs for player sheet
                onClearMovieWatchHistory = {},
                showImageLogos = showImageLogos,
                showPlayButton = false,
                firstItemFocusRequester = firstItemFocusRequester
            )
        }
        is PlayerInfoState.Tv -> {
            TvDetailModal(
                detail = state.detail,
                selectedSeason = state.selectedSeason,
                allProgress = allProgress,
                nav = nav,
                context = context,
                theme = theme,
                isBookmarked = isBookmarked,
                onToggleBookmark = onToggleBookmark,
                hasProgress = false,
                resumeProgress = null,
                onBack = onClose,
                onSelectSeason = onSelectSeason,
                onMarkEpisodeWatched = {},
                onClearEpisodeWatchHistory = {},
                onMarkSeasonWatched = {},
                onClearSeasonWatchHistory = {},
                showImageLogos = showImageLogos,
                showPlayButton = false,
                firstItemFocusRequester = firstItemFocusRequester
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class, UnstableApi::class)
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

@OptIn(ExperimentalComposeUiApi::class, UnstableApi::class)
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
