@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zstream.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.Urls
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

/** e.g. "57.9 GB / 128 GB free" */
fun formatFreeSpace(freeBytes: Long, totalBytes: Long): String {
    fun gb(bytes: Long) = bytes / (1024.0 * 1024.0 * 1024.0)
    return "%.1f GB / %.0f GB free".format(gb(freeBytes), gb(totalBytes))
}

private fun posterUrl(posterPath: String?): String? {
    if (posterPath.isNullOrBlank()) return null
    if (posterPath.startsWith("http")) return posterPath
    val clean = if (posterPath.startsWith("/")) posterPath else "/$posterPath"
    return Urls.TMDB_IMAGE + "w342$clean"
}

@Composable
fun DownloadsScreen(nav: NavController) {
    val vm: DownloadsViewModel = hiltViewModel()
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val uiState by vm.uiState.collectAsState()
    val freeSpace by vm.freeSpace.collectAsState()
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var pendingFolderDelete by remember { mutableStateOf<LocalLibraryFolderEntity?>(null) }
    var pendingFolderPickAgain by remember { mutableStateOf<LocalLibraryFolderEntity?>(null) }
    var selected by remember { mutableStateOf<LibraryItem?>(null) }
    val backFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val oldFolder = pendingFolderPickAgain
            if (oldFolder == null) vm.addFolder(uri) else vm.replaceFolder(oldFolder, uri)
        }
        pendingFolderPickAgain = null
    }

    BackHandler(enabled = selected != null) { selected = null }

    LaunchedEffect(isTv, uiState.items.size, selected) {
        if (isTv) {
            if (uiState.items.isNotEmpty() && selected == null) runCatching { firstItemFocusRequester.requestFocus() }
            else runCatching { backFocusRequester.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selected == null) onBack() else selected = null }, modifier = Modifier.focusRequester(backFocusRequester)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(selectedTitle(selected), color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(headerStatus(freeSpace, uiState), color = theme.colors.type.secondary, fontSize = 12.sp)
            }
            if (selected == null) {
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Filled.Folder, contentDescription = "Add Folder", tint = theme.colors.type.secondary)
                }
                IconButton(onClick = { vm.rescan(uiState.folders) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Rescan", tint = theme.colors.type.secondary)
                }
            }
        }

        val current = selected
        if (current == null) {
            LibraryContent(
                items = uiState.items,
                folders = uiState.folders,
                scanningFolderIds = uiState.scanningFolderIds,
                isTv = isTv,
                theme = theme,
                firstItemFocusRequester = firstItemFocusRequester,
                onOpen = { item ->
                    when (item) {
                        is LibraryItem.DownloadMovie -> if (item.entity.status == DownloadStatus.DONE) nav.navigate("localPlayer/${item.entity.id}")
                        is LibraryItem.DownloadShow -> selected = item
                        is LibraryItem.LocalGroup -> {
                            if (item.items.size == 1 && item.mediaKind != "show") nav.navigate("localFilePlayer/${item.items.first().id}")
                            else selected = item
                        }
                    }
                },
                onRemoveFolder = { pendingFolderDelete = it },
                onAddFolder = { folderPicker.launch(null) },
                onPickAgain = {
                    pendingFolderPickAgain = it
                    folderPicker.launch(null)
                },
            )
        } else {
            DetailList(
                libraryItem = current,
                theme = theme,
                isTv = isTv,
                onPlayDownload = { nav.navigate("localPlayer/${it.id}") },
                onPlayLocal = { nav.navigate("localFilePlayer/${it.id}") },
                onPause = { vm.pause(it) },
                onResume = { vm.resume(it) },
                onCancel = { vm.cancel(it) },
                onDelete = { pendingDelete = it },
            )
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
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    pendingFolderDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingFolderDelete = null },
            title = { Text("Remove folder?") },
            text = { Text(folder.displayName) },
            confirmButton = {
                TextButton(onClick = { vm.removeFolder(folder); pendingFolderDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingFolderDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LibraryContent(
    items: List<LibraryItem>,
    folders: List<LocalLibraryFolderEntity>,
    scanningFolderIds: Set<Long>,
    isTv: Boolean,
    theme: ZStreamTheme,
    firstItemFocusRequester: FocusRequester,
    onOpen: (LibraryItem) -> Unit,
    onRemoveFolder: (LocalLibraryFolderEntity) -> Unit,
    onAddFolder: () -> Unit,
    onPickAgain: (LocalLibraryFolderEntity) -> Unit,
) {
    if (items.isEmpty() && folders.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No downloads or local folders yet", color = theme.colors.type.secondary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAddFolder) {
                Text("Add Folder")
            }
        }
        return
    }
    if (isTv) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer(firstItemFocusRequester),
        ) {
            items(items, key = { itemKey(it) }) { item ->
                LibraryCard(item, theme, isTv, if (item == items.firstOrNull()) firstItemFocusRequester else null) { onOpen(item) }
            }
            items(folders, key = { "folder:${it.id}" }) { folder ->
                FolderStatusRow(folder, theme, isTv, folder.id in scanningFolderIds, onRemoveFolder, onPickAgain)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(items, key = { itemKey(it) }) { item ->
                LibraryCard(item, theme, isTv, if (item == items.firstOrNull()) firstItemFocusRequester else null) { onOpen(item) }
                Spacer(Modifier.height(10.dp))
            }
            items(folders, key = { "folder:${it.id}" }) { folder ->
                FolderStatusRow(folder, theme, isTv, folder.id in scanningFolderIds, onRemoveFolder, onPickAgain)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DetailList(
    libraryItem: LibraryItem,
    theme: ZStreamTheme,
    isTv: Boolean,
    onPlayDownload: (DownloadEntity) -> Unit,
    onPlayLocal: (LocalMediaEntity) -> Unit,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    val downloads = when (libraryItem) {
        is LibraryItem.DownloadShow -> libraryItem.episodes
        else -> emptyList()
    }
    val local = when (libraryItem) {
        is LibraryItem.LocalGroup -> libraryItem.items
        else -> emptyList()
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = if (isTv) Modifier.focusRestorer() else Modifier,
    ) {
        downloads.groupBy { it.season ?: 0 }.forEach { (season, episodes) ->
            item("download-season:$season") { SeasonHeader(season, theme) }
            items(episodes, key = { "d:${it.id}" }) { entity ->
                DownloadItem(entity, theme, isTv, null, { if (entity.status == DownloadStatus.DONE) onPlayDownload(entity) }, { onPause(entity) }, { onResume(entity) }, { onCancel(entity) }, { onDelete(entity) })
                Spacer(Modifier.height(10.dp))
            }
        }
        local.groupBy { it.season ?: 0 }.forEach { (season, episodes) ->
            if (libraryItem is LibraryItem.LocalGroup && libraryItem.mediaKind == "show") item("local-season:$season") { SeasonHeader(season, theme) }
            items(episodes, key = { "l:${it.id}" }) { media ->
                LocalMediaRow(media, theme, isTv) { onPlayLocal(media) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SeasonHeader(season: Int, theme: ZStreamTheme) {
    Text(
        text = if (season > 0) "Season $season" else "Season unknown",
        color = theme.colors.type.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun LibraryCard(item: LibraryItem, theme: ZStreamTheme, isTv: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val title = itemTitle(item)
    val subtitle = when (item) {
        is LibraryItem.DownloadMovie -> statusLabel(item.entity)
        is LibraryItem.DownloadShow -> showSummary(item.episodes)
        is LibraryItem.LocalGroup -> if (item.mediaKind == "show") "${item.items.size} episodes" else "${item.items.size} files"
    }
    val poster = when (item) {
        is LibraryItem.DownloadMovie -> posterUrl(item.entity.posterPath)
        is LibraryItem.DownloadShow -> posterUrl(item.posterPath)
        is LibraryItem.LocalGroup -> null
    }
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = isTv && focused,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.settings.card.background)
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterBox(poster, title, icon = if (item is LibraryItem.DownloadShow || (item is LibraryItem.LocalGroup && item.mediaKind == "show")) Icons.Filled.Tv else Icons.Filled.Movie, theme)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = theme.colors.type.emphasis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = theme.colors.type.secondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FolderStatusRow(
    folder: LocalLibraryFolderEntity,
    theme: ZStreamTheme,
    isTv: Boolean,
    isScanning: Boolean,
    onRemove: (LocalLibraryFolderEntity) -> Unit,
    onPickAgain: (LocalLibraryFolderEntity) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(10.dp)) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.background.secondary.copy(alpha = 0.45f))
            .border(1.dp, theme.colors.type.divider.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.hasFocus }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, null, tint = theme.colors.type.secondary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.displayName, color = theme.colors.type.emphasis, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = if (isScanning) "Scanning..." else folder.lastScanError ?: folder.lastScanAt?.let { "Scanned" } ?: "Not scanned yet"
            Text(status, color = if (folder.lastScanError == null) theme.colors.type.secondary else theme.colors.type.danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (folder.lastScanError != null && !isScanning) {
            TextButton(onClick = { onPickAgain(folder) }) {
                Text("Pick Again")
            }
        }
        IconButton(onClick = { onRemove(folder) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove folder", tint = theme.colors.type.secondary)
        }
    }
    }
}

@Composable
private fun LocalMediaRow(media: LocalMediaEntity, theme: ZStreamTheme, isTv: Boolean, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.colors.settings.card.background).onFocusChanged { focused = it.isFocused }.clickable(onClick = onPlay).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (media.mediaKind == "show") Icons.Filled.Tv else Icons.Filled.Movie, null, tint = theme.colors.type.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(localMediaTitle(media), color = theme.colors.type.emphasis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.relativePath, color = theme.colors.type.dimmed, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DownloadItem(
    entity: DownloadEntity,
    theme: ZStreamTheme,
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val inFlight = entity.status == DownloadStatus.QUEUED || entity.status == DownloadStatus.DOWNLOADING || entity.status == DownloadStatus.REMUXING
    val isPaused = entity.status == DownloadStatus.PAUSED
    val isTerminal = entity.status == DownloadStatus.DONE || entity.status == DownloadStatus.FAILED || entity.status == DownloadStatus.CANCELLED
    val canPlay = entity.status == DownloadStatus.DONE
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.settings.card.background)
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.hasFocus }
                .clickable(enabled = canPlay, onClick = onPlay)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterBox(posterUrl(entity.posterPath), entity.title, if (entity.type == "show") Icons.Filled.Tv else Icons.Filled.Movie, theme)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(downloadTitleFor(entity), color = theme.colors.type.emphasis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${entity.qualityLabel} · ${entity.sourceId}", color = theme.colors.type.secondary, fontSize = 12.sp)
                if (inFlight || isPaused) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors.mediaCard.barColor)) {
                        Box(Modifier.fillMaxWidth(entity.progressPercent / 100f).fillMaxHeight().background(theme.colors.mediaCard.barFillColor))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(statusLabel(entity), color = if (entity.status == DownloadStatus.FAILED) theme.colors.type.danger else theme.colors.type.dimmed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (inFlight && entity.status != DownloadStatus.REMUXING || isPaused) {
                IconButton(onClick = if (isPaused) onResume else onPause) {
                    Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (isPaused) "Resume" else "Pause", tint = theme.colors.type.secondary)
                }
            }
            if (inFlight || isPaused) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = theme.colors.type.secondary)
                }
            }
            if (isTerminal) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = theme.colors.type.secondary)
                }
            }
        }
    }
}

