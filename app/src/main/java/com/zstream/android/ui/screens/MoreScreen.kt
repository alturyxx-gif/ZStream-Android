package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant

@Composable
fun MoreScreen(
    nav: NavController,
    vm: MoreViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val theme = LocalZStreamTheme.current
    val gridState = rememberLazyGridState()
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
                    .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZsIconButton(
                    onClick = { nav.popBackStack() },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title.replace('_', ' '),
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.colors.type.emphasis,
                    )
                    Text(
                        text = "Browse everything in this section",
                        color = theme.colors.type.secondary,
                        style = MaterialTheme.typography.bodyMedium,
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
                            MediaCard(
                                media = media,
                                onClick = { nav.navigate("detail/${media.type}/${media.id}") },
                            )
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = theme.colors.global.accentA)
                                }
                            }
                        } else if (state.canLoadMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
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
    }
}
