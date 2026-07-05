package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant

@Composable
fun MoreScreen(
    nav: NavController,
    vm: MoreViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val gridState = rememberLazyGridState()
    var editing by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var editingGroup by remember { mutableStateOf(false) }
    var tvEditMediaId by remember { mutableStateOf<Int?>(null) }
    val loadMoreThreshold = 6

    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.layoutInfo.totalItemsCount, state.media.size) {
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (state.canLoadMore && state.media.isNotEmpty() && lastVisible >= state.media.lastIndex - loadMoreThreshold) {
            vm.loadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 48.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZsIconButton(
                    onClick = { nav.popBackStack() },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (state.groupKey != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            groupIconKey(state.groupKey!!)?.let { iconKey ->
                                Icon(
                                    painter = groupIconPainter(iconKey),
                                    contentDescription = null,
                                    tint = theme.colors.type.emphasis,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = normalizeGroupName(state.groupKey!!),
                                style = MaterialTheme.typography.headlineSmall,
                                color = theme.colors.type.emphasis,
                            )
                        }
                    } else {
                        Text(
                            text = state.title.replace('_', ' '),
                            style = MaterialTheme.typography.headlineSmall,
                            color = theme.colors.type.emphasis,
                        )
                    }
                    Text(
                        text = "Browse everything in this section",
                        color = theme.colors.type.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.editable) {
                    if (editing && state.groupKey != null) {
                        ZsIconButton(
                            onClick = { editingGroup = true },
                            icon = Icons.Default.Settings,
                            contentDescription = "Section settings",
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ZsIconButton(
                        onClick = { editing = !editing },
                        icon = if (editing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (editing) "Done editing" else "Edit section",
                        variant = ZsIconButtonVariant.Secondary,
                        selected = editing,
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.colors.global.accentA)
                }
                state.error != null -> ZsStatusBanner(
                    message = state.error!!,
                    variant = ZsStatusBannerVariant.Error,
                )
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(110.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(state.media, key = { index, media -> "$index-${media.type}-${media.id}" }) { _, media ->
                            val progress = state.progressMap[media.id.toString()]
                            val progressInfo = progress?.let { getProgressInfo(it) }
                            Box {
                                MediaCard(
                                    media = media,
                                    onClick = {
                                        if (editing && isTv) tvEditMediaId = media.id
                                        else nav.navigate("detail/${media.type}/${media.id}")
                                    },
                                    percentage = progressInfo?.first,
                                    seriesLabel = progressInfo?.second,
                                    editOverlay = editing,
                                )
                                if (editing && !isTv) {
                                    if (state.bookmarkSection) {
                                        ZsIconButton(
                                            onClick = { editingBookmark = state.bookmarks[media.id.toString()] },
                                            icon = Icons.Default.Edit,
                                            contentDescription = "Edit bookmark",
                                            variant = ZsIconButtonVariant.Overlay,
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                            containerSize = 34.dp,
                                            iconSize = 16.dp,
                                        )
                                    }
                                    ZsIconButton(
                                        onClick = { vm.remove(media) },
                                        icon = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        variant = ZsIconButtonVariant.Overlay,
                                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 62.dp),
                                        containerSize = 40.dp,
                                        iconSize = 22.dp,
                                    )
                                }
                                if (editing && isTv && tvEditMediaId == media.id) {
                                    TvMediaEditMenu(
                                        onEdit = if (state.bookmarkSection) {{
                                            editingBookmark = state.bookmarks[media.id.toString()]
                                        }} else null,
                                        onRemove = { vm.remove(media) },
                                        onDismiss = { tvEditMediaId = null },
                                    )
                                }
                            }
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = theme.colors.global.accentA)
                                }
                            }
                        } else if (state.canLoadMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
                                    ZsButton(
                                        text = "View more",
                                        onClick = vm::loadMore,
                                        variant = ZsButtonVariant.Secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        editingBookmark?.let { bookmark ->
            BookmarkEditDialog(
                bookmark = bookmark,
                allGroups = state.bookmarks.values.flatMap { it.groups.orEmpty() }.distinct(),
                onSave = { title, year, groups ->
                    vm.updateBookmark(bookmark, title, year, groups)
                    editingBookmark = null
                },
                onDismiss = { editingBookmark = null },
            )
        }
        if (editingGroup && state.groupKey != null) {
            GroupEditDialog(
                group = state.groupKey!!,
                onSave = { vm.renameGroup(it); editingGroup = false },
                onDismiss = { editingGroup = false },
            )
        }
    }
}

private fun getProgressInfo(p: ProgressEntity): Pair<Float, String?>? {
    val duration = p.duration.takeIf { it > 0 } ?: return null
    val percentage = (p.watched.toFloat() / duration.toFloat()) * 100f
    if (percentage >= 95f) return null
    val seriesLabel = if (p.type == "show" && p.seasonNumber != null && p.episodeNumber != null) {
        "S${p.seasonNumber} - E${p.episodeNumber}"
    } else null
    return percentage to seriesLabel
}

@Composable
private fun BookmarkEditDialog(
    bookmark: BookmarkEntity,
    allGroups: List<String>,
    onSave: (String, Int?, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalZStreamTheme.current
    var title by remember(bookmark) { mutableStateOf(bookmark.title) }
    var year by remember(bookmark) { mutableStateOf(bookmark.year?.toString().orEmpty()) }
    var groups by remember(bookmark) { mutableStateOf(bookmark.groups.orEmpty()) }
    var creatingGroup by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = theme.colors.modal.background, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit bookmark", style = MaterialTheme.typography.titleLarge, color = theme.colors.type.emphasis)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true)
                Text("Groups", color = theme.colors.type.secondary)
                allGroups.forEach { group ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            groups = if (group in groups) groups - group else (groups + group).distinct()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(group in groups, { checked -> groups = if (checked) (groups + group).distinct() else groups - group })
                        Text(normalizeGroupName(group), color = theme.colors.type.emphasis)
                    }
                }
                TextButton(onClick = { creatingGroup = true }) { Text("Create group") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(title.trim(), year.toIntOrNull(), groups) }) { Text("Save") }
                }
            }
        }
    }
    if (creatingGroup) {
        GroupEditorDialog(
            currentGroups = groups,
            allGroups = allGroups,
            theme = theme,
            showExistingGroups = false,
            onUpdateGroups = { groups = it },
            onDismiss = { creatingGroup = false },
        )
    }
}

