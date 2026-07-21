package com.zstream.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.data.ReleaseSubscriptionRequest
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.realSeasonNumber
import com.zstream.android.data.model.realEpisodeNumber
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

internal class TrackedReleaseInteraction(
    val pendingKeys: Set<String>,
    val toggleMovie: (MovieDetail, Boolean) -> Unit,
    val toggleEpisode: (Int, String, String?, Episode, Boolean) -> Unit,
)

@Composable
internal fun rememberTrackedReleaseInteraction(
    trackedReleaseVm: TrackedReleaseViewModel,
): TrackedReleaseInteraction {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingKeys by trackedReleaseVm.pendingKeys.collectAsState()
    val pairedPhones by trackedReleaseVm.pairedPhones.collectAsState()
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    var tvRequest by remember { mutableStateOf<ReleaseSubscriptionRequest?>(null) }
    var selectedPhoneId by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionRequired = stringResource(R.string.detail_notification_permission_required)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterPermission
        afterPermission = null
        if (granted) action?.invoke()
        else trackedReleaseVm.reportFailure(notificationPermissionRequired)
    }

    fun runLocalChange(subscribing: Boolean, action: () -> Unit) {
        if (
            !subscribing ||
            Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            afterPermission = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun toggleMovie(detail: MovieDetail, currentlyTracked: Boolean) {
        if (isTv) {
            tvRequest = ReleaseSubscriptionRequest(
                tmdbId = detail.id,
                mediaType = "movie",
                title = detail.title,
                posterPath = detail.posterPath,
            )
        } else {
            runLocalChange(subscribing = !currentlyTracked) { trackedReleaseVm.toggleMovie(detail) }
        }
    }

    fun toggleEpisode(
        showId: Int,
        showTitle: String,
        posterPath: String?,
        episode: Episode,
        currentlyTracked: Boolean,
    ) {
        if (isTv) {
            tvRequest = ReleaseSubscriptionRequest(
                tmdbId = showId,
                mediaType = "tv",
                title = showTitle,
                posterPath = posterPath,
                seasonNumber = episode.realSeasonNumber,
                episodeNumber = episode.realEpisodeNumber,
                episodeTitle = episode.name,
            )
        } else {
            runLocalChange(subscribing = !currentlyTracked) {
                trackedReleaseVm.toggleEpisode(showId, showTitle, posterPath, episode)
            }
        }
    }

    LaunchedEffect(trackedReleaseVm) {
        trackedReleaseVm.events.collect { event ->
            when (event) {
                is TrackedReleaseUiEvent.Success -> {
                    tvRequest = null
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is TrackedReleaseUiEvent.Failure -> {
                    tvRequest = null
                    failure = event.message
                }
            }
        }
    }

    LaunchedEffect(tvRequest, pairedPhones) {
        if (tvRequest != null && pairedPhones.none { it.id == selectedPhoneId && !it.needsRepair }) {
            selectedPhoneId = pairedPhones.firstOrNull { !it.needsRepair }?.id
        }
    }

    tvRequest?.let { request ->
        val pending = request.key in pendingKeys
        if (pairedPhones.isEmpty()) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = theme.colors.modal.background,
                title = { Text(stringResource(R.string.detail_no_paired_phones), color = theme.colors.type.emphasis) },
                text = {
                    Text(
                        stringResource(R.string.detail_pair_phone_instructions),
                        color = theme.colors.type.text,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { tvRequest = null }) {
                        Text(stringResource(R.string.detail_ok), color = theme.colors.global.accentA)
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!pending) tvRequest = null },
                containerColor = theme.colors.modal.background,
                title = { Text(stringResource(R.string.detail_notify_on_phone), color = theme.colors.type.emphasis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pairedPhones.forEach { phone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !pending && !phone.needsRepair) {
                                        selectedPhoneId = phone.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedPhoneId == phone.id,
                                    onClick = null,
                                    enabled = !pending && !phone.needsRepair,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(phone.phoneName, color = theme.colors.type.text)
                                    if (phone.needsRepair) {
                                        Text(
                                            stringResource(R.string.detail_repair_phone),
                                            color = theme.colors.type.secondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    val phone = pairedPhones.firstOrNull { it.id == selectedPhoneId }
                    TextButton(
                        enabled = phone != null && !pending,
                        onClick = { phone?.let { trackedReleaseVm.subscribeOnPhone(it, request) } },
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = theme.colors.global.accentA,
                            )
                        } else {
                            Text(stringResource(R.string.detail_subscribe), color = theme.colors.global.accentA)
                        }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !pending, onClick = { tvRequest = null }) {
                        Text(stringResource(R.string.detail_cancel), color = theme.colors.type.secondary)
                    }
                },
            )
        }
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = theme.colors.modal.background,
            title = { Text(stringResource(R.string.detail_notification_update_failed), color = theme.colors.type.emphasis) },
            text = { Text(message, color = theme.colors.type.text) },
            confirmButton = {
                TextButton(onClick = { failure = null }) {
                    Text(stringResource(R.string.detail_ok), color = theme.colors.global.accentA)
                }
            },
        )
    }

    return TrackedReleaseInteraction(pendingKeys, ::toggleMovie, ::toggleEpisode)
}

@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.settings.collectAsState()
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val context = androidx.compose.ui.platform.LocalContext.current
    val trackedReleaseVm: TrackedReleaseViewModel = hiltViewModel()
    val releaseInteraction = rememberTrackedReleaseInteraction(trackedReleaseVm)

    val progress by vm.progress.collectAsState()
    val allProgress by vm.allProgress.collectAsState()
    val isBookmarked by vm.isBookmarked.collectAsState()
    val bookmark by vm.bookmark.collectAsState()
    val collection by vm.collection.collectAsState()
    val allGroups by vm.allGroups.collectAsState()
    val trailers by vm.trailers.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val isOffline by vm.isOffline.collectAsState()
    val pendingDownloads by vm.pendingDownloads.collectAsState()
    val seasonDownloadProgress by vm.seasonDownloadProgress.collectAsState()
    val hasProgress = progress?.let { it.watched >= 20 } ?: false

    if (!isTv) {
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
                Button(onClick = vm::load) { Text(stringResource(R.string.detail_retry)) }
            }
        }
        is DetailState.Movie -> {
            MovieDetailModal(
                detail = s.detail,
                certification = s.certification,
                nav = nav,
                context = context,
                theme = theme,
                isBookmarked = isBookmarked,
                onToggleBookmark = vm::toggleBookmark,
                hasProgress = hasProgress,
                onBack = onBack,
                onMarkMovieWatched = vm::markMovieWatched,
                onClearMovieWatchHistory = vm::clearMovieWatchHistory,
                downloadedMovieId = downloadedEpisodes["null|null"]?.id,
                isOffline = isOffline,
                onDownloadMovie = vm::downloadMovie,
                isMovieDownloadPending = "null|null" in pendingDownloads,
                onBookmarkCollection = vm::bookmarkCollection,
                onBrowseCollection = vm::loadCollection,
                onClearCollection = vm::clearCollection,
                collectionState = collection,
                showImageLogos = settings.enableImageLogos,
                currentGroups = bookmark?.groups.orEmpty(),
                allGroups = allGroups,
                onUpdateGroups = vm::updateBookmarkGroups,
                trailers = trailers,
                openTrailersInApp = settings.trailersOpenInApp,
                trackedReleaseVm = trackedReleaseVm,
                releasePendingKeys = releaseInteraction.pendingKeys,
                onToggleRelease = releaseInteraction.toggleMovie,
            )
        }
        is DetailState.Tv -> {
            TvDetailModal(
                detail = s.detail,
                certification = s.certification,
                selectedSeason = s.selectedSeason,
                requestedEpisodeNumber = vm.requestedEpisodeNumber,
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
                downloadedEpisodes = downloadedEpisodes,
                onDownloadEpisode = vm::downloadEpisode,
                onDownloadSeason = vm::downloadSeason,
                pendingDownloads = pendingDownloads,
                seasonDownloadProgress = seasonDownloadProgress,
                isOffline = isOffline,
                onBookmarkCollection = vm::bookmarkCollection,
                onBrowseCollection = vm::loadCollection,
                onClearCollection = vm::clearCollection,
                collectionState = collection,
                showImageLogos = settings.enableImageLogos,
                currentGroups = bookmark?.groups.orEmpty(),
                allGroups = allGroups,
                onUpdateGroups = vm::updateBookmarkGroups,
                trailers = trailers,
                openTrailersInApp = settings.trailersOpenInApp,
                trackedReleaseVm = trackedReleaseVm,
                releasePendingKeys = releaseInteraction.pendingKeys,
                onToggleEpisodeRelease = releaseInteraction.toggleEpisode,
            )
        }
    }
}
