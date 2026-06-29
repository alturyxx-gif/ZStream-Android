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

// Shared components from SharedDetailSheet

internal val DETAIL_SHEET_CORNER_RADIUS = 28.dp
internal val DETAIL_SHEET_BACKDROP_HEIGHT = 360.dp
internal val DETAIL_SHEET_CONTENT_PADDING = 20.dp
internal val DETAIL_SHEET_BOTTOM_SPACER = 36.dp
internal val DETAIL_SHEET_MIN_SCROLL_EXTRA = 1.dp

private fun calculateDefaultBringIntoViewScrollDistance(
    offset: Float,
    size: Float,
    containerSize: Float,
): Float {
    val trailingEdge = offset + size
    return when {
        offset < 0f -> offset
        trailingEdge > containerSize -> trailingEdge - containerSize
        else -> 0f
    }
}

internal val LocalScrollState = compositionLocalOf<ScrollState?> { null }
internal val LocalScrollableContainerCoordinates = compositionLocalOf<LayoutCoordinates?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedDetailSheetScaffold(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    showImageLogos: Boolean,
    posterUrl: String?,
    year: String?,
    rating: String?,
    theme: ZStreamTheme,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isTv = LocalIsTv.current
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val bringIntoViewSpec = remember(isTv) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                // On TV, we handle scrolling such that the item is positioned near the top
                if (isTv) {
                    val targetPadding = 200f // Scroll such that item is 200px from the top
                    return offset - targetPadding
                }
                
                return calculateDefaultBringIntoViewScrollDistance(
                    offset = offset,
                    size = size,
                    containerSize = containerSize,
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides bringIntoViewSpec,
            LocalScrollState provides scrollState,
            LocalScrollableContainerCoordinates provides containerCoords
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { containerCoords = it }
                    .padding(top = if (isTv) 32.dp else 48.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = theme.colors.background.main,
                    shape = RoundedCornerShape(DETAIL_SHEET_CORNER_RADIUS),
                    modifier = Modifier
                        .fillMaxWidth(if (isTv) 0.85f else 1f)
                        .heightIn(min = this@BoxWithConstraints.maxHeight + DETAIL_SHEET_MIN_SCROLL_EXTRA)
                        .border(
                            1.dp,
                            theme.colors.type.divider.copy(alpha = 0.2f),
                            RoundedCornerShape(DETAIL_SHEET_CORNER_RADIUS)
                        )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        SharedDetailSheetHero(
                            title = title,
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl,
                            showImageLogos = showImageLogos,
                            posterUrl = posterUrl,
                            year = year,
                            rating = rating,
                            theme = theme,
                            onClose = onClose
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-20).dp)
                        ) {
                            content()
                            Spacer(Modifier.height(DETAIL_SHEET_BOTTOM_SPACER))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedDetailSheetHero(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    showImageLogos: Boolean,
    posterUrl: String?,
    year: String?,
    rating: String?,
    theme: ZStreamTheme,
    onClose: (() -> Unit)?,
) {
    val isTv = LocalIsTv.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DETAIL_SHEET_BACKDROP_HEIGHT)
    ) {
        AsyncImage(
            model = backdropUrl ?: posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
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
        if (onClose != null && !isTv) {
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
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = DETAIL_SHEET_CONTENT_PADDING)
        ) {
            if (showImageLogos && logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(if (isTv) 0.4f else 0.6f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    title,
                    color = theme.colors.type.emphasis,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )
            }
            SharedMetadataRow(
                content = listOfNotNull(
                    rating?.toDoubleOrNull()?.let { numericRating -> { SharedTmdbRating(numericRating, theme) } },
                    year?.let { { Text(it, fontSize = 12.sp, color = Color.White) } }
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Alignment.CenterVertically
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
internal fun ColumnScope.SharedMovieDetailContent(
    detail: MovieDetail,
    context: android.content.Context,
    nav: NavController,
    theme: ZStreamTheme,
    firstItemFocusRequester: FocusRequester? = null,
    specActions: @Composable ColumnScope.() -> Unit = {},
    topActions: @Composable RowScope.(firstItemFocusRequester: FocusRequester?) -> Unit,
) {
    val genres = detail.genres.orEmpty()
    val cast = detail.credits?.cast.orEmpty().take(8)
    val scrollState = LocalScrollState.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        topActions(firstItemFocusRequester)
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { 
                Text(
                    it, 
                    color = theme.colors.type.text, 
                    fontSize = 14.sp,
                    modifier = Modifier.focusable()
                ) 
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { SharedGenreChip(it.name, theme) }
            }
        }
        Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SharedDetailSpec("Runtime", detail.runtime?.let { "$it min" } ?: "-", theme)
            SharedDetailSpec("Language", "EN", theme)
            SharedDetailSpec("Release Date", detail.releaseDate ?: "-", theme)
            SharedDetailSpec("Rating", "PG-13", theme)
            specActions()
        }
    }

    ZsBottomSheetSectionHeader("Cast")
    SharedCastRow(cast, theme, context)
    ZsBottomSheetSectionHeader("Trailers")
    SharedTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    ZsBottomSheetSectionHeader("Similar")
    SharedSimilarGrid(detail.similar?.results.orEmpty(), theme, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SharedTvDetailContent(
    detail: TvDetail,
    selectedSeason: Season?,
    allProgress: List<ProgressEntity>,
    context: android.content.Context,
    nav: NavController,
    theme: ZStreamTheme,
    onSelectSeason: (Int) -> Unit,
    onMarkEpisodeWatched: (Episode) -> Unit = {},
    onClearEpisodeWatchHistory: (Episode) -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    specActions: @Composable ColumnScope.() -> Unit = {},
    topActions: @Composable RowScope.(firstItemFocusRequester: FocusRequester?) -> Unit,
) {
    val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
    val scrollState = LocalScrollState.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        topActions(firstItemFocusRequester)
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { 
                Text(
                    it, 
                    color = theme.colors.type.text, 
                    fontSize = 14.sp,
                    modifier = Modifier.focusable()
                ) 
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.genres.orEmpty().forEach { SharedGenreChip(it.name, theme) }
            }
        }
        Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SharedDetailSpec("Seasons", detail.numberOfSeasons?.toString() ?: "—", theme)
            SharedDetailSpec("Language", "EN", theme)
            SharedDetailSpec("Release Date", detail.firstAirDate ?: "—", theme)
            SharedDetailSpec("Rating", "TV-14", theme)
            specActions()
        }
    }

    ZsBottomSheetSectionHeader("Seasons")
    val isTv = LocalIsTv.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
        modifier = Modifier.padding(horizontal = 20.dp),
        contentPadding = if (isTv) PaddingValues(4.dp) else PaddingValues(0.dp)
    ) {
        items(seasons) { season ->
            var isFocused by remember { mutableStateOf(false) }
            val isSelected = season.seasonNumber == selectedSeason?.seasonNumber

            ZsOutlinedWrapper(
                shape = RoundedCornerShape(12.dp),
                visible = isFocused && isTv,
                outlineColor = Color.White,
                outlineWidth = 2.dp,
                horizontal = 3.dp,
                vertical = (-5).dp,
            ) {
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectSeason(season.seasonNumber) },
                    label = { Text("S${season.seasonNumber}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.colors.background.secondaryHover.copy(alpha = 0.8f),
                        selectedLabelColor = theme.colors.type.emphasis,
                        containerColor = theme.colors.background.secondary.copy(alpha = 0.5f),
                        labelColor = theme.colors.type.secondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = theme.colors.type.divider.copy(alpha = 0.22f),
                        selectedBorderColor = theme.colors.global.accentA.copy(alpha = 0.45f),
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                    ),
                    modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
                    leadingIcon = null,
                    trailingIcon = null,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    selectedSeason?.episodes?.airedEpisodes()?.takeIf { it.isNotEmpty() }?.let { episodes ->
        val progressMap = allProgress
            .filter { it.seasonNumber == selectedSeason.seasonNumber }
            .associateBy { it.episodeNumber }
        ZsBottomSheetSectionHeader("Episodes")
        episodes.forEach { episode ->
            com.zstream.android.ui.screens.SharedEpisodeRow(
                ep = episode,
                showId = detail.id,
                title = detail.name,
                posterPath = detail.posterPath,
                nav = nav,
                theme = theme,
                episodeProgress = progressMap[episode.episodeNumber],
                enableWatchActions = true,
                onMarkWatched = { onMarkEpisodeWatched(episode) },
                onClearHistory = { onClearEpisodeWatchHistory(episode) },
            )
        }
    }

    ZsBottomSheetSectionHeader("Cast")
    SharedCastRow(detail.credits?.cast.orEmpty().take(8), theme, context)
    ZsBottomSheetSectionHeader("Trailers")
    SharedTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    ZsBottomSheetSectionHeader("Similar")
    SharedSimilarGrid(detail.similar?.results.orEmpty(), theme, nav)
}

@Composable
internal fun Modifier.onTvFocusScroll(scrollState: ScrollState): Modifier {
    // BringIntoViewSpec handles all focus scrolling now
    return this
}

@Composable
internal fun SharedActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: ZStreamTheme,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val isTv = LocalIsTv.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { 
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
    ) {
        ZsOutlinedWrapper(
            shape = RoundedCornerShape(50.dp),
            visible = isFocused && isTv,
            outlineColor = Color.White,
            outlineWidth = 2.dp,
            gap = 4.dp,
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = if (isFocused && isTv) theme.colors.global.accentA.copy(alpha = 0.3f) else theme.colors.type.text.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                modifier = Modifier
                    .clickable(onClick = onClick)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = theme.colors.type.text)
                }
            }
        }
    }
}

