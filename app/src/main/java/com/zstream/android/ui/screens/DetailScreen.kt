package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
            MovieDetailModal(s, nav, context, theme, bookmarkRepo)
        }
        is DetailState.Tv -> {
            TvDetailModal(s, vm, nav, context, theme, bookmarkRepo)
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
    bookmarkRepo: com.zstream.android.data.BookmarkRepository
) {

    val d = state.detail
    val genres = d.genres.orEmpty()
    val cast = d.credits?.cast.orEmpty().take(8)

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
            .verticalScroll(rememberScrollState()),
    ) {
        // Movie Backdrop Image
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            AsyncImage(model = d.backdropUrl(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), theme.colors.background.main))))

            XCloseButton(nav)

            MinimalMovieDetails(d, theme)
        }

        // Resume and Share button
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { nav.navigate("player/movie/${d.id}?title=${d.title.encode()}&year=${d.releaseDate?.take(4)?.toIntOrNull() ?: 0}") },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.buttons.primaryText)
                Spacer(Modifier.width(6.dp))
                Text("Resume", color = theme.colors.buttons.primaryText)
            }
            ActionPill(Icons.Filled.Share, "", theme, 50.dp) {
                openShareSheet(context, d.title, d.id, "movie")
            }
            BookmarkButton(
                d.id.toString(), d.title, "movie", 
                d.releaseDate?.take(4)?.toIntOrNull(), d.posterPath, 
                bookmarkRepo, theme
            )
        }

        // Movie Details
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
                d.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { GenreChip(it.name, theme) }
                }
            }
            Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSpec("Runtime", d.runtime?.let { "$it min" } ?: "-", theme)
                DetailSpec("Language", "EN", theme)
                DetailSpec("Release Date", d.releaseDate ?: "-", theme)
                DetailSpec("Rating", "PG-13", theme)
            }
        }

        SectionHeader("Cast", theme)
        CastRow(cast, theme, context)
        
        SectionHeader("Trailers", theme)
        TrailerGrid(d.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
        
        SectionHeader("Similar", theme)
        SimilarMoviesGrid(d.similar?.results.orEmpty(), theme, nav)
    }
}

// should be the same as MinimalTVDetails but are different functions because of the MovieDetail variable
@Composable
private fun BoxScope.MinimalMovieDetails(
    d: MovieDetail,
    theme: ZStreamTheme
) {
    Column(Modifier
        .align(Alignment.BottomStart)
        .padding(horizontal = 32.dp, vertical = 0.dp)) {
        d.logoUrl()?.let {
            Box(Modifier
                .height(100.dp)
                .fillMaxWidth(0.6f)) {
                AsyncImage(
                    model = it,
                    contentDescription = d.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } ?: run {
            Text(
                d.title.uppercase(),
                color = theme.colors.type.emphasis,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
        }
        MetadataRow(
            listOfNotNull(
                d.voteAverage?.let { { TmdbRating(it, theme) } },
                d.releaseDate?.let { { Text(it.take(4), fontSize = 12.sp, color = Color.White) } }
            ),
            theme,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Alignment.CenterVertically
        )
    }
}

@Composable
private fun BoxScope.XCloseButton(nav: NavController) {
    IconButton(
        onClick = { nav.popBackStack() },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(start = 24.dp, end = 24.dp, top = 48.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.75f))
    ) {
        Icon(Icons.Filled.Close, null, tint = Color.White)
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
    bookmarkRepo: com.zstream.android.data.BookmarkRepository
) {
    val d = state.detail
    val season = state.selectedSeason
    var selectedSeasonNum by remember { mutableIntStateOf(season?.seasonNumber ?: 1) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            AsyncImage(model = d.backdropUrl(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), theme.colors.background.main))))

            XCloseButton(nav)
            MinimalTVDetails(d, theme)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val firstEp = season?.episodes?.firstOrNull()
                    if (firstEp != null) {
                        nav.navigate("player/tv/${d.id}?season=${firstEp.seasonNumber}&episode=${firstEp.episodeNumber}&title=${d.name.encode()}&year=${d.firstAirDate?.take(4)?.toIntOrNull() ?: 0}")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = theme.colors.buttons.primaryText)
                Spacer(Modifier.width(6.dp))
                Text("Resume", color = theme.colors.buttons.primaryText)
            }
            ActionPill(Icons.Filled.Share, "", theme, 50.dp) {
                openShareSheet(context, d.name, d.id, "tv")
            }
            BookmarkButton(
                d.id.toString(), d.name, "tv", 
                d.firstAirDate?.take(4)?.toIntOrNull(), d.posterPath, 
                bookmarkRepo, theme
            )
            }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
               d.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
               Spacer(Modifier.height(18.dp))
               FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                   d.genres.orEmpty().forEach { GenreChip(it.name, theme) }
               }
            }
            Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
               DetailSpec("Seasons", d.numberOfSeasons?.toString() ?: "—", theme)
               DetailSpec("Language", "EN", theme)
               DetailSpec("Release Date", d.firstAirDate ?: "—", theme)
               DetailSpec("Rating", "TV-14", theme)
            }
        }

        SectionHeader("Seasons", theme)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 32.dp)) {
            items(d.seasons.orEmpty().filter { it.seasonNumber > 0 }) { s ->
               FilterChip(selected = s.seasonNumber == selectedSeasonNum, onClick = { selectedSeasonNum = s.seasonNumber; vm.selectSeason(s.seasonNumber) }, label = { Text("S${s.seasonNumber}") })
            }
        }
        Spacer(Modifier.height(16.dp))
        season?.episodes?.let { episodes ->
            SectionHeader("Episodes", theme)
            episodes.forEach { ep -> EpisodeRow(ep, d.id, d.name, nav, theme) }
        }
        
        SectionHeader("Cast", theme)
        CastRow(d.credits?.cast.orEmpty().take(8), theme, context)
        
        SectionHeader("Trailers", theme)
        TrailerGrid(d.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme, context)
        
        SectionHeader("Similar", theme)
        SimilarMoviesGrid(d.similar?.results.orEmpty(), theme, nav)
    }
}