@Composable
private fun PosterBox(poster: String?, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, theme: ZStreamTheme) {
    Box(
        modifier = Modifier.size(width = 60.dp, height = 88.dp).clip(RoundedCornerShape(6.dp)).background(theme.colors.background.secondary),
        contentAlignment = Alignment.Center,
    ) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(icon, contentDescription = null, tint = theme.colors.type.dimmed.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
        }
    }
}

private fun selectedTitle(item: LibraryItem?): String = item?.let { itemTitle(it) } ?: "Downloads"

private fun headerStatus(freeSpace: FreeSpaceInfo, state: DownloadsUiState): String {
    val scanning = state.scanningFolderIds.size
    val folderCount = state.folders.size
    val storage = formatFreeSpace(freeSpace.freeBytes, freeSpace.totalBytes)
    return when {
        scanning > 0 -> "$storage · Scanning $scanning"
        folderCount > 0 -> "$storage · $folderCount local ${if (folderCount == 1) "folder" else "folders"}"
        else -> storage
    }
}

private fun itemKey(item: LibraryItem): String = when (item) {
    is LibraryItem.DownloadMovie -> "download:${item.entity.id}"
    is LibraryItem.DownloadShow -> item.key
    is LibraryItem.LocalGroup -> item.key
}

private fun itemTitle(item: LibraryItem): String = when (item) {
    is LibraryItem.DownloadMovie -> item.entity.title
    is LibraryItem.DownloadShow -> item.title
    is LibraryItem.LocalGroup -> item.title
}