@Composable
private fun GroupEditDialog(group: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    var name by remember(group) { mutableStateOf(normalizeGroupName(group)) }
    var iconKey by remember(group) { mutableStateOf(groupIconKey(group) ?: "BOOKMARK") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = theme.colors.modal.background, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Section settings", style = MaterialTheme.typography.titleLarge, color = theme.colors.type.emphasis)
                OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupIconOptions.forEach { (key, icon) ->
                        Surface(
                            shape = CircleShape,
                            color = if (iconKey == key) theme.colors.global.accentA.copy(alpha = 0.22f) else Color.Transparent,
                            modifier = Modifier.clip(CircleShape).clickable { iconKey = key },
                        ) {
                            Icon(painterResource(icon), null, tint = theme.colors.type.emphasis, modifier = Modifier.padding(10.dp).size(20.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = name.isNotBlank(), onClick = { onSave("[${iconKey.lowercase()}]${name.trim()}") }) { Text("Save") }
                }
            }
        }
    }
}

private fun normalizeGroupName(group: String): String {
    val match = Regex("^\\[[^\\]]+]\\s*").find(group)
    return if (match != null) group.removePrefix(match.value).trim() else group.trim()
}

private fun groupIconKey(group: String): String? =
    Regex("^\\[([A-Za-z0-9_]+)]").find(group)?.groupValues?.getOrNull(1)?.uppercase()
