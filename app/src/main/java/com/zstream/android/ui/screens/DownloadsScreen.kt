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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

@Composable
fun DownloadsScreen(nav: NavController) {
    val vm: DownloadsViewModel = hiltViewModel()
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val downloads by vm.downloads.collectAsState()
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Column(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("Downloads", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = theme.colors.type.secondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(items = downloads, key = { it.id }) { entity ->
                    DownloadItem(entity, theme, onDelete = { pendingDelete = entity })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = { Text(downloadTitleFor(entity)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(entity); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private fun downloadTitleFor(entity: DownloadEntity): String {
    if (entity.type != "show") return entity.title
    val season = entity.season?.toString()?.padStart(2, '0') ?: "?"
    val episode = entity.episode?.toString()?.padStart(2, '0') ?: "?"
    return "${entity.title} S${season}E$episode"
}

private fun statusLabel(entity: DownloadEntity): String = when (entity.status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading ${entity.progressPercent}%"
    DownloadStatus.REMUXING -> "Remuxing ${entity.progressPercent}%"
    DownloadStatus.DONE -> "Downloaded"
    DownloadStatus.FAILED -> "Failed: ${entity.errorMessage ?: "Unknown error"}"
    DownloadStatus.CANCELLED -> "Cancelled"
}

@Composable
private fun DownloadItem(entity: DownloadEntity, theme: ZStreamTheme, onDelete: () -> Unit) {
    val inFlight = entity.status == DownloadStatus.QUEUED ||
        entity.status == DownloadStatus.DOWNLOADING ||
        entity.status == DownloadStatus.REMUXING

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.settings.card.background)
            .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(enabled = !inFlight, onClick = onDelete)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(theme.colors.background.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    entity.status == DownloadStatus.FAILED -> Icons.Filled.Error
                    entity.type == "show" -> Icons.Filled.Tv
                    else -> Icons.Filled.Movie
                },
                contentDescription = null,
                tint = if (entity.status == DownloadStatus.FAILED) theme.colors.type.danger else theme.colors.type.dimmed.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                downloadTitleFor(entity), color = theme.colors.type.emphasis, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text("${entity.qualityLabel} · ${entity.sourceId}", color = theme.colors.type.secondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            if (inFlight) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.colors.mediaCard.barColor)
                ) {
                    Box(
                        Modifier.fillMaxWidth(entity.progressPercent / 100f).fillMaxHeight()
                            .background(theme.colors.mediaCard.barFillColor)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                statusLabel(entity),
                color = if (entity.status == DownloadStatus.FAILED) theme.colors.type.danger else theme.colors.type.dimmed,
                fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        if (!inFlight) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = theme.colors.type.secondary)
            }
        }
    }
}
