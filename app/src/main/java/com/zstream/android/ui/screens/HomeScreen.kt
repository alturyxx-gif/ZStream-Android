package com.zstream.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.data.model.Media

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZStream", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Default.Search, null) }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, null) }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = vm::load) { Text("Retry") }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.padding(padding)) {
                item { MediaRow("Trending Movies", state.movies, nav) }
                item { MediaRow("Trending TV", state.tv, nav) }
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, items: List<Media>, nav: NavController) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { media ->
                PosterCard(media) { nav.navigate("detail/${media.type}/${media.id}") }
            }
        }
    }
}

@Composable
fun PosterCard(media: Media, onClick: () -> Unit) {
    AsyncImage(
        model = media.posterUrl(),
        contentDescription = media.displayTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    )
}
