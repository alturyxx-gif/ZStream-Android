package com.zstream.android.ui.screens

import android.content.Context
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
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import com.zstream.android.R
import com.zstream.android.data.model.TvSeasonCatalog
import com.zstream.android.data.model.airedEpisodes
import com.zstream.android.data.model.CollectionSummary
import com.zstream.android.data.model.EpisodeGroupResolver
import com.zstream.android.data.remote.CollectionDetails
import com.zstream.android.download.DownloadResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    data class Movie(val detail: MovieDetail, val certification: String? = null) : DetailState()
    data class Tv(val detail: TvDetail, val selectedSeason: Season? = null, val certification: String? = null, val seasonCatalog: TvSeasonCatalog? = null) : DetailState()
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
    @ApplicationContext private val context: Context,
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
    private val requestedSeasonNumber = savedState.get<Int>("season")?.takeIf { it >= 0 }
    val requestedEpisodeNumber = savedState.get<Int>("episode")?.takeIf { it >= 0 }

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

    /** (queued so far, total) while [downloadSeason] is running, so the season button can show real progress instead of a static label. Null when no season batch is in flight. */
    private val _seasonDownloadProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val seasonDownloadProgress: kotlinx.coroutines.flow.StateFlow<Pair<Int, Int>?> = _seasonDownloadProgress.asStateFlow()

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

    private var pendingInitialSeasonNumber: Int? = requestedSeasonNumber
    private var pendingInitialEpisodeNumber: Int? = requestedEpisodeNumber
    private var initialSeasonResolved = false
    private var seasonCatalog: TvSeasonCatalog? = null

    init {
        if (mediaType == "tv") {
            viewModelScope.launch {
                progress.collectLatest { currentProgress ->
                    if (requestedSeasonNumber != null) return@collectLatest
                    val seasonNumber = currentProgress?.seasonNumber ?: return@collectLatest
                    pendingInitialSeasonNumber = seasonNumber
                    pendingInitialEpisodeNumber = currentProgress.episodeNumber
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
                .onFailure {
                    _collection.value = CollectionState.Error(
                        collection,
                        it.message ?: context.getString(R.string.detail_load_collection_failed),
                    )
                }
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
                    requireNotNull(detail) { context.getString(R.string.detail_no_connection_cache) }
                    val filteredDetail = detail.similar?.results?.let { similarItems ->
                        detail.copy(similar = detail.similar.copy(results = certRepo.filterForKids(similarItems, kidsModeEnabled)))
                    } ?: detail
                    val certification = certRepo.getCertification(detail.id, "movie")
                    _state.value = DetailState.Movie(filteredDetail, certification)
                    if (!offline) loadImdbTrailers(detail.imdbId)
                } else {
                    val detail = if (offline) {
                        buildOfflineTvDetail()
                    } else {
                        runCatching { repo.tvDetail(id) }.getOrNull() ?: buildOfflineTvDetail()
                    }
                    requireNotNull(detail) { context.getString(R.string.detail_no_connection_cache) }
                    val filteredDetail = detail.similar?.results?.let { similarItems ->
                        detail.copy(similar = detail.similar.copy(results = certRepo.filterForKids(similarItems, kidsModeEnabled)))
                    } ?: detail

                    val catalog = if (offline) {
                        buildOfflineSeasonCatalog(filteredDetail)
                    } else {
                        runCatching { repo.seasonCatalog(id, filteredDetail) }
                            .getOrElse { com.zstream.android.data.model.EpisodeGroupResolver.defaultCatalogFromDetail(filteredDetail) }
                    }
                    seasonCatalog = catalog

                    val detailForUi = if (catalog.usingEpisodeGroups) {
                        filteredDetail.copy(
                            seasons = catalog.seasons.map { it.copy(episodes = null) },
                            numberOfSeasons = catalog.seasons.size,
                        )
                    } else {
                        filteredDetail
                    }

                    val preferredSeasonNumber = resolveInitialDisplaySeason(
                        catalog = catalog,
                        preferredSeason = pendingInitialSeasonNumber ?: progress.value?.seasonNumber,
                        preferredEpisode = pendingInitialEpisodeNumber ?: progress.value?.episodeNumber,
                        preferredEpisodeId = progress.value?.episodeId,
                    )
                    val firstSeason = preferredSeasonNumber?.let { fetchOrCachedSeason(it) }
                        ?: catalog.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber?.let { fetchOrCachedSeason(it) }
                        ?: detailForUi.seasons
                            ?.firstOrNull { it.seasonNumber > 0 }
                            ?.let { fetchOrCachedSeason(it.seasonNumber) }

                    val certification = certRepo.getCertification(detail.id, "tv")
                    _state.value = DetailState.Tv(detailForUi, firstSeason, certification, catalog)
                    applyPendingInitialSeason()
                    if (!offline) loadImdbTrailers(detail.imdbId ?: detail.externalIds?.imdbId)
                }
            }.onFailure {
                _state.value = DetailState.Error(it.message ?: context.getString(R.string.detail_load_failed))
            }
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
            Season(
                id = 0,
                seasonNumber = seasonNumber,
                name = context.getString(R.string.detail_season_number, seasonNumber),
                episodeCount = null,
                posterPath = null,
                episodes = null,
            )
        }
        return TvDetail(
            id = id, name = title, overview = null, posterPath = poster, backdropPath = null,
            firstAirDate = null, voteAverage = null, numberOfSeasons = seasons.size, seasons = seasons,
            genres = null, credits = null,
        )
    }

    /**
     * Fetches a season live when online (caching the result), otherwise falls back to the local cache.
     * When an episode-group catalog is active, seasons come from that layout (display S/E).
     */
    private suspend fun fetchOrCachedSeason(seasonNumber: Int): Season? {
        val catalog = seasonCatalog
        if (catalog != null && catalog.usingEpisodeGroups) {
            val fromCatalog = catalog.season(seasonNumber)
            if (fromCatalog != null) {
                cacheSeason(fromCatalog)
                return fromCatalog
            }
        }
        if (connectivityObserver.isOnlineNow()) {
            val season = if (catalog != null) {
                runCatching { repo.seasonForPlayback(id, seasonNumber) }.getOrNull()
            } else {
                runCatching { repo.season(id, seasonNumber) }.getOrNull()
            }
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
            name = context.getString(R.string.detail_season_number, seasonNumber),
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
                title = ep.name ?: context.getString(R.string.detail_episode_number, ep.episodeNumber),
                overview = ep.overview,
                stillPath = ep.stillPath,
                airDate = ep.airDate,
            )
        }
        if (episodes.isNotEmpty()) cachedEpisodeDao.upsertAll(episodes)
    }

    /** Rebuild season list from Room cache written the last time we were online (display S/E). */
    private suspend fun buildOfflineSeasonCatalog(detail: TvDetail): TvSeasonCatalog {
        val seasonNumbers = cachedEpisodeDao.getAvailableSeasonsSync(id.toString())
            .filter { it > 0 }
            .sorted()
        if (seasonNumbers.isEmpty()) {
            return com.zstream.android.data.model.EpisodeGroupResolver.defaultCatalogFromDetail(detail)
        }
        val seasons = seasonNumbers.mapNotNull { number ->
            val cached = cachedEpisodeDao.getSeasonSync(id.toString(), number)
            if (cached.isEmpty()) return@mapNotNull null
            Season(
                id = 0,
                seasonNumber = number,
                name = "Season $number",
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
        // Multiple cached seasons usually means we previously applied a group split (or normal multi-season).
        return TvSeasonCatalog(
            seasons = seasons,
            usingEpisodeGroups = seasons.size > 1 &&
                (detail.seasons.orEmpty().filter { it.seasonNumber > 0 }.size <= 1 ||
                    detail.seasons.orEmpty().any { (it.episodeCount ?: 0) >= EpisodeGroupResolver.LARGE_SEASON_EPISODE_THRESHOLD }),
        )
    }

    /**
     * Finds the season that was viewed last, with backwards compatibility
     */
    private fun resolveInitialDisplaySeason(
        catalog: TvSeasonCatalog,
        preferredSeason: Int?,
        preferredEpisode: Int?,
        preferredEpisodeId: String?,
    ): Int? {
        val match = catalog.findEpisode(preferredSeason, preferredEpisode, preferredEpisodeId)
        if (match != null) return match.seasonNumber
        if (preferredSeason != null && catalog.seasons.any { it.seasonNumber == preferredSeason }) {
            return preferredSeason
        }
        return catalog.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
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
            fetchOrCachedSeason(seasonNumber)?.let {
                _state.value = current.copy(selectedSeason = it, seasonCatalog = seasonCatalog ?: current.seasonCatalog)
            }
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

    fun markEpisodeWatched(episode: Episode) {
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

    fun clearEpisodeWatchHistory(episode: Episode) {
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
        val preferredSeason = pendingInitialSeasonNumber ?: return
        val current = _state.value as? DetailState.Tv ?: return
        val catalog = seasonCatalog ?: current.seasonCatalog
        val displaySeason = resolveInitialDisplaySeason(
            catalog = catalog ?: TvSeasonCatalog(
                seasons = current.detail.seasons.orEmpty(),
                usingEpisodeGroups = false,
            ),
            preferredSeason = preferredSeason,
            preferredEpisode = pendingInitialEpisodeNumber,
            preferredEpisodeId = progress.value?.episodeId,
        ) ?: preferredSeason

        if (current.selectedSeason?.seasonNumber == displaySeason) {
            initialSeasonResolved = true
            return
        }
        if (current.detail.seasons.orEmpty().none { it.seasonNumber == displaySeason }) {
            initialSeasonResolved = true
            return
        }

        initialSeasonResolved = true
        viewModelScope.launch {
            fetchOrCachedSeason(displaySeason)?.let {
                _state.value = current.copy(selectedSeason = it, seasonCatalog = catalog ?: current.seasonCatalog)
            }
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
            .airedEpisodes()
            .filterNot { alreadyDownloaded.contains("${it.seasonNumber}|${it.episodeNumber}") }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            // Asked once for the whole season, not once per episode.
            val destination = com.zstream.android.download.DownloadDestinationBroker.chooseTreeUri() ?: return@launch
            _seasonDownloadProgress.value = 0 to targets.size
            try {
                targets.forEachIndexed { index, episode ->
                    val key = "${episode.seasonNumber}|${episode.episodeNumber}"
                    if (key in _pendingDownloads.value) return@forEachIndexed
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
                        // Advance even on failure -- a stuck-at-3/12 counter reads as a hang, and
                        // one episode failing to resolve shouldn't block visibility into the rest.
                        _seasonDownloadProgress.value = (index + 1) to targets.size
                    }
                }
            } finally {
                _seasonDownloadProgress.value = null
            }
        }
    }
}
