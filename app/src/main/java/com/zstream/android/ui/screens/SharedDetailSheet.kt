package com.zstream.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.zstream.android.theme.ZStreamTheme

internal val DETAIL_SHEET_CORNER_RADIUS = 28.dp
internal val DETAIL_SHEET_BACKDROP_HEIGHT = 360.dp
internal val DETAIL_SHEET_CONTENT_PADDING = 32.dp
internal val DETAIL_SHEET_BOTTOM_SPACER = 36.dp
internal val DETAIL_SHEET_MIN_SCROLL_EXTRA = 1.dp

@Composable
internal fun SharedDetailSheetScaffold(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    posterUrl: String?,
    year: String?,
    rating: String?,
    theme: ZStreamTheme,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                color = theme.colors.background.main,
                shape = RoundedCornerShape(DETAIL_SHEET_CORNER_RADIUS),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = this@BoxWithConstraints.maxHeight + DETAIL_SHEET_MIN_SCROLL_EXTRA)
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.2f), RoundedCornerShape(DETAIL_SHEET_CORNER_RADIUS))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SharedDetailSheetHero(
                        title = title,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        posterUrl = posterUrl,
                        year = year,
                        rating = rating,
                        theme = theme,
                        onClose = onClose
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    ) {
                        content()
                        Spacer(Modifier.height(DETAIL_SHEET_BOTTOM_SPACER))
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
    posterUrl: String?,
    year: String?,
    rating: String?,
    theme: ZStreamTheme,
    onClose: (() -> Unit)?,
) {
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
        if (onClose != null) {
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
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(0.6f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    title.uppercase(),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SharedMovieDetailContent(
    detail: MovieDetail,
    context: android.content.Context,
    nav: NavController,
    theme: ZStreamTheme,
    topActions: @Composable RowScope.() -> Unit,
) {
    val genres = detail.genres.orEmpty()
    val cast = detail.credits?.cast.orEmpty().take(8)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = topActions
    )

    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
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
        }
    }

    SharedSectionHeader("Cast", theme)
    SharedCastRow(cast, theme, context)
    SharedSectionHeader("Trailers", theme)
    SharedTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    SharedSectionHeader("Similar", theme)
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
    topActions: @Composable RowScope.() -> Unit,
) {
    val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = topActions
    )

    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f)) {
            detail.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
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
        }
    }

    SharedSectionHeader("Seasons", theme)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 32.dp)) {
        items(seasons) { season ->
            FilterChip(
                selected = season.seasonNumber == selectedSeason?.seasonNumber,
                onClick = { onSelectSeason(season.seasonNumber) },
                label = { Text("S${season.seasonNumber}") }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    selectedSeason?.episodes?.let { episodes ->
        val progressMap = allProgress
            .filter { it.seasonNumber == selectedSeason.seasonNumber }
            .associateBy { it.episodeNumber }
        SharedSectionHeader("Episodes", theme)
        episodes.forEach { episode ->
            SharedEpisodeRow(
                ep = episode,
                showId = detail.id,
                title = detail.name,
                posterPath = detail.posterPath,
                nav = nav,
                theme = theme,
                episodeProgress = progressMap[episode.episodeNumber]
            )
        }
    }

    SharedSectionHeader("Cast", theme)
    SharedCastRow(detail.credits?.cast.orEmpty().take(8), theme, context)
    SharedSectionHeader("Trailers", theme)
    SharedTrailerGrid(detail.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
    SharedSectionHeader("Similar", theme)
    SharedSimilarGrid(detail.similar?.results.orEmpty(), theme, nav)
}

@Composable
internal fun SharedActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: ZStreamTheme,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = theme.colors.type.text.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = theme.colors.type.text)
        }
    }
}

@Composable
internal fun SharedSectionHeader(title: String, theme: ZStreamTheme) {
    Text(title, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = theme.colors.type.emphasis)
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
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = theme.colors.type.text)
    }
}

@Composable
internal fun SharedCastRow(cast: List<CastMember>, theme: ZStreamTheme, context: android.content.Context) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
        items(cast) { member ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(105.dp).clickable {
                openCastProfile(context, member.id, member.externalIds?.imdbId)
            }) {
                AsyncImage(
                    model = member.profilePath?.let { "${Urls.TMDB_IMAGE}w185$it" },
                    contentDescription = member.name,
                    modifier = Modifier.size(96.dp).clip(CircleShape).border(1.dp, theme.colors.type.text.copy(alpha = 0.08f), CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(10.dp))
                Text(member.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                Text(member.character.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
            }
        }
    }
}

@Composable
internal fun SharedTrailerGrid(trailers: List<TrailerData>, theme: ZStreamTheme, context: android.content.Context) {
    if (trailers.isEmpty()) {
        Text("No trailers available", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), color = theme.colors.type.secondary)
        return
    }

    Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trailers.take(3)) { trailer ->
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.modal.background)
                        .clickable { openYoutubeTrailer(context, trailer.key) }
                ) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${trailer.key}/0.jpg",
                        contentDescription = trailer.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(trailer.name, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
internal fun SharedSimilarGrid(similar: List<Media>, theme: ZStreamTheme, nav: NavController) {
    if (similar.isEmpty()) {
        Text("No similar movies available", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), color = theme.colors.type.secondary)
        return
    }

    Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(similar.take(10)) { movie ->
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { nav.navigate("detail/${movie.type}/${movie.id}") }
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(8.dp)).background(theme.colors.modal.background)) {
                        movie.posterUrl()?.let {
                            AsyncImage(model = it, contentDescription = movie.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(movie.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                    val year = (movie.releaseDate ?: movie.firstAirDate)?.take(4) ?: "—"
                    val capitalizedMovie = movie.type.replaceFirstChar { it.uppercase() }
                    Text("$capitalizedMovie • $year", style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
                    Spacer(Modifier.height(16.dp))
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
internal fun SharedTmdbRating(rating: Double, theme: ZStreamTheme) {
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
