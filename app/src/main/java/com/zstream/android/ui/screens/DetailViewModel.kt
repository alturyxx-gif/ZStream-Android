package com.zstream.android.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.ConnectivityObserver
import com.zstream.android.data.ImdbTrailer
import com.zstream.android.data.ImdbTrailerRepository
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.local.dao.CachedEpisodeDao
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.CachedEpisodeEntity
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import com.zstream.android.data.model.airedEpisodes
import com.zstream.android.data.model.CollectionSummary
import com.zstream.android.data.remote.CollectionDetails
import com.zstream.android.download.DownloadResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailState {
    object Loading : DetailState()
    data class Movie(val detail: MovieDetail) : DetailState()
    data class Tv(val detail: TvDetail, val selectedSeason: Season? = null) : DetailState()
    data class Error(val message: String) : DetailState()
}

sealed interface CollectionState {
    data object Closed : CollectionState
    data class Loading(val collection: CollectionSummary) : CollectionState
    data class Loaded(val collection: CollectionDetails) : CollectionState
    data class Error(val collection: CollectionSummary, val message: String) : CollectionState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: TmdbRepository,
    private val progressRepo: com.zstream.android.data.ProgressRepository,
    private val bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    private val imdbTrailerRepo: ImdbTrailerRepository,
    private val cachedEpisodeDao: CachedEpisodeDao,
    private val downloadDao: DownloadDao,
    private val connectivityObserver: ConnectivityObserver,
    private val downloadResolver: DownloadResolver,
    private val certRepo: com.zstream.android.data.CertificationRepository,
    private val settingsPrefs: com.zstream.android.data.local.preferences.SettingsPreferences,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id = savedState.get<Int>("id") ?: 0
    private val mediaType = savedState.get<String>("mediaType") ?: "movie"

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state = _state.asStateFlow()
    private val _collection = MutableStateFlow<CollectionState>(CollectionState.Closed)
    val collection = _collection.asStateFlow()
    private val _trailers = MutableStateFlow<List<ImdbTrailer>>(emptyList())
    val trailers = _trailers.asStateFlow()

