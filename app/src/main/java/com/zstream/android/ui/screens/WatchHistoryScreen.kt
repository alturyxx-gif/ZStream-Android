package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.data.remote.ProgressResponse
import com.zstream.android.theme.LocalZStreamTheme

/** Mirrors p-stream shouldShowProgress: watched>=20s AND not within 120s of end */
private fun shouldShowProgress(p: com.zstream.android.data.local.entity.ProgressEntity): Boolean {
    val watched = p.watched.toLong()
    val duration = p.duration.toLong()
    if (watched < 20) return false
    if (duration > 0 && (duration - watched) < 120) return false
    return true
}

@Composable
fun WatchHistoryScreen(nav: NavController) {
    val activity = LocalActivity.current as ComponentActivity
    val accountVm: AccountViewModel = hiltViewModel(activity)
    val theme = LocalZStreamTheme.current
    val progressList by accountVm.progress.collectAsState()

    val items = remember(progressList) {
        progressList
            .filter { shouldShowProgress(it) }
            .sortedByDescending { it.updatedAt }
            // Deduplicate: one card per tmdbId+episodeNumber combo, keep most recent
            .distinctBy { "${it.tmdbId}:${it.episodeNumber}:${it.seasonNumber}" }
    }

    Column(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("Watch History", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = theme.colors.type.secondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(items, key = { it.tmdbId + (it.episodeId ?: "") }) { p ->
                    WatchHistoryItem(p, theme) {
                        val mediaType = if (p.type == "show") "tv" else "movie"
                        val base = "player/$mediaType/${p.tmdbId}?title=${p.title}&year=${p.year}"
                        val route = if (p.seasonNumber != null && p.episodeNumber != null)
                            "$base&season=${p.seasonNumber}&episode=${p.episodeNumber}"
                        else base
                        nav.navigate(route)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryItem(
    p: com.zstream.android.data.local.entity.ProgressEntity,
    theme: com.zstream.android.theme.ZStreamTheme,
    onClick: () -> Unit,
) {
    val watched = p.watched.toLong()
    val duration = p.duration.toLong()
    val pct = if (duration > 0) (watched.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.settings.card.background)
            .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster
        AsyncImage(
            model = p.posterPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 60.dp, height = 88.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(p.title, color = theme.colors.type.emphasis, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val subtitle = buildString {
                append(p.year ?: "")
                if (p.seasonNumber != null) append(" · S${p.seasonNumber}")
                if (p.episodeNumber != null) append("E${p.episodeNumber}")
            }
            Text(subtitle, color = theme.colors.type.secondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            // Progress bar
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.colors.mediaCard.barColor)
            ) {
                Box(
                    Modifier.fillMaxWidth(pct).fillMaxHeight()
                        .background(theme.colors.mediaCard.barFillColor)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("${(pct * 100).toInt()}%", color = theme.colors.type.dimmed, fontSize = 11.sp)
        }
    }
}
