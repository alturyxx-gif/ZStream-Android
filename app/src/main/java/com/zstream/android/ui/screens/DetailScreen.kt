package com.zstream.android.ui.screens

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val context = androidx.compose.ui.platform.LocalContext.current

    val progress by vm.progress.collectAsState()
    val allProgress by vm.allProgress.collectAsState()
    val isBookmarked by vm.isBookmarked.collectAsState()
    val hasProgress = progress?.let { it.watched >= 20 } ?: false

    if (!LocalIsTv.current) {
        val d = context as? DialogWindowProvider
        DisposableEffect(d) {
            d?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            onDispose { }
        }
    }

    when (val s = state) {
        is DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.colors.global.accentA)
        }
        is DetailState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ZsStatusBanner(
                    message = s.message,
                    variant = ZsStatusBannerVariant.Error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::load) { Text("Retry") }
            }
        }
        is DetailState.Movie -> {
            MovieDetailModal(
                detail = s.detail,
                nav = nav,
                context = context,
                theme = theme,
                isBookmarked = isBookmarked,
                onToggleBookmark = vm::toggleBookmark,
                hasProgress = hasProgress,
                onBack = onBack,
                onMarkMovieWatched = vm::markMovieWatched,
                onClearMovieWatchHistory = vm::clearMovieWatchHistory,
            )
        }
        is DetailState.Tv -> {
            TvDetailModal(
                detail = s.detail,
                selectedSeason = s.selectedSeason,
                allProgress = allProgress,
                nav = nav,
                context = context,
                theme = theme,
                isBookmarked = isBookmarked,
                onToggleBookmark = vm::toggleBookmark,
                hasProgress = hasProgress,
                resumeProgress = progress,
                onBack = onBack,
                onSelectSeason = vm::selectSeason,
                onMarkEpisodeWatched = vm::markEpisodeWatched,
                onClearEpisodeWatchHistory = vm::clearEpisodeWatchHistory,
                onMarkSeasonWatched = vm::markSeasonWatched,
                onClearSeasonWatchHistory = vm::clearSeasonWatchHistory,
            )
        }
    }
}
