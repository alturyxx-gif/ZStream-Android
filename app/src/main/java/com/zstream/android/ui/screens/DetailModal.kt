package com.zstream.android.ui.screens

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.R
import com.zstream.android.Urls
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.CastMember
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.Media
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TrailerData
import com.zstream.android.data.model.TvDetail
import com.zstream.android.data.model.airedEpisodes
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsBottomSheetSectionHeader
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import kotlinx.coroutines.launch

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

enum class WatchBulkTarget { Movie, Season }
enum class WatchBulkAction { MarkWatched, ClearHistory }

@Composable
fun MovieDetailModal(
    detail: MovieDetail,
    nav: NavController,
    context: android.content.Context,
    theme: ZStreamTheme,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    hasProgress: Boolean,
    onBack: () -> Unit,
    onMarkMovieWatched: () -> Unit,
    onClearMovieWatchHistory: () -> Unit,
    showPlayButton: Boolean = true,
    showBackground: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var pendingBulkTarget by remember { mutableStateOf<WatchBulkTarget?>(null) }
    var pendingBulkAction by remember { mutableStateOf<WatchBulkAction?>(null) }
    val isTv = LocalIsTv.current
    val scrollState = rememberScrollState()
    
    var scrollRequestId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scrollRequestId) {
        if (scrollRequestId > 0) {
            scrollState.animateScrollTo(0)
        }
    }

    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null && isTv) {
            kotlinx.coroutines.delay(100)
            firstItemFocusRequester.requestFocus()
        }
    }

    SharedDetailSheetScaffold(
        title = detail.title,
        backdropUrl = detail.backdropUrl(),
        logoUrl = detail.logoUrl(),
        posterUrl = detail.posterUrl(),
        year = detail.releaseDate?.take(4),
        rating = detail.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = onBack,
        modifier = if (showBackground) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier,
        scrollState = scrollState
    ) {
        SharedMovieDetailContent(
            detail = detail,
            context = context,
            nav = nav,
            theme = theme,
            firstItemFocusRequester = firstItemFocusRequester,
            specActions = { },
        ) { requester ->
            if (showPlayButton) {
                var playFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    visible = playFocused && isTv,
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    horizontal = 3.dp,
                    vertical = (-1).dp,
                ) {
                    Button(
                        onClick = { nav.navigate("player/movie/${detail.id}?title=${detail.title.encode()}&year=${detail.releaseDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.buttons.purple,
                            contentColor = theme.colors.type.emphasis,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .widthIn(min = 120.dp)
                            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                            .onFocusChanged { 
                                playFocused = it.isFocused
                                if (it.isFocused && isTv) {
                                    scrollRequestId++
                                }
                            }
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.type.emphasis)
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasProgress) "Resume" else "Play", color = theme.colors.type.emphasis)
                    }
                }
            }

            if (!isTv) {
                SharedActionPill(Icons.Filled.Share, theme, focusRequester = if (!showPlayButton) requester else null, onFocusChanged = { focused ->
                    if (focused && isTv) scrollRequestId++
                }) {
                    openShareSheet(context, detail.title, detail.id, "movie")
                }
            }
            
            val bookmarkIcon = if (isBookmarked) ImageVector.vectorResource(R.drawable.ic_player_bookmark_filled) else ImageVector.vectorResource(R.drawable.ic_player_bookmark_outline)
            SharedActionPill(bookmarkIcon, theme, focusRequester = if (isTv && !showPlayButton) requester else null, onFocusChanged = { focused ->
                if (focused && isTv) scrollRequestId++
            }) {
                onToggleBookmark()
            }

            SharedActionPill(
                icon = Icons.Filled.RemoveRedEye,
                theme = theme,
                onFocusChanged = { focused ->
                    if (focused && isTv) scrollRequestId++
                },
                onClick = { pendingBulkTarget = WatchBulkTarget.Movie },
            )
        }
    }

    WatchBulkDialogs(
        target = pendingBulkTarget,
        pendingAction = pendingBulkAction,
        theme = theme,
        onDismiss = {
            pendingBulkTarget = null
            pendingBulkAction = null
        },
        onChooseAction = { pendingBulkAction = it },
        onConfirm = { action ->
            when (action) {
                WatchBulkAction.MarkWatched -> onMarkMovieWatched()
                WatchBulkAction.ClearHistory -> onClearMovieWatchHistory()
            }
            pendingBulkTarget = null
            pendingBulkAction = null
        },
    )
}

