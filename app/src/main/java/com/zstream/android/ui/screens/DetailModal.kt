package com.zstream.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.painterResource
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
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.CastMember
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.Media
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.CollectionSummary
import com.zstream.android.data.remote.CollectionDetails
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

private fun normalizeGroupLabel(group: String): String = group
    .replace(Regex("^\\[[A-Za-z0-9_]+]"), "")
    .trim()

private fun groupIconKey(group: String): String? =
    Regex("^\\[([A-Za-z0-9_]+)]").find(group)?.groupValues?.getOrNull(1)?.uppercase()

enum class WatchBulkTarget { Movie, Season }
enum class WatchBulkAction { MarkWatched, ClearHistory }

@Composable
fun MovieDetailModal(
    detail: MovieDetail,
    certification: String?,
    nav: NavController,
    context: android.content.Context,
    theme: ZStreamTheme,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    hasProgress: Boolean,
    onBack: () -> Unit,
    onMarkMovieWatched: () -> Unit,
    downloadedMovieId: Long? = null,
    isOffline: Boolean = false,
    onDownloadMovie: () -> Unit = {},
    isMovieDownloadPending: Boolean = false,
    onClearMovieWatchHistory: () -> Unit,
    onBookmarkCollection: ((CollectionSummary) -> Unit)? = null,
    onBrowseCollection: ((CollectionSummary) -> Unit)? = null,
    onClearCollection: () -> Unit = {},
    collectionState: CollectionState = CollectionState.Closed,
    showImageLogos: Boolean = true,
    showPlayButton: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    currentGroups: List<String> = emptyList(),
    allGroups: List<String> = emptyList(),
    onUpdateGroups: (List<String>) -> Unit = {},
    trailers: List<com.zstream.android.data.ImdbTrailer> = emptyList(),
    openTrailersInApp: Boolean = true,
    onTrailerWillPlay: () -> Unit = {},
) {
    var pendingBulkTarget by remember { mutableStateOf<WatchBulkTarget?>(null) }
    var pendingBulkAction by remember { mutableStateOf<WatchBulkAction?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
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
            androidx.compose.runtime.withFrameNanos { }
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    SharedDetailSheetScaffold(
        title = detail.title,
        backdropUrl = detail.backdropUrl(),
        logoUrl = detail.logoUrl(),
        showImageLogos = showImageLogos,
        posterUrl = detail.posterUrl(),
        year = detail.releaseDate?.take(4),
        rating = detail.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = onBack,
        modifier = Modifier,
        scrollState = scrollState
    ) {
        SharedMovieDetailContent(
            detail = detail,
            certification = certification,
            context = context,
            nav = nav,
            theme = theme,
            firstItemFocusRequester = firstItemFocusRequester,
            trailers = trailers,
            openTrailersInApp = openTrailersInApp,
            onTrailerWillPlay = onTrailerWillPlay,
            specActions = {
                detail.belongsToCollection?.let { collection ->
                    Surface(
                        onClick = { onBrowseCollection?.invoke(collection) },
                        color = theme.colors.background.secondary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Movie, null, tint = theme.colors.type.secondary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("COLLECTION", color = theme.colors.type.dimmed, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                Text(collection.name, color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
        ) { requester ->
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            onClick = {
                                if (isOffline && downloadedMovieId != null) nav.navigate("localPlayer/$downloadedMovieId")
                                else nav.navigate("player/movie/${detail.id}?title=${detail.title.encode()}&year=${detail.releaseDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
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

                if (isBookmarked) {
                    SharedActionPill(
                        icon = Icons.Filled.Edit,
                        theme = theme,
                        onFocusChanged = { focused -> if (focused && isTv) scrollRequestId++ },
                        onClick = { showGroupEditor = true },
                    )
                }

                SharedActionPill(
                    icon = if (downloadedMovieId != null) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    theme = theme,
                    onFocusChanged = { focused ->
                        if (focused && isTv) scrollRequestId++
                    },
                    loading = isMovieDownloadPending,
                    onClick = { if (downloadedMovieId == null) onDownloadMovie() },
                )

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

    if (showGroupEditor) {
        GroupEditorDialog(
            currentGroups = currentGroups,
            allGroups = allGroups,
            theme = theme,
            onUpdateGroups = onUpdateGroups,
            onDismiss = { showGroupEditor = false },
        )
    }

    when (val state = collectionState) {
        CollectionState.Closed -> Unit
        is CollectionState.Loading -> CollectionStatusDialog(state.collection.name, null, theme, onClearCollection)
        is CollectionState.Error -> CollectionStatusDialog(state.collection.name, state.message, theme, onClearCollection)
        is CollectionState.Loaded -> CollectionOverlayDialog(
            collection = state.collection,
            theme = theme,
            nav = nav,
            onDismiss = onClearCollection,
            onBookmarkCollection = { onBookmarkCollection?.invoke(CollectionSummary(state.collection.id, state.collection.name, state.collection.posterPath, state.collection.backdropPath)) },
        )
    }
}

@Composable
internal fun GroupEditorDialog(
    currentGroups: List<String>,
    allGroups: List<String>,
    theme: ZStreamTheme,
    onUpdateGroups: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    showExistingGroups: Boolean = true,
) {
    var newGroupInput by remember { mutableStateOf("") }
    var selectedGroupIcon by remember { mutableStateOf("BOOKMARK") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 384.dp),
            shape = RoundedCornerShape(20.dp),
            color = theme.colors.modal.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.2f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (showExistingGroups) "Edit groups" else "Create group", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (showExistingGroups) {
                    Text("Tap groups to add or remove them.", color = theme.colors.type.text, fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allGroups.forEach { group ->
                            FilterChip(
                                selected = group in currentGroups,
                                onClick = {
                                    onUpdateGroups(if (group in currentGroups) currentGroups - group else (currentGroups + group).distinct())
                                },
                                label = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        groupIconKey(group)?.let { Icon(groupIconPainter(it), null, modifier = Modifier.size(14.dp)) }
                                        Text(normalizeGroupLabel(group))
                                    }
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newGroupInput,
                    onValueChange = { newGroupInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("New group name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.colors.type.emphasis,
                        unfocusedTextColor = theme.colors.type.text,
                        focusedBorderColor = theme.colors.global.accentA,
                        unfocusedBorderColor = theme.colors.type.divider.copy(alpha = 0.4f),
                        cursorColor = theme.colors.global.accentA,
                    ),
                )
                Text("Icon", color = theme.colors.type.secondary, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupIconOptions.forEach { (key, icon) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedGroupIcon == key) theme.colors.global.accentA.copy(alpha = 0.22f) else theme.colors.background.secondary,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedGroupIcon == key) theme.colors.global.accentA else theme.colors.type.divider.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { selectedGroupIcon = key },
                        ) {
                            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icon), key.lowercase().replace('_', ' '), modifier = Modifier.size(18.dp), tint = theme.colors.type.emphasis)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = theme.colors.type.secondary) }
                    TextButton(onClick = {
                        val created = newGroupInput.trim().takeIf(String::isNotEmpty)?.let { "[${selectedGroupIcon.lowercase()}]$it" }
                        if (created != null) onUpdateGroups((currentGroups + created).distinct())
                        onDismiss()
                    }) { Text("Save", color = theme.colors.global.accentA) }
                }
            }
        }
    }
}

@Composable
private fun CollectionStatusDialog(
    name: String,
    error: String?,
    theme: ZStreamTheme,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = theme.colors.modal.background,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = theme.colors.type.emphasis, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = theme.colors.type.secondary) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (error == null) {
                        CircularProgressIndicator(color = theme.colors.global.accentA)
                        Text("Loading collection...", color = theme.colors.type.secondary)
                    } else {
                        Icon(Icons.Filled.Error, null, tint = theme.colors.type.danger, modifier = Modifier.size(42.dp))
                        Text("Error loading collection", color = theme.colors.type.danger, fontWeight = FontWeight.SemiBold)
                        Text(error, color = theme.colors.type.secondary, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CollectionOverlayDialog(
    collection: CollectionDetails,
    theme: ZStreamTheme,
    nav: NavController,
    onDismiss: () -> Unit,
    onBookmarkCollection: () -> Unit,
) {
    var sortOrder by remember { mutableStateOf("release") }
    val parts = remember(collection.parts, sortOrder) {
        when (sortOrder) {
            "rating" -> collection.parts.sortedByDescending { it.voteAverage ?: 0.0 }
            else -> collection.parts.sortedBy { it.releaseDate ?: "" }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(vertical = 18.dp),
        ) {
            Surface(
                color = theme.colors.modal.background,
                tonalElevation = 6.dp,
                shadowElevation = 14.dp,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .heightIn(min = 420.dp, max = 720.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(collection.name, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = theme.colors.type.secondary) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${parts.size} ${if (parts.size == 1) "movie" else "movies"}",
                            color = theme.colors.type.secondary,
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = onBookmarkCollection,
                            colors = ButtonDefaults.textButtonColors(contentColor = theme.colors.type.secondary),
                        ) {
                            Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bookmark All")
                        }
                    }
                    if (parts.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sort by:", color = theme.colors.type.secondary, fontSize = 12.sp)
                            FilterChip(
                                selected = sortOrder == "release",
                                onClick = { sortOrder = "release" },
                                label = { Text("Release") },
                            )
                            FilterChip(
                                selected = sortOrder == "rating",
                                onClick = { sortOrder = "rating" },
                                label = { Text("Rating") },
                            )
                        }
                    }

                    collection.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }

                    if (parts.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.Movie, null, tint = theme.colors.type.secondary, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No movies in this collection", color = theme.colors.type.secondary)
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(parts, key = { it.id }) { part ->
                                CollectionPartCard(
                                    part = part,
                                    theme = theme,
                                    onClick = {
                                        nav.navigate("detail/movie/${part.id}")
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionPartCard(
    part: com.zstream.android.data.remote.CollectionPart,
    theme: ZStreamTheme,
    onClick: () -> Unit,
) {
    val poster = part.posterPath?.let { path ->
        if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "w342" + if (path.startsWith("/")) path else "/$path"
    }
    Column(
        modifier = Modifier.width(128.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .height(188.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.background.secondary),
            contentAlignment = Alignment.Center,
        ) {
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = part.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(part.title, color = theme.colors.type.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        Text(part.releaseDate.orEmpty().take(4), color = theme.colors.type.dimmed, fontSize = 11.sp)
    }
}

@Composable
fun TvDetailModal(
    detail: TvDetail,
    certification: String?,
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
    downloadedEpisodes: Map<String, DownloadEntity> = emptyMap(),
    onDownloadEpisode: (com.zstream.android.data.model.Episode) -> Unit = {},
    onDownloadSeason: () -> Unit = {},
    pendingDownloads: Set<String> = emptySet(),
    isOffline: Boolean = false,
    onBookmarkCollection: ((CollectionSummary) -> Unit)? = null,
    onBrowseCollection: ((CollectionSummary) -> Unit)? = null,
    onClearCollection: () -> Unit = {},
    collectionState: CollectionState = CollectionState.Closed,
    showImageLogos: Boolean = true,
    showPlayButton: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    currentGroups: List<String> = emptyList(),
    allGroups: List<String> = emptyList(),
    onUpdateGroups: (List<String>) -> Unit = {},
    trailers: List<com.zstream.android.data.ImdbTrailer> = emptyList(),
    openTrailersInApp: Boolean = true,
    onTrailerWillPlay: () -> Unit = {},
) {
    var pendingBulkTarget by remember { mutableStateOf<WatchBulkTarget?>(null) }
    var pendingBulkAction by remember { mutableStateOf<WatchBulkAction?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
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
            androidx.compose.runtime.withFrameNanos { }
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    SharedDetailSheetScaffold(
        title = detail.name,
        backdropUrl = detail.backdropUrl(),
        logoUrl = detail.logoUrl(),
        showImageLogos = showImageLogos,
        posterUrl = detail.posterUrl(),
        year = detail.firstAirDate?.take(4),
        rating = detail.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = onBack,
        modifier = Modifier,
        scrollState = scrollState
    ) {
        SharedTvDetailContent(
            detail = detail,
            certification = certification,
            selectedSeason = selectedSeason,
            allProgress = allProgress,
            context = context,
            nav = nav,
            theme = theme,
            trailers = trailers,
            openTrailersInApp = openTrailersInApp,
            onTrailerWillPlay = onTrailerWillPlay,
            onSelectSeason = onSelectSeason,
            onMarkEpisodeWatched = onMarkEpisodeWatched,
            onClearEpisodeWatchHistory = onClearEpisodeWatchHistory,
            downloadedEpisodes = downloadedEpisodes,
            onDownloadEpisode = onDownloadEpisode,
            onDownloadSeason = onDownloadSeason,
            pendingDownloads = pendingDownloads,
            isOffline = isOffline,
            firstItemFocusRequester = firstItemFocusRequester,
            specActions = { },
        ) { requester ->
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            val progressDownload = if (hasProgress && resumeProgress != null) {
                                val sNum = resumeProgress.seasonNumber
                                val eNum = resumeProgress.episodeNumber
                                if (sNum != null && eNum != null) downloadedEpisodes["$sNum|$eNum"] else null
                            } else null
                            val firstEp = selectedSeason?.episodes?.airedEpisodes()?.firstOrNull()
                            val firstEpDownload = firstEp?.let { downloadedEpisodes["${it.seasonNumber}|${it.episodeNumber}"] }
                            val anyDownload = downloadedEpisodes.values
                                .filter { it.season != null && it.episode != null }
                                .minWithOrNull(compareBy({ it.season }, { it.episode }))

                            when {
                                progressDownload != null && isOffline -> nav.navigate("localPlayer/${progressDownload.id}")
                                hasProgress && resumeProgress != null && !isOffline -> {
                                    val sNum = resumeProgress.seasonNumber ?: selectedSeason?.seasonNumber ?: 1
                                    val eNum = resumeProgress.episodeNumber ?: 1
                                    nav.navigate("player/tv/${detail.id}?season=$sNum&episode=$eNum&title=${detail.name.encode()}&year=${detail.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
                                }
                                firstEpDownload != null && isOffline -> nav.navigate("localPlayer/${firstEpDownload.id}")
                                !isOffline && firstEp != null -> nav.navigate("player/tv/${detail.id}?season=${firstEp.seasonNumber}&episode=${firstEp.episodeNumber}&title=${detail.name.encode()}&year=${detail.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
                                anyDownload != null && isOffline -> nav.navigate("localPlayer/${anyDownload.id}")
                                anyDownload != null -> nav.navigate("player/tv/${detail.id}?season=${anyDownload.season}&episode=${anyDownload.episode}&title=${detail.name.encode()}&year=${detail.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${detail.posterPath?.encode() ?: ""}")
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

            if (isBookmarked) {
                SharedActionPill(
                    icon = Icons.Filled.Edit,
                    theme = theme,
                    onFocusChanged = { focused -> if (focused && isTv) scrollRequestId++ },
                    onClick = { showGroupEditor = true },
                )
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

    if (showGroupEditor) {
        GroupEditorDialog(
            currentGroups = currentGroups,
            allGroups = allGroups,
            theme = theme,
            onUpdateGroups = onUpdateGroups,
            onDismiss = { showGroupEditor = false },
        )
    }
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

internal fun openExternalVideo(context: android.content.Context, url: String, mimeType: String?) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), mimeType?.takeIf { it.isNotBlank() } ?: "video/*")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        }
}

internal fun String.encodeRouteParam(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

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
    onEpisodeClick: (() -> Unit)? = null,
    compact: Boolean = false,
    horizontalPadding: Dp = 16.dp,
    downloadEntry: DownloadEntity? = null,
    onDownloadEpisode: () -> Unit = {},
    isDownloadPending: Boolean = false,
    isOffline: Boolean = false,
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
    val isDownloaded = downloadEntry != null
    val rowDisabled = !isDownloaded && isOffline

    val rowContent: @Composable () -> Unit = {
        val isTv = LocalIsTv.current
        var isFocused by remember { mutableStateOf(false) }

        ZsOutlinedWrapper(
            shape = RoundedCornerShape(16.dp),
            visible = isFocused && isTv,
            outlineColor = Color.White,
            outlineWidth = 2.dp,
            gap = 4.dp,
        ) {
            Box(
                modifier
                    .fillMaxWidth()
                    .alpha(if (rowDisabled) 0.4f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.modal.background)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable(enabled = !rowDisabled) {
                        if (isDownloaded) {
                            if (isOffline) nav.navigate("localPlayer/${downloadEntry?.id}")
                            else if (onEpisodeClick != null) onEpisodeClick()
                            else {
                                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                                val encodedPoster = posterPath?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
                                nav.navigate("player/tv/$showId?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=$encodedTitle&year=${ep.airDate?.take(4)?.toIntOrNull() ?: 0}&poster=$encodedPoster")
                            }
                        } else if (onEpisodeClick != null) {
                            onEpisodeClick()
                        } else {
                            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                            val encodedPoster = posterPath?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
                            nav.navigate("player/tv/$showId?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=$encodedTitle&year=${ep.airDate?.take(4)?.toIntOrNull() ?: 0}&poster=$encodedPoster")
                        }
                    }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (compact) Modifier.height(44.dp) else Modifier.height(IntrinsicSize.Min))
                ) {
                    Box(
                        Modifier
                            .width(if (compact) 64.dp else 90.dp)
                            .fillMaxHeight()
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

                    Column(Modifier.weight(1f).padding(8.dp)) {
                        Text(
                            ep.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = theme.colors.type.emphasis,
                            fontWeight = FontWeight.Medium
                        )

                        ep.overview?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                desc,
                                maxLines = if (compact) 2 else 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.colors.type.text,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = theme.colors.type.success,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    } else if (isDownloadPending) {
                        CircularProgressIndicator(
                            color = theme.colors.type.secondary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    } else if (!isOffline) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download episode",
                            tint = theme.colors.type.secondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                                .clickable { onDownloadEpisode() },
                        )
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
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
            content = { rowContent() },
        )
    } else {
        Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp)) {
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
    // Guards against the background peeking through the (nominally opaque) foreground row when
    // settled at/near zero offset -- only fade the swipe actions in once the user has genuinely
    // dragged past a few pixels, rather than relying solely on z-order to hide them at rest.
    val dragOffsetPx = runCatching { kotlin.math.abs(dismissState.requireOffset()) }.getOrDefault(0f)
    val revealAlpha = (dragOffsetPx / 24f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(revealAlpha)
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