@Composable
internal fun SharedDetailSpec(label: String, value: String, theme: ZStreamTheme) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = theme.colors.type.emphasis)
    }
}

@Composable
internal fun SharedGenreChip(label: String, theme: ZStreamTheme) {
    Surface(
        color = theme.colors.type.text.copy(alpha = 0.08f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.15f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = theme.colors.type.text
        )
    }
}

@Composable
internal fun SharedCastRow(
    cast: List<CastMember>,
    theme: ZStreamTheme,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val isTv = LocalIsTv.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        contentPadding = if (isTv) PaddingValues(4.dp) else PaddingValues(0.dp)
    ) {
        items(cast) { member ->
            var isFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(
                shape = RoundedCornerShape(12.dp),
                visible = isFocused && isTv,
                outlineColor = Color.White,
                outlineWidth = 2.dp,
                gap = 4.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable {
                            com.zstream.android.ui.screens.openCastProfile(context, member.id, member.externalIds?.imdbId)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    AsyncImage(
                        model = member.profilePath?.let { "${Urls.TMDB_IMAGE}w185$it" },
                        contentDescription = member.name,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(1.dp, theme.colors.type.text.copy(alpha = 0.08f), CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        member.name.orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.colors.type.emphasis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        member.character.orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.colors.type.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedTrailerGrid(
    trailers: List<TrailerData>,
    theme: ZStreamTheme,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    if (trailers.isEmpty()) {
        ZsStatusBanner(
            message = "No trailers available",
            variant = ZsStatusBannerVariant.Info,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        return
    }

    val isTv = LocalIsTv.current
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = if (isTv) PaddingValues(4.dp) else PaddingValues(0.dp)
        ) {
            items(trailers.take(3)) { trailer ->
                var isFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    visible = isFocused && isTv,
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    gap = 4.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.modal.background)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { com.zstream.android.ui.screens.openYoutubeTrailer(context, trailer.key) }
                    ) {
                        AsyncImage(
                            model = "https://img.youtube.com/vi/${trailer.key}/0.jpg",
                            contentDescription = trailer.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            trailer.name,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedSimilarGrid(
    similar: List<Media>,
    theme: ZStreamTheme,
    nav: NavController,
    modifier: Modifier = Modifier
) {
    if (similar.isEmpty()) {
        ZsStatusBanner(
            message = "No similar movies available",
            variant = ZsStatusBannerVariant.Info,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        return
    }

    val isTv = LocalIsTv.current
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = if (isTv) PaddingValues(4.dp) else PaddingValues(0.dp)
        ) {
            items(similar.take(10)) { movie ->
                var isFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    visible = isFocused && isTv,
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    gap = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { nav.navigate("detail/${movie.type}/${movie.id}") }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(205.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.modal.background)
                        ) {
                            movie.posterUrl()?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = movie.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            movie.displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.colors.type.emphasis
                        )
                        val year = (movie.releaseDate ?: movie.firstAirDate)?.take(4) ?: "—"
                        val capitalizedMovie = movie.type.replaceFirstChar { it.uppercase() }
                        Text(
                            "$capitalizedMovie • $year",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.colors.type.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedMetadataRow(
    content: List<@Composable () -> Unit>,
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Alignment.Vertical
) {
    Row(horizontalArrangement = horizontalArrangement, verticalAlignment = verticalArrangement) {
        content.forEachIndexed { index, contentItem ->
            if (index > 0) {
                Text("•", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
            }
            contentItem()
        }
    }
}

@Composable
fun SharedTmdbRating(rating: Double, theme: ZStreamTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.tmdb_logo),
            contentDescription = "TMDB Logo",
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "%.1f".format(rating), color = theme.colors.type.emphasis, fontSize = 12.sp)
    }
}