@Composable
fun TvDetailModal(
    detail: TvDetail,
    selectedSeason: Season?,
    allProgress: List<ProgressEntity>,
    nav: NavController,
    context: android.content.Context,
    theme: ZStreamTheme,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    hasProgress: Boolean,
    resumeProgress: ProgressEntity?,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onMarkEpisodeWatched: (com.zstream.android.data.model.Episode) -> Unit,
    onClearEpisodeWatchHistory: (com.zstream.android.data.model.Episode) -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onClearSeasonWatchHistory: () -> Unit,
    showPlayButton: Boolean = true,
    showBackground: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var pendingBulkTarget by remember { mutableStateOf<WatchBulkTarget?>(null) }
    var pendingBulkAction by remember { mutableStateOf<WatchBulkAction?>(null) }
    val isTv = LocalIsTv.current
    val scrollState = rememberScrollState()

    var scrollRequestId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scrollRequestId) {
        if (scrollRequestId > 0) {
            scrollState.animateScrollTo(0)
        }
    }

    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null && isTv) {
            kotlinx.coroutines.delay(100)
            firstItemFocusRequester.requestFocus()
        }
    }

    SharedDetailSheetScaffold(
        title = detail.name,
        backdropUrl = detail.backdropUrl(),
        logoUrl = detail.logoUrl(),
        posterUrl = detail.posterUrl(),
        year = detail.firstAirDate?.take(4),
        rating = detail.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = onBack,
        modifier = if (showBackground) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier,
        scrollState = scrollState
    ) {
        SharedTvDetailContent(
            detail = detail,
            selectedSeason = selectedSeason,
            allProgress = allProgress,
            context = context,
            nav = nav,
            theme = theme,
            onSelectSeason = onSelectSeason,
            onMarkEpisodeWatched = onMarkEpisodeWatched,
            onClearEpisodeWatchHistory = onClearEpisodeWatchHistory,
            firstItemFocusRequester = firstItemFocusRequester,
            specActions = { },
        ) { requester ->
            if (showPlayButton) {
                var playFocused by remember { mutableStateOf(false) }

                val label = if (hasProgress && resumeProgress != null) {
                    val sNum = resumeProgress.seasonNumber
                    val eNum = resumeProgress.episodeNumber
                    if (sNum != null && eNum != null) "Resume S${sNum}:E${eNum}" else "Resume"
                } else "Play"
                
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    visible = playFocused && isTv,
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    horizontal = 3.dp,
                    vertical = (-1).dp,
                ) {
                    Button(
                        onClick = {
                            if (hasProgress && resumeProgress != null) {
                                val sNum = resumeProgress.seasonNumber ?: selectedSeason?.seasonNumber ?: 1
                                val eNum = resumeProgress.episodeNumber ?: 1
                                nav.navigate("player/tv/${detail.id}?season=$sNum&episode=$eNum&title=${detail.name.encode()}&year=${detail.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
                            } else {
                                val firstEp = selectedSeason?.episodes?.airedEpisodes()?.firstOrNull()
                                if (firstEp != null) {
                                    nav.navigate("player/tv/${detail.id}?season=${firstEp.seasonNumber}&episode=${firstEp.episodeNumber}&title=${detail.name.encode()}&year=${detail.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.buttons.purple,
                            contentColor = theme.colors.type.emphasis,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .widthIn(min = 120.dp)
                            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                            .onFocusChanged { 
                                playFocused = it.isFocused
                                if (it.isFocused && isTv) {
                                    scrollRequestId++
                                }
                            }
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.type.emphasis)
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = theme.colors.type.emphasis)
                    }
                }
            }

            if (!isTv) {
                SharedActionPill(Icons.Filled.Share, theme, focusRequester = if (!showPlayButton) requester else null, onFocusChanged = { focused ->
                    if (focused && isTv) scrollRequestId++
                }) {
                    openShareSheet(context, detail.name, detail.id, "tv")
                }
            }

            val bookmarkIcon = if (isBookmarked) ImageVector.vectorResource(R.drawable.ic_player_bookmark_filled) else ImageVector.vectorResource(R.drawable.ic_player_bookmark_outline)
            SharedActionPill(bookmarkIcon, theme, focusRequester = if (isTv && !showPlayButton) requester else null, onFocusChanged = { focused ->
                if (focused && isTv) scrollRequestId++
            }) {
                onToggleBookmark()
            }

            SharedActionPill(
                icon = Icons.Filled.RemoveRedEye,
                theme = theme,
                onFocusChanged = { focused ->
                    if (focused && isTv) scrollRequestId++
                },
                onClick = { pendingBulkTarget = WatchBulkTarget.Season },
            )
        }
    }

    WatchBulkDialogs(
        target = pendingBulkTarget,
        pendingAction = pendingBulkAction,
        theme = theme,
        onDismiss = {
            pendingBulkTarget = null
            pendingBulkAction = null
        },
        onChooseAction = { pendingBulkAction = it },
        onConfirm = { action ->
            when (action) {
                WatchBulkAction.MarkWatched -> onMarkSeasonWatched()
                WatchBulkAction.ClearHistory -> onClearSeasonWatchHistory()
            }
            pendingBulkTarget = null
            pendingBulkAction = null
        },
    )
}

@Composable
fun WatchBulkDialogs(
    target: WatchBulkTarget?,
    pendingAction: WatchBulkAction?,
    theme: ZStreamTheme,
    onDismiss: () -> Unit,
    onChooseAction: (WatchBulkAction) -> Unit,
    onConfirm: (WatchBulkAction) -> Unit,
) {
    val subject = when (target) {
        WatchBulkTarget.Movie -> "movie"
        WatchBulkTarget.Season -> "season"
        null -> null
    }

    if (target != null && pendingAction == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = theme.colors.modal.background,
            title = { Text("Update watch history", color = theme.colors.type.emphasis) },
            text = { Text("Choose what to do with this $subject.", color = theme.colors.type.text) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChooseAction(WatchBulkAction.MarkWatched) }) {
                        Text("Set watched", color = theme.colors.global.accentA)
                    }
                    TextButton(onClick = { onChooseAction(WatchBulkAction.ClearHistory) }) {
                        Text("Clear history", color = theme.colors.buttons.danger)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = theme.colors.type.secondary)
                }
            }
        )
    }

    if (target != null && pendingAction != null) {
        val actionLabel = if (pendingAction == WatchBulkAction.MarkWatched) "set watched" else "clear watch history"
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = theme.colors.modal.background,
            title = { Text("Confirm action", color = theme.colors.type.emphasis) },
            text = { Text("Are you sure you want to $actionLabel for this $subject?", color = theme.colors.type.text) },
            confirmButton = {
                TextButton(onClick = { onConfirm(pendingAction) }) {
                    Text(
                        "Confirm",
                        color = if (pendingAction == WatchBulkAction.ClearHistory) theme.colors.buttons.danger else theme.colors.global.accentA
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = theme.colors.type.secondary)
                }
            }
        )
    }
}

