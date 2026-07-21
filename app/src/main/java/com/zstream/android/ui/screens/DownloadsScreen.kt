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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zstream.android.R
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
import java.io.File

/** e.g. "57.9 GB / 128 GB free" */
fun formatFreeSpace(freeBytes: Long, totalBytes: Long): String {
    fun gb(bytes: Long) = bytes / (1024.0 * 1024.0 * 1024.0)
    return "%.1f GB / %.0f GB free".format(gb(freeBytes), gb(totalBytes))
}

/** Prefixes a stored displayPath with where it actually lives, since the relative path alone
 * ("ZStream/Title (Year)/...") reads as the app's own folder even when the file is really on an
 * external SAF tree the user picked as their download destination. */
@Composable
private fun locationLabel(entity: DownloadEntity, path: String): String =
    if (entity.storageTreeUri != null) stringResource(R.string.downloads_external_path, path) else path

private fun posterUrl(posterPath: String?): String? {
    if (posterPath.isNullOrBlank()) return null
    if (posterPath.startsWith("http")) return posterPath
    val clean = if (posterPath.startsWith("/")) posterPath else "/$posterPath"
    return Urls.TMDB_IMAGE + "w342$clean"
}

private fun localPosterModel(posterPath: String?, thumbnailPath: String?): Any? =
    posterUrl(posterPath) ?: thumbnailPath?.let(::File)

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
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val backFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val oldFolder = pendingFolderPickAgain
            if (oldFolder == null) vm.addFolder(uri) else vm.replaceFolder(oldFolder, uri)
        }
        pendingFolderPickAgain = null
    }

    // Re-derived from uiState.items every recomposition (not a frozen snapshot captured at
    // navigation time) so live progress updates (Room Flow emissions) actually reach the
    // episode list instead of only showing up after backing out and re-entering.
    val current = selectedKey?.let { key -> uiState.items.firstOrNull { itemKey(it) == key } }

    BackHandler(enabled = selectedKey != null) { selectedKey = null }

    LaunchedEffect(isTv, uiState.items.size, selectedKey) {
        if (isTv) {
            if (uiState.items.isNotEmpty() && selectedKey == null) runCatching { firstItemFocusRequester.requestFocus() }
            else runCatching { backFocusRequester.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(
                onClick = { if (selectedKey == null) onBack() else selectedKey = null },
                isTv = isTv,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.downloads_back),
                tint = Color.White,
                modifier = Modifier.focusRequester(backFocusRequester),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(selectedTitle(current), color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(headerStatus(freeSpace, uiState), color = theme.colors.type.secondary, fontSize = 12.sp)
            }
            if (selectedKey == null) {
                TvIconButton(
                    onClick = { folderPicker.launch(null) },
                    isTv = isTv,
                    icon = Icons.Filled.Folder,
                    contentDescription = stringResource(R.string.downloads_add_folder),
                    tint = theme.colors.type.secondary,
                )
                TvIconButton(
                    onClick = { vm.rescan(uiState.folders) },
                    isTv = isTv,
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.downloads_rescan),
                    tint = theme.colors.type.secondary,
                )
            }
        }

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
                        is LibraryItem.DownloadShow -> selectedKey = itemKey(item)
                        is LibraryItem.LocalGroup -> {
                            if (item.items.size == 1 && item.mediaKind != "show") nav.navigate("localFilePlayer/${item.items.first().id}")
                            else selectedKey = itemKey(item)
                        }
                    }
                },
                onRemoveFolder = { pendingFolderDelete = it },
                onAddFolder = { folderPicker.launch(null) },
                onPickAgain = {
                    pendingFolderPickAgain = it
                    folderPicker.launch(null)
                },
                onPause = { vm.pause(it) },
                onResume = { vm.resume(it) },
                onCancel = { vm.cancel(it) },
                onDelete = { pendingDelete = it },
                onRetry = { vm.retry(it) },
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
                onRetry = { vm.retry(it) },
            )
        }
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = theme.colors.type.danger) },
            title = { Text(stringResource(R.string.downloads_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.downloads_delete_message, downloadTitleFor(entity)))
                    entity.filePath?.let { path ->
                        Spacer(Modifier.height(10.dp))
                        Text(locationLabel(entity, path), color = theme.colors.type.dimmed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = {
                var focused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(8.dp)) {
                    TextButton(onClick = { vm.delete(entity); pendingDelete = null }, modifier = Modifier.onFocusChanged { focused = it.isFocused }) {
                        Text(stringResource(R.string.downloads_delete), color = theme.colors.type.danger)
                    }
                }
            },
            dismissButton = {
                var focused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(8.dp)) {
                    TextButton(onClick = { pendingDelete = null }, modifier = Modifier.onFocusChanged { focused = it.isFocused }) { Text(stringResource(R.string.downloads_cancel)) }
                }
            },
        )
    }

    pendingFolderDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingFolderDelete = null },
            title = { Text(stringResource(R.string.downloads_remove_folder_title)) },
            text = { Text(folder.displayName) },
            confirmButton = {
                var focused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(8.dp)) {
                    TextButton(onClick = { vm.removeFolder(folder); pendingFolderDelete = null }, modifier = Modifier.onFocusChanged { focused = it.isFocused }) { Text(stringResource(R.string.downloads_remove)) }
                }
            },
            dismissButton = {
                var focused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(8.dp)) {
                    TextButton(onClick = { pendingFolderDelete = null }, modifier = Modifier.onFocusChanged { focused = it.isFocused }) { Text(stringResource(R.string.downloads_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    isTv: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = isTv && focused, shape = CircleShape, gap = 2.dp) {
        IconButton(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused }) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
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
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
) {
    if (items.isEmpty() && folders.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.downloads_empty), color = theme.colors.type.secondary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            var focused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(visible = isTv && focused, shape = RoundedCornerShape(8.dp)) {
                TextButton(onClick = onAddFolder, modifier = Modifier.onFocusChanged { focused = it.isFocused }) {
                    Text(stringResource(R.string.downloads_add_folder))
                }
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
                LibraryEntry(item, theme, isTv, if (item == items.firstOrNull()) firstItemFocusRequester else null, onOpen, onPause, onResume, onCancel, onDelete, onRetry)
            }
            items(folders, key = { "folder:${it.id}" }) { folder ->
                FolderStatusRow(folder, theme, isTv, folder.id in scanningFolderIds, onRemoveFolder, onPickAgain)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(items, key = { itemKey(it) }) { item ->
                LibraryEntry(item, theme, isTv, if (item == items.firstOrNull()) firstItemFocusRequester else null, onOpen, onPause, onResume, onCancel, onDelete, onRetry)
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
private fun LibraryEntry(
    item: LibraryItem,
    theme: ZStreamTheme,
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onOpen: (LibraryItem) -> Unit,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
) {
    if (item is LibraryItem.DownloadMovie) {
        val entity = item.entity
        DownloadItem(
            entity, theme, isTv, focusRequester,
            onPlay = { if (entity.status == DownloadStatus.DONE) onOpen(item) },
            onPause = { onPause(entity) },
            onResume = { onResume(entity) },
            onCancel = { onCancel(entity) },
            onDelete = { onDelete(entity) },
            onRetry = { onRetry(entity) },
        )
    } else {
        LibraryCard(item, theme, isTv, focusRequester) { onOpen(item) }
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
    onRetry: (DownloadEntity) -> Unit,
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
        downloads.groupBy { it.displaySeason ?: it.season ?: 0 }.forEach { (season, episodes) ->
            item("download-season:$season") { SeasonHeader(season, theme) }
            items(episodes, key = { "d:${it.id}" }) { entity ->
                DownloadItem(entity, theme, isTv, null, { if (entity.status == DownloadStatus.DONE) onPlayDownload(entity) }, { onPause(entity) }, { onResume(entity) }, { onCancel(entity) }, { onDelete(entity) }, { onRetry(entity) })
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
        text = if (season > 0) stringResource(R.string.downloads_season_number, season)
        else stringResource(R.string.downloads_season_unknown),
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
        is LibraryItem.DownloadMovie -> stringResource(R.string.downloads_movie_status, statusLabel(item.entity))
        is LibraryItem.DownloadShow -> showSummary(item.episodes)
        is LibraryItem.LocalGroup -> libraryGroupSummary(item.mediaKind, item.items)
    }
    val poster = when (item) {
        is LibraryItem.DownloadMovie -> posterUrl(item.entity.posterPath)
        is LibraryItem.DownloadShow -> posterUrl(item.posterPath)
        is LibraryItem.LocalGroup -> localPosterModel(item.posterPath, item.thumbnailPath)
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
            val status = if (isScanning) stringResource(R.string.downloads_scanning)
            else folder.lastScanError ?: folder.lastScanAt?.let { stringResource(R.string.downloads_scanned) }
            ?: stringResource(R.string.downloads_not_scanned)
            Text(status, color = if (folder.lastScanError == null) theme.colors.type.secondary else theme.colors.type.danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (folder.lastScanError != null && !isScanning) {
            var pickAgainFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(visible = isTv && pickAgainFocused, shape = RoundedCornerShape(8.dp)) {
                TextButton(onClick = { onPickAgain(folder) }, modifier = Modifier.onFocusChanged { pickAgainFocused = it.isFocused }) {
                    Text(stringResource(R.string.downloads_pick_again))
                }
            }
        }
        TvIconButton(
            onClick = { onRemove(folder) },
            isTv = isTv,
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.downloads_remove_folder),
            tint = theme.colors.type.secondary,
        )
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
private fun StatusPill(status: DownloadStatus, theme: ZStreamTheme) {
    val (label, bg, fg) = when (status) {
        DownloadStatus.QUEUED -> Triple(stringResource(R.string.downloads_queued), theme.colors.type.dimmed.copy(alpha = 0.15f), theme.colors.type.secondary)
        DownloadStatus.DOWNLOADING -> Triple(stringResource(R.string.downloads_downloading), theme.colors.buttons.purple.copy(alpha = 0.2f), theme.colors.buttons.purpleHover)
        DownloadStatus.REMUXING -> Triple(stringResource(R.string.downloads_remuxing), theme.colors.buttons.purple.copy(alpha = 0.2f), theme.colors.buttons.purpleHover)
        DownloadStatus.PAUSED -> Triple(stringResource(R.string.downloads_paused), Color.White.copy(alpha = 0.1f), theme.colors.type.secondary)
        DownloadStatus.DONE -> Triple(stringResource(R.string.downloads_ready), theme.colors.type.success.copy(alpha = 0.2f), theme.colors.type.success)
        DownloadStatus.FAILED -> Triple(stringResource(R.string.downloads_failed), theme.colors.type.danger.copy(alpha = 0.2f), theme.colors.type.danger)
        DownloadStatus.CANCELLED -> Triple(stringResource(R.string.downloads_cancelled), theme.colors.type.dimmed.copy(alpha = 0.15f), theme.colors.type.secondary)
    }
    Text(
        label.uppercase(),
        color = fg,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun QualityBadge(text: String?, bg: Color, fg: Color) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        color = fg,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    isTv: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 36.dp,
) {
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = isTv && focused, shape = CircleShape, gap = 2.dp) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(50))
                .background(background)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
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
    onRetry: () -> Unit,
) {
    val inFlight = entity.status == DownloadStatus.QUEUED || entity.status == DownloadStatus.DOWNLOADING || entity.status == DownloadStatus.REMUXING
    val isPaused = entity.status == DownloadStatus.PAUSED
    val isFailed = entity.status == DownloadStatus.FAILED
    val isTerminal = entity.status == DownloadStatus.DONE || entity.status == DownloadStatus.FAILED || entity.status == DownloadStatus.CANCELLED
    val canPlay = entity.status == DownloadStatus.DONE
    val showProgressBar = inFlight || isPaused
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = !isTv && focused, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.colors.settings.card.background)
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .then(if (!isTv && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    // On TV the row itself isn't a focus target at all -- only the action
                    // buttons beside it are -- so it shouldn't intercept D-pad focus/clicks.
                    if (isTv) Modifier else Modifier.onFocusChanged { focused = it.isFocused }.clickable(enabled = canPlay, onClick = onPlay)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterBox(posterUrl(entity.posterPath), entity.title, if (entity.type == "show") Icons.Filled.Tv else Icons.Filled.Movie, theme)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(entity.status, theme)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    downloadTitleFor(entity),
                    color = theme.colors.type.emphasis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (!isTv && canPlay) Modifier.clickable(onClick = onPlay) else Modifier,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    QualityBadge(entity.qualityLabel.takeIf { it.isNotBlank() }, theme.colors.buttons.purple.copy(alpha = 0.25f), theme.colors.buttons.purpleHover)
                    QualityBadge(entity.audioLanguage, Color.White.copy(alpha = 0.08f), theme.colors.type.secondary)
                    QualityBadge(entity.sourceId, Color.White.copy(alpha = 0.08f), theme.colors.type.secondary)
                }

                if (isFailed && entity.errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(localizedDownloadError(entity.errorMessage), color = theme.colors.type.danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                if (showProgressBar) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors.progress.background)) {
                        Box(
                            Modifier
                                .fillMaxWidth((entity.progressPercent / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(theme.colors.progress.filled.copy(alpha = if (isPaused) 0.5f else 1f))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(leftProgressLineFor(entity), color = theme.colors.type.dimmed, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(rightProgressLineFor(entity), color = theme.colors.type.dimmed, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (entity.status == DownloadStatus.DONE && entity.filePath != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(locationLabel(entity, entity.filePath), color = theme.colors.type.dimmed, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isTv) {
                // Primary (play/pause/resume/retry) and secondary (cancel/delete) actions are
                // mutually exclusive per status, so at most one of each.
                data class Action(
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val label: String,
                    val background: Color,
                    val tint: Color,
                    val onClick: () -> Unit,
                )
                val primary = when {
                    canPlay -> Action(Icons.Filled.PlayArrow, stringResource(R.string.downloads_play), theme.colors.buttons.purple, Color.White, onPlay)
                    inFlight -> Action(Icons.Filled.Pause, stringResource(R.string.downloads_pause), Color.White.copy(alpha = 0.08f), theme.colors.type.secondary, onPause)
                    isPaused -> Action(Icons.Filled.PlayArrow, stringResource(R.string.downloads_resume), theme.colors.buttons.purple, Color.White, onResume)
                    isFailed -> Action(Icons.Filled.Refresh, stringResource(R.string.downloads_retry), theme.colors.buttons.purple.copy(alpha = 0.25f), theme.colors.buttons.purpleHover, onRetry)
                    else -> null
                }
                val secondary = when {
                    inFlight || isPaused -> Action(Icons.Filled.Close, stringResource(R.string.downloads_cancel), theme.colors.buttons.danger.copy(alpha = 0.15f), theme.colors.buttons.danger, onCancel)
                    isTerminal -> Action(Icons.Filled.Delete, stringResource(R.string.downloads_delete), theme.colors.buttons.danger.copy(alpha = 0.15f), theme.colors.buttons.danger, onDelete)
                    else -> null
                }
                val actions = listOfNotNull(primary, secondary)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEachIndexed { index, action ->
                        TvSquareActionButton(
                            icon = action.icon,
                            contentDescription = action.label,
                            background = action.background,
                            tint = action.tint,
                            onClick = action.onClick,
                            size = 50.dp,
                            focusRequester = if (index == 0) focusRequester else null,
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canPlay) {
                        RoundIconButton(Icons.Filled.PlayArrow, stringResource(R.string.downloads_play), theme.colors.buttons.purple, Color.White, onPlay, size = 40.dp)
                    }
                    if (inFlight) {
                        RoundIconButton(Icons.Filled.Pause, stringResource(R.string.downloads_pause), Color.White.copy(alpha = 0.08f), theme.colors.type.secondary, onPause)
                    }
                    if (isPaused) {
                        RoundIconButton(Icons.Filled.PlayArrow, stringResource(R.string.downloads_resume), theme.colors.buttons.purple, Color.White, onResume)
                    }
                    if (isFailed) {
                        RoundIconButton(Icons.Filled.Refresh, stringResource(R.string.downloads_retry), theme.colors.buttons.purple.copy(alpha = 0.25f), theme.colors.buttons.purpleHover, onRetry)
                    }
                    if (inFlight || isPaused) {
                        RoundIconButton(Icons.Filled.Close, stringResource(R.string.downloads_cancel), theme.colors.buttons.danger.copy(alpha = 0.15f), theme.colors.buttons.danger, onCancel)
                    }
                    if (isTerminal) {
                        RoundIconButton(Icons.Filled.Delete, stringResource(R.string.downloads_delete), theme.colors.buttons.danger.copy(alpha = 0.15f), theme.colors.buttons.danger, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSquareActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(visible = focused, shape = RoundedCornerShape(10.dp), gap = 2.dp) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.4f))
        }
    }
}

@Composable
private fun PosterBox(poster: Any?, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, theme: ZStreamTheme) {
    Box(
        modifier = Modifier.size(width = 50.dp, height = 120.dp).clip(RoundedCornerShape(8.dp)).background(theme.colors.background.secondary),
        contentAlignment = Alignment.Center,
    ) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(icon, contentDescription = null, tint = theme.colors.type.dimmed.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun selectedTitle(item: LibraryItem?): String =
    item?.let { itemTitle(it) } ?: stringResource(R.string.downloads_title)

@Composable
private fun headerStatus(freeSpace: FreeSpaceInfo, state: DownloadsUiState): String {
    val scanning = state.scanningFolderIds.size
    val folderCount = state.folders.size
    fun gb(bytes: Long) = bytes / (1024.0 * 1024.0 * 1024.0)
    val storage = stringResource(
        R.string.downloads_free_space,
        gb(freeSpace.freeBytes),
        gb(freeSpace.totalBytes),
    )
    return when {
        scanning > 0 -> stringResource(R.string.downloads_storage_scanning, storage, scanning)
        folderCount > 0 -> stringResource(
            R.string.downloads_storage_folders,
            storage,
            pluralStringResource(R.plurals.downloads_local_folder_count, folderCount, folderCount),
        )
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

@Composable
private fun showSummary(episodes: List<DownloadEntity>): String {
    val seasons = episodes.mapNotNull { it.season }.distinct().size
    val status = when {
        episodes.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.REMUXING } -> stringResource(R.string.downloads_downloading)
        episodes.any { it.status == DownloadStatus.QUEUED } -> stringResource(R.string.downloads_queued)
        episodes.any { it.status == DownloadStatus.PAUSED } -> stringResource(R.string.downloads_paused)
        episodes.all { it.status == DownloadStatus.DONE } -> stringResource(R.string.downloads_downloaded)
        episodes.any { it.status == DownloadStatus.FAILED } -> stringResource(R.string.downloads_has_failures)
        else -> stringResource(R.string.downloads_not_complete)
    }
    val seasonLabel = pluralStringResource(R.plurals.downloads_season_count, seasons, seasons)
    val episodeLabel = pluralStringResource(R.plurals.downloads_episode_count, episodes.size, episodes.size)
    return stringResource(R.string.downloads_summary, seasonLabel, episodeLabel, status)
}

/** [items] are pure local (never actual downloads) — "Downloaded" here means TMDB-tracked, "Local" means unmatched. */
@Composable
private fun libraryGroupSummary(mediaKind: String, items: List<LocalMediaEntity>): String {
    val isTracked = items.any { it.tmdbId != null }
    val status = stringResource(if (isTracked) R.string.downloads_downloaded else R.string.downloads_local)
    return when (mediaKind) {
        "show" -> {
            val seasons = items.mapNotNull { it.season }.distinct().size
            val seasonLabel = pluralStringResource(R.plurals.downloads_season_count, seasons, seasons)
            val episodeLabel = pluralStringResource(R.plurals.downloads_episode_count, items.size, items.size)
            stringResource(R.string.downloads_summary, seasonLabel, episodeLabel, status)
        }
        "movie" -> stringResource(R.string.downloads_movie_status, status)
        else -> stringResource(
            R.string.downloads_file_status,
            pluralStringResource(R.plurals.downloads_file_count, items.size, items.size),
            status,
        )
    }
}

@Composable
private fun downloadTitleFor(entity: DownloadEntity): String {
    if (entity.type != "show") return entity.title
    val season = (entity.displaySeason ?: entity.season)?.toString()?.padStart(2, '0') ?: "?"
    val episode = (entity.displayEpisode ?: entity.episode)?.toString()?.padStart(2, '0') ?: "?"
    return stringResource(R.string.system_release_episode_subject, entity.title, season, episode)
}

@Composable
private fun localMediaTitle(media: LocalMediaEntity): String {
    if (media.mediaKind != "show") return media.displayName
    val season = media.season?.toString()?.padStart(2, '0') ?: "?"
    val episode = media.episode?.toString()?.padStart(2, '0') ?: "?"
    val episodeLabel = stringResource(R.string.system_release_episode_subject, "", season, episode).trim()
    return "$episodeLabel ${media.displayName}"
}

@Composable
private fun localizedDownloadError(message: String?): String = when (message) {
    null -> stringResource(R.string.downloads_unknown_error)
    "Interrupted" -> stringResource(R.string.system_download_interrupted)
    "Download failed" -> stringResource(R.string.downloads_failed)
    else -> message
}

@Composable
private fun statusLabel(entity: DownloadEntity): String = when (entity.status) {
    DownloadStatus.QUEUED -> stringResource(R.string.downloads_queued)
    DownloadStatus.DOWNLOADING -> entity.statusMessage?.let {
        stringResource(R.string.downloads_stalled, entity.progressPercent, it)
    } ?: stringResource(R.string.downloads_downloading_percent, entity.progressPercent)
    DownloadStatus.REMUXING -> stringResource(R.string.downloads_remuxing_percent, entity.progressPercent)
    DownloadStatus.PAUSED -> stringResource(R.string.downloads_paused_percent, entity.progressPercent)
    DownloadStatus.DONE -> stringResource(R.string.downloads_downloaded)
    DownloadStatus.FAILED -> stringResource(
        R.string.downloads_failed_reason,
        localizedDownloadError(entity.errorMessage),
    )
    DownloadStatus.CANCELLED -> stringResource(R.string.downloads_cancelled)
}

private fun formatSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    return if (kb >= 1024.0) "%.1f MB/s".format(kb / 1024.0) else "%.0f KB/s".format(kb)
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}

@Composable
private fun leftProgressLineFor(entity: DownloadEntity): String {
    val parts = mutableListOf<String>()
    parts.add("${entity.progressPercent}%")
    val speed = entity.speedBps
    if (speed != null && speed > 0) {
        parts.add(formatSpeed(speed))
        val total = entity.estimatedTotalBytes
        val downloaded = entity.bytesDownloaded
        if (total != null && downloaded != null && total > downloaded) {
            parts.add(stringResource(R.string.downloads_eta, formatEta((total - downloaded) / speed)))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun rightProgressLineFor(entity: DownloadEntity): String {
    entity.statusMessage?.let { return it }
    val parts = mutableListOf<String>()
    if (entity.segTotal > 0) {
        parts.add(stringResource(R.string.downloads_segments, entity.segDone, entity.segTotal))
    } else {
        val downloaded = entity.bytesDownloaded
        val total = entity.estimatedTotalBytes
        if (downloaded != null) {
            parts.add(if (total != null && total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)}" else formatBytes(downloaded))
        }
    }
    if (entity.remuxTotal > 0) {
        parts.add(stringResource(R.string.downloads_muxed, entity.remuxDone, entity.remuxTotal))
    }
    return parts.joinToString(" · ")
}