    /** Completed downloads for this title, grouped by "season|episode" (movies use "null|null"). */
    val downloadedEpisodes: kotlinx.coroutines.flow.StateFlow<Map<String, DownloadEntity>> =
        downloadDao.observeCompleted()
            .map { downloads ->
                downloads.filter { it.tmdbId == id.toString() }
                    .associateBy { "${it.season}|${it.episode}" }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isOffline: kotlinx.coroutines.flow.StateFlow<Boolean> =
        connectivityObserver.isOnline
            .map { online -> !online }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), !connectivityObserver.isOnlineNow())

    /** "season|episode" keys (movies "null|null") currently being resolved by [downloadResolver] — lets the UI show a spinner between tap and enqueue, since the resolve step itself has no other progress signal. */
    private val _pendingDownloads = MutableStateFlow<Set<String>>(emptySet())
    val pendingDownloads: kotlinx.coroutines.flow.StateFlow<Set<String>> = _pendingDownloads.asStateFlow()

    // Add flows for bookmark and progress
    val isBookmarked = bookmarkRepo.observeBookmark(id.toString())
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val bookmark: kotlinx.coroutines.flow.StateFlow<com.zstream.android.data.local.entity.BookmarkEntity?> = bookmarkRepo.observeBookmark(id.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val allGroups = bookmarkRepo.observeAllBookmarks()
        .map { bookmarks -> bookmarks.flatMap { it.groups.orEmpty() }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progress = progressRepo.observeProgress(id.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allProgress = progressRepo.observeAllProgressForTmdb(id.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingInitialSeasonNumber: Int? = null
    private var initialSeasonResolved = false

    init {
        if (mediaType == "tv") {
            viewModelScope.launch {
                progress.collectLatest { currentProgress ->
                    val seasonNumber = currentProgress?.seasonNumber ?: return@collectLatest
                    pendingInitialSeasonNumber = seasonNumber
                    applyPendingInitialSeason()
                }
            }
        }
        load()
    }

    fun toggleBookmark() {
        val current = _state.value
        val title = when (current) {
            is DetailState.Movie -> current.detail.title
            is DetailState.Tv -> current.detail.name
            else -> return
        }
        val poster = when (current) {
            is DetailState.Movie -> current.detail.posterPath
            is DetailState.Tv -> current.detail.posterPath
            else -> return
        }
        val year = when (current) {
            is DetailState.Movie -> current.detail.releaseDate?.take(4)?.toIntOrNull()
            is DetailState.Tv -> current.detail.firstAirDate?.take(4)?.toIntOrNull()
            else -> return
        }
        
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkRepo.removeBookmark(id.toString())
            } else {
                bookmarkRepo.addBookmark(id.toString(), title, mediaType, year, poster)
            }
        }
    }

    fun updateBookmarkGroups(groups: List<String>) {
        val current = _state.value
        val title = when (current) {
            is DetailState.Movie -> current.detail.title
            is DetailState.Tv -> current.detail.name
            else -> return
        }
        val poster = when (current) {
            is DetailState.Movie -> current.detail.posterPath
            is DetailState.Tv -> current.detail.posterPath
            else -> return
        }
        val year = when (current) {
            is DetailState.Movie -> current.detail.releaseDate?.take(4)?.toIntOrNull()
            is DetailState.Tv -> current.detail.firstAirDate?.take(4)?.toIntOrNull()
            else -> return
        }
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkRepo.setBookmarkGroups(id.toString(), groups)
            } else {
                bookmarkRepo.addBookmark(id.toString(), title, mediaType, year, poster, groups)
            }
        }
    }

    fun bookmarkCollection(collection: CollectionSummary) {
        viewModelScope.launch {
            val full = runCatching { repo.collection(collection.id) }.getOrElse { return@launch }
            val groupName = "[${groupIconOptions.random().first.lowercase()}]${full.name}"
            full.parts.forEach { part ->
                val year = part.releaseDate?.take(4)?.toIntOrNull() ?: return@forEach
                bookmarkRepo.addBookmark(
                    tmdbId = part.id.toString(),
                    title = part.title,
                    type = "movie",
                    year = year,
                    posterPath = part.posterPath,
                    groups = listOf(groupName),
                )
            }
        }
    }

    fun loadCollection(collection: CollectionSummary) {
        _collection.value = CollectionState.Loading(collection)
        viewModelScope.launch {
            runCatching { repo.collection(collection.id) }
                .onSuccess { _collection.value = CollectionState.Loaded(it) }
                .onFailure { _collection.value = CollectionState.Error(collection, it.message ?: "Failed to load collection") }
        }
    }

    fun clearCollection() {
        _collection.value = CollectionState.Closed
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailState.Loading
            _trailers.value = emptyList()
            val offline = !connectivityObserver.isOnlineNow()
            val kidsModeEnabled = runCatching { settingsPrefs.settings.first().kidsModeEnabled }.getOrDefault(false)
            runCatching {
                if (mediaType == "movie") {
                    val detail = if (offline) {
                        buildOfflineMovieDetail()
                    } else {
                        runCatching { repo.movieDetail(id) }.getOrNull() ?: buildOfflineMovieDetail()
                    }
                    requireNotNull(detail) { "No connection and nothing cached for this title" }
                    val filteredDetail = detail.similar?.results?.let { similarItems ->
                        detail.copy(similar = detail.similar.copy(results = certRepo.filterForKids(similarItems, kidsModeEnabled)))
                    } ?: detail
                    _state.value = DetailState.Movie(filteredDetail)
                    if (!offline) loadImdbTrailers(detail.imdbId)
                } else {
                    val detail = if (offline) {
                        buildOfflineTvDetail()
                    } else {
                        runCatching { repo.tvDetail(id) }.getOrNull() ?: buildOfflineTvDetail()
                    }
                    requireNotNull(detail) { "No connection and nothing cached for this title" }
                    val filteredDetail = detail.similar?.results?.let { similarItems ->
                        detail.copy(similar = detail.similar.copy(results = certRepo.filterForKids(similarItems, kidsModeEnabled)))
                    } ?: detail
                    val preferredSeasonNumber = pendingInitialSeasonNumber ?: progress.value?.seasonNumber
                    val firstSeason = preferredSeasonNumber?.let { seasonNumber ->
                        filteredDetail.seasons?.firstOrNull { it.seasonNumber == seasonNumber }?.let { fetchOrCachedSeason(seasonNumber) }
                    } ?: filteredDetail.seasons
                        ?.firstOrNull { it.seasonNumber > 0 }
                        ?.let { fetchOrCachedSeason(it.seasonNumber) }
                    _state.value = DetailState.Tv(filteredDetail, firstSeason)
                    applyPendingInitialSeason()
                    if (!offline) loadImdbTrailers(detail.imdbId ?: detail.externalIds?.imdbId)
                }
            }.onFailure { _state.value = DetailState.Error(it.message ?: "Failed to load") }
        }
    }

    /** Builds a minimal MovieDetail from whatever's cached locally (download/bookmark/progress). */
    private suspend fun buildOfflineMovieDetail(): MovieDetail? {
        val fromDownload = downloadDao.getAllSync()
            .firstOrNull { it.tmdbId == id.toString() && it.status == com.zstream.android.data.local.entity.DownloadStatus.DONE }
        val title = fromDownload?.title ?: bookmark.value?.title ?: progress.value?.title ?: return null
        val poster = fromDownload?.posterPath ?: bookmark.value?.posterPath ?: progress.value?.posterPath
        return MovieDetail(
            id = id, title = title, overview = null, posterPath = poster, backdropPath = null,
            releaseDate = null, voteAverage = null, runtime = null, genres = null, credits = null,
        )
    }

    /** Builds a minimal TvDetail (with cached season numbers) from local data only. */
    private suspend fun buildOfflineTvDetail(): TvDetail? {
        val fromDownload = downloadDao.getAllSync()
            .firstOrNull { it.tmdbId == id.toString() && it.status == com.zstream.android.data.local.entity.DownloadStatus.DONE }
        val title = fromDownload?.title ?: bookmark.value?.title ?: progress.value?.title ?: return null
        val poster = fromDownload?.posterPath ?: bookmark.value?.posterPath ?: progress.value?.posterPath
        val seasons = cachedEpisodeDao.getAvailableSeasonsSync(id.toString()).map { seasonNumber ->
            Season(id = 0, seasonNumber = seasonNumber, name = "Season $seasonNumber", episodeCount = null, posterPath = null, episodes = null)
        }
        return TvDetail(
            id = id, name = title, overview = null, posterPath = poster, backdropPath = null,
            firstAirDate = null, voteAverage = null, numberOfSeasons = seasons.size, seasons = seasons,
            genres = null, credits = null,
        )
    }

    /** Fetches a season live when online (caching the result), otherwise falls back to the local cache. */
    private suspend fun fetchOrCachedSeason(seasonNumber: Int): Season? {
        if (connectivityObserver.isOnlineNow()) {
            val season = runCatching { repo.season(id, seasonNumber) }.getOrNull()
            if (season != null) {
                cacheSeason(season)
                return season
            }
        }
        val cached = cachedEpisodeDao.getSeasonSync(id.toString(), seasonNumber)
        if (cached.isEmpty()) return null
        return Season(
            id = 0,
            seasonNumber = seasonNumber,
            name = "Season $seasonNumber",
            episodeCount = cached.size,
            posterPath = null,
            episodes = cached.map { entry ->
                Episode(
                    id = entry.episodeId,
                    name = entry.title,
                    episodeNumber = entry.episode,
                    seasonNumber = entry.season,
                    stillPath = entry.stillPath,
                    overview = entry.overview,
                    airDate = entry.airDate,
                )
            },
        )
    }

    private suspend fun cacheSeason(season: Season) {
        val episodes = season.episodes.orEmpty().map { ep ->
            CachedEpisodeEntity(
                tmdbId = id.toString(),
                season = season.seasonNumber,
                episode = ep.episodeNumber,
                episodeId = ep.id,
                title = ep.name ?: "Episode ${ep.episodeNumber}",
                overview = ep.overview,
                stillPath = ep.stillPath,
                airDate = ep.airDate,
            )
        }
        if (episodes.isNotEmpty()) cachedEpisodeDao.upsertAll(episodes)
    }

    private fun loadImdbTrailers(imdbId: String?) {
        if (imdbId.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { imdbTrailerRepo.getTrailers(imdbId) }
                .onSuccess {
                    android.util.Log.d("ImdbTrailers", "imdbId=$imdbId trailers=${it.size}")
                    _trailers.value = it
                }
                .onFailure {
                    android.util.Log.e("ImdbTrailers", "imdbId=$imdbId failed", it)
                }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val current = _state.value as? DetailState.Tv ?: return
        viewModelScope.launch {
            fetchOrCachedSeason(seasonNumber)?.let { _state.value = current.copy(selectedSeason = it) }
        }
    }

    fun markMovieWatched() {
        val current = _state.value as? DetailState.Movie ?: return
        viewModelScope.launch {
            val durationSeconds = current.detail.runtime?.times(60)
                ?: progress.value?.duration
                ?: 1
            progressRepo.updateProgress(
                tmdbId = id.toString(),
                title = current.detail.title,
                type = "movie",
                watched = durationSeconds,
                duration = durationSeconds,
                year = current.detail.releaseDate?.take(4)?.toIntOrNull(),
                posterPath = current.detail.posterPath,
            )
        }
    }

    fun clearMovieWatchHistory() {
        viewModelScope.launch {
            progressRepo.removeProgressItem(tmdbId = id.toString())
        }
    }

    fun markEpisodeWatched(episode: com.zstream.android.data.model.Episode) {
        val current = _state.value as? DetailState.Tv ?: return
        val season = current.selectedSeason
        viewModelScope.launch {
            val existing = allProgress.value.firstOrNull {
                it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
            }
            val durationSeconds = existing?.duration ?: 1
            progressRepo.updateProgress(
                tmdbId = id.toString(),
                title = current.detail.name,
                type = "show",
                watched = durationSeconds,
                duration = durationSeconds,
                year = current.detail.firstAirDate?.take(4)?.toIntOrNull(),
                posterPath = current.detail.posterPath,
                episodeNumber = episode.episodeNumber,
                seasonNumber = episode.seasonNumber,
                episodeId = existing?.episodeId ?: episode.id.toString(),
                seasonId = existing?.seasonId ?: season?.id?.toString(),
            )
        }
    }

    fun clearEpisodeWatchHistory(episode: com.zstream.android.data.model.Episode) {
        val current = _state.value as? DetailState.Tv ?: return
        val season = current.selectedSeason
        viewModelScope.launch {
            val existing = allProgress.value.firstOrNull {
                it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
            }
            progressRepo.removeProgressItem(
                tmdbId = id.toString(),
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                seasonId = existing?.seasonId ?: season?.id?.toString(),
                episodeId = existing?.episodeId ?: episode.id.toString(),
            )
        }
    }

    fun markSeasonWatched() {
        val current = _state.value as? DetailState.Tv ?: return
        val season = current.selectedSeason ?: return
        viewModelScope.launch {
            val year = current.detail.firstAirDate?.take(4)?.toIntOrNull()
            val posterPath = current.detail.posterPath
            val existingByEpisode = allProgress.value
                .filter { it.seasonNumber == season.seasonNumber }
                .associateBy { it.episodeNumber }

            season.episodes.orEmpty().airedEpisodes().forEach { episode ->
                val existing = existingByEpisode[episode.episodeNumber]
                val durationSeconds = existing?.duration ?: 1
                progressRepo.updateProgress(
                    tmdbId = id.toString(),
                    title = current.detail.name,
                    type = "show",
                    watched = durationSeconds,
                    duration = durationSeconds,
                    year = year,
                    posterPath = posterPath,
                    episodeNumber = episode.episodeNumber,
                    seasonNumber = episode.seasonNumber,
                    episodeId = existing?.episodeId ?: episode.id.toString(),
                    seasonId = existing?.seasonId ?: season.id.toString(),
                )
            }
        }
    }

    fun clearSeasonWatchHistory() {
        val current = _state.value as? DetailState.Tv ?: return
        val season = current.selectedSeason ?: return
        viewModelScope.launch {
            val existingByEpisode = allProgress.value
                .filter { it.seasonNumber == season.seasonNumber }
                .associateBy { it.episodeNumber }

            season.episodes.orEmpty().airedEpisodes().forEach { episode ->
                val existing = existingByEpisode[episode.episodeNumber]
                progressRepo.removeProgressItem(
                    tmdbId = id.toString(),
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    seasonId = existing?.seasonId ?: season.id.toString(),
                    episodeId = existing?.episodeId ?: episode.id.toString(),
                )
            }
        }
    }

    private fun applyPendingInitialSeason() {
        if (initialSeasonResolved) return
        val seasonNumber = pendingInitialSeasonNumber ?: return
        val current = _state.value as? DetailState.Tv ?: return
        if (current.selectedSeason?.seasonNumber == seasonNumber) {
            initialSeasonResolved = true
            return
        }
        if (current.detail.seasons.orEmpty().none { it.seasonNumber == seasonNumber }) {
            initialSeasonResolved = true
            return
        }

        initialSeasonResolved = true
        viewModelScope.launch {
            fetchOrCachedSeason(seasonNumber)?.let { _state.value = current.copy(selectedSeason = it) }
        }
    }

    fun downloadMovie() {
        val current = _state.value as? DetailState.Movie ?: return
        val key = "null|null"
        if (key in _pendingDownloads.value) return
        viewModelScope.launch {
            val destination = com.zstream.android.download.DownloadDestinationBroker.chooseTreeUri() ?: return@launch
            _pendingDownloads.value = _pendingDownloads.value + key
            try {
                downloadResolver.resolveAndEnqueue(
                    mediaType = "movie",
                    tmdbId = id.toString(),
                    title = current.detail.title,
                    year = current.detail.releaseDate?.take(4)?.toIntOrNull(),
                    posterPath = current.detail.posterPath,
                    destinationTreeUri = destination.treeUri,
                )
            } finally {
                _pendingDownloads.value = _pendingDownloads.value - key
            }
        }
    }

    fun downloadEpisode(episode: Episode) {
        val current = _state.value as? DetailState.Tv ?: return
        val key = "${episode.seasonNumber}|${episode.episodeNumber}"
        if (key in _pendingDownloads.value) return
        viewModelScope.launch {
            val destination = com.zstream.android.download.DownloadDestinationBroker.chooseTreeUri() ?: return@launch
            _pendingDownloads.value = _pendingDownloads.value + key
            try {
                downloadResolver.resolveAndEnqueue(
                    mediaType = "tv",
                    tmdbId = id.toString(),
                    title = current.detail.name,
                    year = current.detail.firstAirDate?.take(4)?.toIntOrNull(),
                    posterPath = current.detail.posterPath,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    episodeTitle = episode.name,
                    destinationTreeUri = destination.treeUri,
                )
            } finally {
                _pendingDownloads.value = _pendingDownloads.value - key
            }
        }
    }

    fun downloadSeason() {
        val current = _state.value as? DetailState.Tv ?: return
        val season = current.selectedSeason ?: return
        val alreadyDownloaded = downloadedEpisodes.value.keys
        val targets = season.episodes.orEmpty()
            .filterNot { alreadyDownloaded.contains("${it.seasonNumber}|${it.episodeNumber}") }
        viewModelScope.launch {
            // Asked once for the whole season, not once per episode.
            val destination = com.zstream.android.download.DownloadDestinationBroker.chooseTreeUri() ?: return@launch
            targets.forEach { episode ->
                val key = "${episode.seasonNumber}|${episode.episodeNumber}"
                if (key in _pendingDownloads.value) return@forEach
                _pendingDownloads.value = _pendingDownloads.value + key
                try {
                    downloadResolver.resolveAndEnqueue(
                        mediaType = "tv",
                        tmdbId = id.toString(),
                        title = current.detail.name,
                        year = current.detail.firstAirDate?.take(4)?.toIntOrNull(),
                        posterPath = current.detail.posterPath,
                        season = episode.seasonNumber,
                        episode = episode.episodeNumber,
                        episodeTitle = episode.name,
                        destinationTreeUri = destination.treeUri,
                    )
                } finally {
                    _pendingDownloads.value = _pendingDownloads.value - key
                }
            }
        }
    }
}