internal fun openShareSheet(context: android.content.Context, title: String, id: Int, mediaType: String) {
   val url = "https://www.themoviedb.org/$mediaType/$id"
   val shareText = "$title on ZStream!\n\n$url"
   val intent = android.content.Intent().apply {
       action = android.content.Intent.ACTION_SEND
       putExtra(android.content.Intent.EXTRA_TEXT, shareText)
       type = "text/plain"
   }
   val chooser = android.content.Intent.createChooser(intent, "Share")
   context.startActivity(chooser)
}

internal fun openCastProfile(context: android.content.Context, castId: Int, imdbId: String?) {
    val url = if (!imdbId.isNullOrEmpty()) {
        "https://www.imdb.com/name/$imdbId/"
    } else {
        "https://www.themoviedb.org/person/$castId"
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

internal fun openYoutubeTrailer(context: android.content.Context, youtubeKey: String) {
    val url = "https://www.youtube.com/watch?v=$youtubeKey"
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedEpisodeRow(
    ep: com.zstream.android.data.model.Episode,
    showId: Int,
    title: String,
    posterPath: String?,
    nav: NavController,
    theme: ZStreamTheme,
    episodeProgress: ProgressEntity?,
    enableWatchActions: Boolean = false,
    onMarkWatched: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pct = if (episodeProgress != null && episodeProgress.duration > 0) {
        ((episodeProgress.watched.toFloat() / episodeProgress.duration) * 100f).coerceIn(0f, 100f)
    } else 0f
    val isWatched = episodeProgress?.let { it.duration > 0 && it.watched >= (it.duration * 0.95f) } == true
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.35f },
        confirmValueChange = { true }
    )
    val scope = rememberCoroutineScope()

    val rowContent: @Composable () -> Unit = {
        val isTv = LocalIsTv.current
        var isFocused by remember { mutableStateOf(false) }

        ZsOutlinedWrapper(
            shape = RoundedCornerShape(16.dp),
            visible = isFocused && isTv,
            outlineColor = Color.White,
            outlineWidth = 2.dp,
            gap = 4.dp,
            modifier = modifier
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.modal.background)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable {
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                        val encodedPoster = posterPath?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
                        nav.navigate("player/tv/$showId?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=$encodedTitle&year=${ep.airDate?.take(4)?.toIntOrNull() ?: 0}&poster=$encodedPoster")
                    }
            ) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(120.dp)
                            .fillMaxHeight()
                            .animateContentSize(animationSpec = tween(300))
                    ) {
                        AsyncImage(
                            model = ep.stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.colors.mediaCard.badge)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "E${ep.episodeNumber}",
                                color = theme.colors.mediaCard.badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(Modifier.weight(1f).padding(12.dp).animateContentSize()) {
                        Text(
                            ep.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = theme.colors.type.emphasis,
                            fontWeight = FontWeight.Medium
                        )

                        ep.overview?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(Modifier.height(4.dp))
                            var expanded by remember { mutableStateOf(false) }
                            var canExpand by remember(desc) { mutableStateOf(false) }
                            val collapsedLines = 3
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .let { modifier ->
                                        if (canExpand && !isTv) {
                                            modifier.clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { expanded = !expanded }
                                        } else {
                                            modifier
                                        }
                                    }
                            ) {
                                Text(
                                    desc,
                                    maxLines = if (expanded || isTv) Int.MAX_VALUE else collapsedLines,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.colors.type.text,
                                    onTextLayout = { layoutResult ->
                                        if (!expanded) {
                                            canExpand = layoutResult.hasVisualOverflow
                                        }
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                if (pct > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(theme.colors.progress.background.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = pct / 100f)
                                .height(4.dp)
                                .align(Alignment.CenterStart)
                                .background(theme.colors.progress.filled)
                        )
                    }
                }
            }
        }
    }

    if (enableWatchActions) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                EpisodeSwipeBackground(
                    theme = theme,
                    isWatched = isWatched,
                    dismissState = dismissState,
                    onMarkWatched = {
                        onMarkWatched()
                        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                    },
                    onClearHistory = {
                        onClearHistory()
                        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                    },
                    onCancelSwipe = {
                        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                    },
                )
            },
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            content = { rowContent() },
        )
    } else {
        Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)) {
            rowContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSwipeBackground(
    theme: ZStreamTheme,
    isWatched: Boolean,
    dismissState: SwipeToDismissBoxState,
    onMarkWatched: () -> Unit,
    onClearHistory: () -> Unit,
    onCancelSwipe: () -> Unit,
) {
    val activeMark = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd ||
        dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
        dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
    val activeClear = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
        dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.background.secondary.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = theme.colors.background.secondaryHover.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.secondary.copy(alpha = 0.3f)),
                modifier = Modifier.clickable(onClick = onCancelSwipe),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Cancel swipe",
                    tint = theme.colors.type.secondary,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (activeMark) theme.colors.type.success.copy(alpha = 0.22f) else theme.colors.background.secondaryHover.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.success.copy(alpha = 0.28f)),
                modifier = Modifier.clickable(onClick = onMarkWatched).padding(start = 5.dp)
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        tint = if (isWatched) theme.colors.type.secondary else theme.colors.type.success,
                    )
                    Text(
                        text = if (isWatched) "Watched" else "Mark watched",
                        color = if (isWatched) theme.colors.type.secondary else theme.colors.type.success,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (activeClear) theme.colors.buttons.danger.copy(alpha = 0.2f) else theme.colors.background.secondaryHover.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.buttons.danger.copy(alpha = 0.3f)),
                modifier = Modifier.clickable(onClick = onClearHistory).padding(start = 5.dp)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = theme.colors.buttons.danger,
                    )
                    Text(
                        text = "Clear history",
                        color = theme.colors.buttons.danger,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}