private fun showSummary(episodes: List<DownloadEntity>): String {
    val seasons = episodes.mapNotNull { it.season }.distinct().size
    val status = when {
        episodes.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.REMUXING } -> "Downloading"
        episodes.any { it.status == DownloadStatus.QUEUED } -> "Queued"
        episodes.any { it.status == DownloadStatus.PAUSED } -> "Paused"
        episodes.all { it.status == DownloadStatus.DONE } -> "Downloaded"
        episodes.any { it.status == DownloadStatus.FAILED } -> "Has failures"
        else -> "Not complete"
    }
    val seasonLabel = if (seasons == 1) "1 season" else "$seasons seasons"
    val episodeLabel = if (episodes.size == 1) "1 episode" else "${episodes.size} episodes"
    return "$seasonLabel · $episodeLabel · $status"
}

private fun downloadTitleFor(entity: DownloadEntity): String {
    if (entity.type != "show") return entity.title
    val season = entity.season?.toString()?.padStart(2, '0') ?: "?"
    val episode = entity.episode?.toString()?.padStart(2, '0') ?: "?"
    return "${entity.title} S${season}E$episode"
}

private fun localMediaTitle(media: LocalMediaEntity): String {
    if (media.mediaKind != "show") return media.displayName
    val season = media.season?.toString()?.padStart(2, '0') ?: "?"
    val episode = media.episode?.toString()?.padStart(2, '0') ?: "?"
    return "S${season}E$episode ${media.displayName}"
}

private fun statusLabel(entity: DownloadEntity): String = when (entity.status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading ${entity.progressPercent}%"
    DownloadStatus.REMUXING -> "Remuxing ${entity.progressPercent}%"
    DownloadStatus.PAUSED -> "Paused at ${entity.progressPercent}%"
    DownloadStatus.DONE -> "Downloaded"
    DownloadStatus.FAILED -> "Failed: ${entity.errorMessage ?: "Unknown error"}"
    DownloadStatus.CANCELLED -> "Cancelled"
}
