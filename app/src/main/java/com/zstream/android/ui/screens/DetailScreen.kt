package com.zstream.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.Urls
import dagger.hilt.android.EntryPointAccessors
import com.zstream.android.data.model.CastMember
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.TvDetail
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val theme = LocalZStreamTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val bookmarkRepo = EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.zstream.android.di.RepositoryEntryPoint::class.java
    ).bookmarkRepository()

    val progress by vm.progress.collectAsState()
    val allProgress by vm.allProgress.collectAsState()
    val hasProgress = progress?.let { it.watched >= 20 } ?: false

    when (val s = state) {
        is DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DetailState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::load) { Text("Retry") }
            }
        }
        is DetailState.Movie -> {
            MovieDetailModal(s, nav, context, theme, bookmarkRepo, hasProgress)
        }
        is DetailState.Tv -> {
            TvDetailModal(s, vm, nav, context, theme, bookmarkRepo, hasProgress, progress, allProgress)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailModal(
    state: DetailState.Movie, 
    nav: NavController, 
    context: android.content.Context, 
    theme: com.zstream.android.theme.ZStreamTheme,
    bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    hasProgress: Boolean
) {
    val d = state.detail
    SharedDetailSheetScaffold(
        title = d.title,
        backdropUrl = d.backdropUrl(),
        logoUrl = d.logoUrl(),
        posterUrl = d.posterUrl(),
        year = d.releaseDate?.take(4),
        rating = d.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = { nav.popBackStack() },
        modifier = Modifier.background(theme.colors.background.main)
    ) {
        SharedMovieDetailContent(
            detail = d,
            context = context,
            nav = nav,
            theme = theme,
        ) {
            Button(
                onClick = { nav.navigate("player/movie/${d.id}?title=${d.title.encode()}&year=${d.releaseDate?.take(4)?.toIntOrNull() ?: 0}&poster=${d.posterPath?.encode() ?: ""}") },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.primary),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.buttons.primaryText)
                Spacer(Modifier.width(6.dp))
                Text(if (hasProgress) "Resume" else "Play", color = theme.colors.buttons.primaryText)
            }
            SharedActionPill(Icons.Filled.Share, theme) {
                openShareSheet(context, d.title, d.id, "movie")
            }
            BookmarkButton(
                d.id.toString(), d.title, "movie",
                d.releaseDate?.take(4)?.toIntOrNull(), d.posterPath,
                bookmarkRepo, theme
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvDetailModal(
    state: DetailState.Tv,
    vm: DetailViewModel,
    nav: NavController,
    context: android.content.Context,
    theme: com.zstream.android.theme.ZStreamTheme,
    bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    hasProgress: Boolean,
    progress: com.zstream.android.data.local.entity.ProgressEntity?,
    allProgress: List<com.zstream.android.data.local.entity.ProgressEntity>,
) {
    val d = state.detail
    val season = state.selectedSeason
    var selectedSeasonNum by remember { mutableIntStateOf(season?.seasonNumber ?: 1) }
    SharedDetailSheetScaffold(
        title = d.name,
        backdropUrl = d.backdropUrl(),
        logoUrl = d.logoUrl(),
        posterUrl = d.posterUrl(),
        year = d.firstAirDate?.take(4),
        rating = d.voteAverage?.let { String.format("%.1f", it) },
        theme = theme,
        onClose = { nav.popBackStack() },
        modifier = Modifier.background(theme.colors.background.main)
    ) {
        SharedTvDetailContent(
            detail = d,
            selectedSeason = season,
            allProgress = allProgress,
            context = context,
            nav = nav,
            theme = theme,
            onSelectSeason = { seasonNumber ->
                selectedSeasonNum = seasonNumber
                vm.selectSeason(seasonNumber)
            },
        ) {
            Button(
                onClick = {
                    if (hasProgress && progress != null) {
                        val sNum = progress.seasonNumber ?: season?.seasonNumber ?: 1
                        val eNum = progress.episodeNumber ?: 1
                        nav.navigate("player/tv/${d.id}?season=$sNum&episode=$eNum&title=${d.name.encode()}&year=${d.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${d.posterPath?.encode() ?: ""}")
                    } else {
                        val firstEp = season?.episodes?.firstOrNull()
                        if (firstEp != null) {
                            nav.navigate("player/tv/${d.id}?season=${firstEp.seasonNumber}&episode=${firstEp.episodeNumber}&title=${d.name.encode()}&year=${d.firstAirDate?.take(4)?.toIntOrNull() ?: 0}&poster=${d.posterPath?.encode() ?: ""}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.primary),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.buttons.primaryText)
                Spacer(Modifier.width(6.dp))
                val label = if (hasProgress && progress != null) {
                    val sNum = progress.seasonNumber
                    val eNum = progress.episodeNumber
                    if (sNum != null && eNum != null) "Resume S${sNum}:E${eNum}" else "Resume"
                } else "Play"
                Text(label, color = theme.colors.buttons.primaryText)
            }
            SharedActionPill(Icons.Filled.Share, theme) {
                openShareSheet(context, d.name, d.id, "tv")
            }
            BookmarkButton(
                d.id.toString(), d.name, "tv",
                d.firstAirDate?.take(4)?.toIntOrNull(), d.posterPath,
                bookmarkRepo, theme
            )
        }
    }
}

@Composable
internal fun SharedEpisodeRow(
    ep: Episode,
    showId: Int,
    title: String,
    posterPath: String?,
    nav: NavController,
    theme: com.zstream.android.theme.ZStreamTheme,
    episodeProgress: com.zstream.android.data.local.entity.ProgressEntity?,
) {
    val pct = if (episodeProgress != null && episodeProgress.duration > 0) {
        ((episodeProgress.watched.toFloat() / episodeProgress.duration) * 100f).coerceIn(0f, 100f)
    } else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.modal.background)
            .clickable {
                nav.navigate("player/tv/$showId?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=${title.encode()}&year=${ep.airDate?.take(4)?.toIntOrNull() ?: 0}&poster=${posterPath?.encode() ?: ""}")
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
                    model = ep.stillPath?.let { "${Urls.TMDB_IMAGE}w780$it" },
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Episode number badge
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2D2D3D))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "E${ep.episodeNumber}",
                        color = Color(0xFFC4C4D4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(Modifier.weight(1f).padding(12.dp).animateContentSize()) {
                // Episode title
                Text(
                    ep.name.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = theme.colors.type.emphasis,
                    fontWeight = FontWeight.Medium
                )

                // Episode description — tap to expand/collapse
                ep.overview?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(Modifier.height(4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { expanded = !expanded }
                    ) {
                        Text(
                            desc,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.colors.type.text
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
                    .background(Color(0xFF8D8D8D).copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = pct / 100f)
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .background(Color(0xFF5A62EB))
                )
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>, theme: com.zstream.android.theme.ZStreamTheme, context: android.content.Context) {
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
private fun SectionHeader(title: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Text(title, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 0.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = theme.colors.type.emphasis)
}

@Composable
private fun DetailSpec(label: String, value: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = theme.colors.type.emphasis)
    }
}

@Composable
private fun GenreChip(label: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Surface(
        color = theme.colors.type.text.copy(alpha = 0.08f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.15f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = theme.colors.type.text)
    }
}

@Composable
private fun TmdbRating(rating: Double, theme: com.zstream.android.theme.ZStreamTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.zstream.android.R.drawable.tmdb_logo),
            contentDescription = "TMDB Logo",
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "%.1f".format(rating),
            color = theme.colors.type.emphasis,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MetadataRow(
    content: List<@Composable () -> Unit>, 
    theme: com.zstream.android.theme.ZStreamTheme, 
    horizontalArrangement: Arrangement.Horizontal, 
    verticalArrangement: Alignment.Vertical
) {
    Row(horizontalArrangement = horizontalArrangement, verticalAlignment = verticalArrangement) {
        content.forEachIndexed { index, contentItem ->
            if (index > 0) Text("•", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 4.dp))
            contentItem()
        }
    }
}

@Composable
private fun BookmarkButton(
    tmdbId: String,
    title: String,
    type: String,
    year: Int? = null,
    posterPath: String? = null,
    bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    theme: com.zstream.android.theme.ZStreamTheme
) {
    val isBookmarked by bookmarkRepo.observeBookmark(tmdbId).collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    
    ActionPill(
        icon = if (isBookmarked != null) androidx.compose.material.icons.Icons.Filled.Bookmark else androidx.compose.material.icons.Icons.Filled.BookmarkBorder,
        label = "",
        theme = theme,
        cornerRadius = 50.dp,
        onClick = {
            scope.launch {
                if (isBookmarked != null) {
                    bookmarkRepo.removeBookmark(tmdbId)
                } else {
                    bookmarkRepo.addBookmark(tmdbId, title, type, year, posterPath)
                }
            }
        }
    )
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    theme: com.zstream.android.theme.ZStreamTheme, 
    cornerRadius: Dp, 
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius), 
        color = theme.colors.type.text.copy(alpha = 0.05f), 
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = if (label.isEmpty()) 14.dp else 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = theme.colors.type.text)
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, color = theme.colors.type.text, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrailerGrid(trailers: List<com.zstream.android.data.model.TrailerData>, theme: com.zstream.android.theme.ZStreamTheme, context: android.content.Context) {
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
                    Text(trailer.name, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SimilarMoviesGrid(similar: List<com.zstream.android.data.model.Media>, theme: com.zstream.android.theme.ZStreamTheme, nav: NavController) {
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
                        .clickable {
                            val mediaType = movie.type
                            nav.navigate("detail/$mediaType/${movie.id}")
                        }
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
   val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
   context.startActivity(intent)
}

internal fun openYoutubeTrailer(context: android.content.Context, youtubeKey: String) {
   val url = "https://www.youtube.com/watch?v=$youtubeKey"
   val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
   context.startActivity(intent)
}