// should be the same as MinimalMovieDetails but are different functions because of the TvDetail variable
@Composable
private fun BoxScope.MinimalTVDetails(
    d: TvDetail,
    theme: ZStreamTheme
) {
    Column(Modifier
        .align(Alignment.BottomStart)
        .padding(horizontal = 32.dp, vertical = 0.dp)) {
        d.logoUrl()?.let {
            Box(Modifier
                .height(100.dp)
                .fillMaxWidth(0.6f)) {
                AsyncImage(
                    model = it,
                    contentDescription = d.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } ?: run {
            Text(
                d.name.uppercase(),
                color = theme.colors.type.emphasis,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
        }
        MetadataRow(
            listOfNotNull(
                d.voteAverage?.let { { TmdbRating(it, theme) } },
                        d.firstAirDate?.let { { Text(it.take(4), fontSize = 12.sp, color = Color.White) } },
            ),
            theme,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Alignment.CenterVertically
        )

    }
}

@Composable
private fun EpisodeRow(ep: Episode, showId: Int, title: String, nav: NavController, theme: com.zstream.android.theme.ZStreamTheme) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.navigate("player/tv/$showId?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=${title.encode()}&year=${ep.airDate?.take(4)?.toIntOrNull() ?: 0}") }
            .padding(horizontal = 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = ep.stillPath?.let { "${Urls.TMDB_IMAGE}w185$it" }, contentDescription = null, modifier = Modifier.size(80.dp, 56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${ep.episodeNumber}. ${ep.name.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.colors.type.emphasis)
            ep.overview?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = theme.colors.type.text) }
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
    Surface(color = theme.colors.type.text.copy(alpha = 0.08f), shape = RoundedCornerShape(4.dp)) {
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
    Surface(shape = RoundedCornerShape(cornerRadius), color = theme.colors.type.text.copy(alpha = 0.05f), modifier = Modifier.clickable(onClick = onClick)) {
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

private fun openShareSheet(context: android.content.Context, title: String, id: Int, mediaType: String) {
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

private fun openCastProfile(context: android.content.Context, castId: Int, imdbId: String?) {
   val url = if (!imdbId.isNullOrEmpty()) {
       "https://www.imdb.com/name/$imdbId/"
   } else {
       "https://www.themoviedb.org/person/$castId"
   }
   val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
   context.startActivity(intent)
}

private fun openYoutubeTrailer(context: android.content.Context, youtubeKey: String) {
   val url = "https://www.youtube.com/watch?v=$youtubeKey"
   val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
   context.startActivity(intent)
}
