package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.Urls
import com.zstream.android.data.model.CastMember
import com.zstream.android.data.model.Episode
import com.zstream.android.theme.LocalZStreamTheme

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val theme = LocalZStreamTheme.current

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
        is DetailState.Movie -> MovieDetailModal(s, nav, theme)
        is DetailState.Tv -> TvDetailModal(s, vm, nav, theme)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieDetailModal(state: DetailState.Movie, nav: NavController, theme: com.zstream.android.theme.ZStreamTheme) {
    val d = state.detail
    val genres = d.genres.orEmpty()
    val cast = d.credits?.cast.orEmpty().take(8)

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            AsyncImage(model = d.backdropUrl(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), theme.colors.background.main))))
            
            IconButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
            ) {
                Icon(Icons.Filled.Close, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 18.dp)) {
                d.logoUrl()?.let {
                    Box(Modifier.height(100.dp).fillMaxWidth(0.6f)) {
                        AsyncImage(model = it, contentDescription = d.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                } ?: run {
                    Text(d.title.uppercase(), color = theme.colors.type.emphasis, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                }
                Spacer(Modifier.height(8.dp))
                MetadataRow(listOfNotNull(d.releaseDate?.take(4), d.runtime?.let { "$it min" }, d.voteAverage?.let { "⭐ ${"%.1f".format(it)}" }))
            }
        }

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
            ActionPill(Icons.Filled.Share, "Share", theme)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
                d.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.colors.type.text, fontSize = 14.sp) }
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { GenreChip(it.name, theme) }
                }
            }
            Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSpec("Runtime", d.runtime?.let { "$it min" } ?: "—", theme)
                DetailSpec("Language", "EN", theme)
                DetailSpec("Release Date", d.releaseDate ?: "—", theme)
                DetailSpec("Rating", "PG-13", theme)
            }
        }

        SectionHeader("Cast", theme)
        CastRow(cast, theme)
        
        SectionHeader("Trailers", theme)
        TrailerGrid(d.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme)
        
        SectionHeader("Similar", theme)
        SimilarMoviesGrid(d.similar?.results.orEmpty(), theme)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvDetailModal(state: DetailState.Tv, vm: DetailViewModel, nav: NavController, theme: com.zstream.android.theme.ZStreamTheme) {
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
            
            IconButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
            ) {
                Icon(Icons.Filled.Close, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 18.dp)) {
               d.logoUrl()?.let {
                    Box(Modifier.height(100.dp).fillMaxWidth(0.6f)) {
                       AsyncImage(model = it, contentDescription = d.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                } ?: run {
                    Text(d.name.uppercase(), color = theme.colors.type.emphasis, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                }
                Spacer(Modifier.height(8.dp))
                MetadataRow(listOfNotNull(d.firstAirDate?.take(4), d.voteAverage?.let { "⭐ ${"%.1f".format(it)}" }))
            }
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
            ActionPill(Icons.Filled.Share, "Share", theme)
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
        CastRow(d.credits?.cast.orEmpty().take(8), theme)
        
        SectionHeader("Trailers", theme)
        TrailerGrid(d.videos?.results?.filter { it.site == "YouTube" && it.type == "Trailer" }.orEmpty(), theme)
        
        SectionHeader("Similar", theme)
        SimilarMoviesGrid(d.similar?.results.orEmpty(), theme)
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
private fun CastRow(cast: List<CastMember>, theme: com.zstream.android.theme.ZStreamTheme) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
        items(cast) { member ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(105.dp)) {
                AsyncImage(
                    model = member.profilePath?.let { "${Urls.TMDB_IMAGE}w185$it" },
                    contentDescription = member.name,
                    modifier = Modifier.size(96.dp).clip(CircleShape).border(1.dp, theme.colors.type.text.copy(alpha = 0.08f), CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(10.dp))
                Text(member.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                Text(member.character.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Text(title, modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = theme.colors.type.emphasis)
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
private fun MetadataRow(parts: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text("•", fontSize = 12.sp)
            Text(part, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, theme: com.zstream.android.theme.ZStreamTheme) {
    Surface(shape = RoundedCornerShape(6.dp), color = theme.colors.type.text.copy(alpha = 0.05f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = theme.colors.type.text)
            Spacer(Modifier.width(6.dp))
            Text(label, color = theme.colors.type.text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TrailerGrid(trailers: List<com.zstream.android.data.model.TrailerData>, theme: com.zstream.android.theme.ZStreamTheme) {
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
                        .clickable { /* Open trailer */ }
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
private fun SimilarMoviesGrid(similar: List<com.zstream.android.data.model.Media>, theme: com.zstream.android.theme.ZStreamTheme) {
    if (similar.isEmpty()) {
        Text("No similar movies available", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), color = theme.colors.type.secondary)
        return
    }
    
    Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(similar.take(6)) { movie ->
                Column(modifier = Modifier.width(140.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(8.dp)).background(theme.colors.modal.background)) {
                        movie.posterUrl()?.let {
                            AsyncImage(model = it, contentDescription = movie.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(movie.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = theme.colors.type.emphasis)
                    val year = (movie.releaseDate ?: movie.firstAirDate)?.take(4) ?: "—"
                    Text("${movie.type} • $year", style = MaterialTheme.typography.labelSmall, color = theme.colors.type.secondary)
                }
            }
        }
    }
}
