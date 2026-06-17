package com.zstream.android.ui.screens

import com.zstream.android.Urls
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.data.model.TvDetail

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        when (val s = state) {
            is DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is DetailState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::load) { Text("Retry") }
                }
            }
            is DetailState.Movie -> MovieDetailContent(s, nav, Modifier.padding(padding))
            is DetailState.Tv -> TvDetailContent(s, vm, nav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun MovieDetailContent(state: DetailState.Movie, nav: NavController, modifier: Modifier) {
    val d = state.detail
    LazyColumn(modifier) {
        item {
            AsyncImage(model = d.backdropUrl(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${d.releaseDate?.take(4) ?: ""} • ${d.runtime?.let { "$it min" } ?: ""} • ⭐ ${"%.1f".format(d.voteAverage)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(d.overview ?: "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { nav.navigate("player/movie/${d.id}?title=${d.title.encode()}&year=${d.releaseDate?.take(4)?.toIntOrNull() ?: 0}") }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
            }
        }
    }
}

@Composable
private fun TvDetailContent(state: DetailState.Tv, vm: DetailViewModel, nav: NavController, modifier: Modifier) {
    val d = state.detail
    val season = state.selectedSeason
    var selectedSeasonNum by remember { mutableIntStateOf(season?.seasonNumber ?: 1) }

    LazyColumn(modifier) {
        item {
            AsyncImage(model = d.backdropUrl(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${d.firstAirDate?.take(4) ?: ""} • ⭐ ${"%.1f".format(d.voteAverage)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(d.overview ?: "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                // Season tabs
                val validSeasons = d.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
                if (validSeasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(validSeasons) { s ->
                            FilterChip(
                                selected = s.seasonNumber == selectedSeasonNum,
                                onClick = { selectedSeasonNum = s.seasonNumber; vm.selectSeason(s.seasonNumber) },
                                label = { Text("S${s.seasonNumber}") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        // Episodes
        season?.episodes?.let { eps ->
            items(eps) { ep ->
                ListItem(
                    headlineContent = { Text("${ep.episodeNumber}. ${ep.name ?: ""}") },
                    supportingContent = ep.overview?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 2) } },
                    leadingContent = ep.stillPath?.let { path ->
                        {
                            AsyncImage(
                                model = "${Urls.TMDB_IMAGE}w185$path",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp, 56.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        val encodedTitle = d.name.encode()
                        val year = d.firstAirDate?.take(4)?.toIntOrNull() ?: 0
                        nav.navigate("player/tv/${d.id}?season=${ep.seasonNumber}&episode=${ep.episodeNumber}&title=$encodedTitle&year=$year")
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
