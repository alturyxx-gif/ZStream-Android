package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.ConnectivityObserver
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.dao.toMediaOrNull
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.local.preferences.UserPreferences
import com.zstream.android.data.model.Media
import com.zstream.android.data.WatchPartyAction
import com.zstream.android.data.WatchPartyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

enum class HomeTab { MOVIES, TV, EDITOR }

enum class DiscoverSort { POPULARITY, RATING, RELEASE }
enum class SortOrder { ASC, DESC }

enum class MediaSectionSource {
    ContinueWatching,
    Bookmarks,
    BookmarkGroup,
    PopularMovies,
    NowPlayingMovies,
    TopRatedMovies,
    TrendingMovies,
    PopularTv,
    OnAirTv,
    TopRatedTv,
    TrendingTv,
    EditorMovies,
    EditorTv,
}

private const val GRID_SECTION_SIZE = 24
private const val DISCOVER_REPLACEMENT_RESERVE = 20

data class MediaSection(
    val title: String,
    val items: List<Media>,
    val source: MediaSectionSource? = null,
    val groupKey: String? = null,
    val totalItems: Int = items.size,
)

data class HomeState(
    val movieSections: List<MediaSection> = emptyList(),
    val tvSections: List<MediaSection> = emptyList(),
    val editorSections: List<MediaSection> = emptyList(),
    val continueWatching: List<MediaSection> = emptyList(),
    val bookmarks: List<MediaSection> = emptyList(),
    val downloaded: List<MediaSection> = emptyList(),
    val downloadedByTmdbId: Map<String, List<DownloadEntity>> = emptyMap(),
    val bookmarkEntities: Map<String, BookmarkEntity> = emptyMap(),
    val progressMap: Map<String, ProgressEntity> = emptyMap(),
    val enableDiscover: Boolean = false,
    val kidsModeEnabled: Boolean = false,
    val enableFeatured: Boolean = true,
    val enableImageLogos: Boolean = true,
    val enableLowPerformanceMode: Boolean = false,
    val featuredMedia: List<Media> = emptyList(),
    val activeTab: HomeTab = HomeTab.MOVIES,
    val searchQuery: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false,
    val offlineBannerDismissed: Boolean = false,
    val continueWatchingLoading: Boolean = true,
    val bookmarksLoading: Boolean = true,
    val enableCarouselView: Boolean = true,
    val homeSectionCarouselLimit: Int = 20,
    val gridRows: Int = 2,
    val canLoadMore: Boolean = false,
    val initialFocusRequested: Boolean = false,
    val isDiscoverMode: Boolean = false,
    val selectedSearchGenreId: Int? = null,
    val selectedSearchTab: HomeTab = HomeTab.MOVIES,
    val selectedSearchSort: DiscoverSort = DiscoverSort.POPULARITY,
    val selectedSearchSortOrder: SortOrder = SortOrder.DESC,
) {
    val userSections: List<MediaSection> get() {
        val userContent = mutableListOf<MediaSection>()
        if (isOffline) {
            fun List<MediaSection>.keepOnlyDownloaded() = mapNotNull { section ->
                val filtered = section.items.filter { downloadedByTmdbId.containsKey(it.id.toString()) }
                if (filtered.isEmpty()) null else section.copy(items = filtered, totalItems = filtered.size)
            }
            userContent.addAll(continueWatching.keepOnlyDownloaded())
            userContent.addAll(bookmarks.keepOnlyDownloaded())
        } else {
            if (continueWatching.isNotEmpty()) userContent.addAll(continueWatching)
            if (bookmarks.isNotEmpty()) userContent.addAll(bookmarks)
        }
        if (downloaded.isNotEmpty()) userContent.addAll(downloaded)
        return userContent
    }

    val baseSections: List<MediaSection> get() {
        val base = when (activeTab) {
            HomeTab.MOVIES -> movieSections
            HomeTab.TV -> tvSections
            HomeTab.EDITOR -> editorSections
        }
        val seenIds = userSections.flatMap { it.items }.map { it.id }.toMutableSet()
        val sectionSize = if (enableCarouselView) homeSectionCarouselLimit else GRID_SECTION_SIZE
        return base.map { section ->
            val uniqueItems = section.items.filter { seenIds.add(it.id) }
            section.copy(items = uniqueItems.take(sectionSize))
        }.filter { it.items.isNotEmpty() }
    }
}

private fun MediaSection.limitForHomeCarousel(limit: Int): MediaSection {
    if (items.size <= limit) return copy(totalItems = items.size)
    return copy(items = items.take(limit), totalItems = items.size)
}

private fun normalizeGroupName(group: String): String {
    val match = Regex("^\\[[^\\]]+]\\s*").find(group)
    return if (match != null) group.removePrefix(match.value).trim() else group.trim()
}

private fun groupSortKey(group: String): String {
    val label = normalizeGroupName(group)
    return label.lowercase()
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class BookmarkStateInputs(
    val bookmarks: List<BookmarkEntity>,
    val settings: SettingsEntity,
    val isSyncing: Boolean,
    val traktSyncing: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TmdbRepository,
    private val progressRepo: com.zstream.android.data.ProgressRepository,
    private val bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    private val settingsPrefs: SettingsPreferences,
    val userPrefs: UserPreferences,
    private val watchPartyManager: WatchPartyManager,
    private val dataSyncManager: com.zstream.android.data.DataSyncManager,
    private val traktRepo: com.zstream.android.data.TraktRepository,
    private val downloadDao: DownloadDao,
    private val connectivityObserver: ConnectivityObserver,
    private val certRepo: com.zstream.android.data.CertificationRepository,
) : ViewModel() {
    private val discoverSourcePages = mutableMapOf<MediaSectionSource, Int>()
    private var replenishDiscoverJob: Job? = null
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    private var currentSearchPage = 1
    private var isSearchingMore = false
    private var searchGeneration = 0

    init {
        com.zstream.android.CrashLog.breadcrumb("HomeVM", "init start")
        load()
        observeUserContent()
        observeDownloaded()
        observeSettings()
        observeConnectivityRecovery()
        com.zstream.android.CrashLog.breadcrumb("HomeVM", "init done")
    }

    val watchPartyRoomCode = watchPartyManager.roomCode
    val watchPartyEnabled = watchPartyManager.enabled
    val watchPartyIsOffline = watchPartyManager.isOffline
    val watchPartyHostGraceDeadlineMs = watchPartyManager.hostGraceDeadlineMs

    fun joinWatchParty(code: String) {
        watchPartyManager.joinRoom(code)
    }

    fun leaveWatchParty() {
        watchPartyManager.leaveRoom()
    }

    fun removeBookmark(tmdbId: String) {
        viewModelScope.launch { bookmarkRepo.removeBookmark(tmdbId) }
    }

    fun removeProgress(tmdbId: String) {
        viewModelScope.launch { progressRepo.removeProgress(tmdbId) }
    }

    fun updateBookmarkGroups(tmdbId: String, groups: List<String>) {
        viewModelScope.launch { bookmarkRepo.setBookmarkGroups(tmdbId, groups) }
    }

    fun renameGroup(oldGroup: String, newGroup: String) {
        viewModelScope.launch { bookmarkRepo.renameGroup(oldGroup, newGroup) }
    }

    fun setGroupOrder(order: List<String>) {
        viewModelScope.launch { settingsPrefs.setGroupOrder(order) }
    }

    fun updateBookmark(
        tmdbId: String,
        title: String,
        type: String,
        year: Int?,
        posterPath: String?,
        groups: List<String>?,
    ) {
        viewModelScope.launch {
            bookmarkRepo.addBookmark(
                tmdbId = tmdbId,
                title = title,
                type = type,
                year = year,
                posterPath = posterPath,
                groups = groups,
            )
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsPrefs.settings.collect { s ->
                val kidsModeChanged = _state.value.kidsModeEnabled != s.kidsModeEnabled
                _state.update { it.copy(
                    enableDiscover = s.enableDiscover,
                    enableFeatured = s.enableFeatured,
                    enableImageLogos = s.enableImageLogos,
                    enableLowPerformanceMode = s.enableLowPerformanceMode,
                    enableCarouselView = s.enableCarouselView,
                    homeSectionCarouselLimit = s.homeSectionCarouselLimit,
                    gridRows = s.gridRows,
                    kidsModeEnabled = s.kidsModeEnabled,
                ) }
                if (kidsModeChanged) {
                    discoverSourcePages.clear()
                    load()
                } else {
                    scheduleDiscoverReplenish()
                }
            }
        }
    }

    private fun observeUserContent() {
        viewModelScope.launch {
            combine(
                progressRepo.observeAllProgress(),
                settingsPrefs.settings,
                progressRepo.isSyncing,
                traktRepo.state,
            ) { progress, settings, isSyncing, traktState ->
                Quadruple(progress, settings, isSyncing, traktState.syncing)
            }.collect { (progress, settings, isSyncing, traktSyncing) ->
                val continueWatching = progress.toContinueWatchingResult()
                val limitedSection = continueWatching.media
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        MediaSection(
                            "Continue Watching",
                            it,
                            MediaSectionSource.ContinueWatching,
                        ).let { section ->
                            if (settings.enableCarouselView) section.limitForHomeCarousel(settings.homeSectionCarouselLimit) else section
                        }
                    }
                _state.update { it.copy(
                    progressMap = continueWatching.progressMap,
                    continueWatching = limitedSection?.let(::listOf) ?: emptyList(),
                    continueWatchingLoading = continueWatching.media.isEmpty() && (isSyncing || traktSyncing),
                ) }
                scheduleDiscoverReplenish()
            }
        }
        
        viewModelScope.launch {
            combine(
                bookmarkRepo.observeAllBookmarks(),
                settingsPrefs.settings,
                bookmarkRepo.isSyncing,
                traktRepo.state,
            ) { bookmarks, settings, isSyncing, traktState ->
                BookmarkStateInputs(bookmarks, settings, isSyncing, traktState.syncing)
            }.collect { (bookmarkEntities, settings, isSyncing, traktSyncing) ->
                val bookmarks = bookmarkEntities
                val bookmarkMedia = bookmarks.associateBy { it.tmdbId }.mapValues { (_, b) ->
                    Media(
                        id = b.tmdbId.toIntOrNull() ?: 0,
                        title = if (b.type == "movie") b.title else null,
                        name = if (b.type == "show") b.title else null,
                        overview = null,
                        posterPath = b.posterPath,
                        backdropPath = null,
                        releaseDate = b.year?.toString(),
                        firstAirDate = b.year?.toString(),
                        voteAverage = null,
                        mediaType = if (b.type == "show") "tv" else b.type,
                        genreIds = null
                    )
                }

                val grouped = linkedMapOf<String, MutableList<Media>>()
                val regular = mutableListOf<Media>()
                bookmarkEntities.forEach { bookmark ->
                    val media = bookmarkMedia[bookmark.tmdbId] ?: return@forEach
                    val groups = bookmark.groups.orEmpty()
                    if (groups.isEmpty()) {
                        regular += media
                    } else {
                        groups.forEach { group ->
                            grouped.getOrPut(group) { mutableListOf() }.add(media)
                        }
                    }
                }

                val availableGroups = bookmarks.asSequence()
                    .flatMap { it.groups.orEmpty().asSequence() }
                    .distinct()
                    .toList()
                val orderedGroups = settings.groupOrder.filter { it in availableGroups } +
                    availableGroups.filterNot { it in settings.groupOrder }

                val sectionsByGroup = buildMap<String, MediaSection> {
                    orderedGroups.forEach { group ->
                        grouped[group]?.takeIf { it.isNotEmpty() }?.let {
                            val section = MediaSection(
                                title = group,
                                items = it.sortedBy { media -> media.displayTitle.lowercase() },
                            )
                            put(group, if (settings.enableCarouselView) section.limitForHomeCarousel(settings.homeSectionCarouselLimit) else section)
                        }
                    }
                    if (regular.isNotEmpty()) {
                        val section = MediaSection("My Bookmarks", regular, MediaSectionSource.Bookmarks)
                        put("bookmarks", if (settings.enableCarouselView) section.limitForHomeCarousel(settings.homeSectionCarouselLimit) else section)
                    }
                }
                val sectionOrder = settings.groupOrder.filter { it in sectionsByGroup } +
                    sectionsByGroup.keys.filterNot { it in settings.groupOrder }
                val sections = sectionOrder.mapNotNull { key ->
                    sectionsByGroup[key]?.let { section ->
                        if (key.startsWith("[")) section.copy(source = MediaSectionSource.BookmarkGroup, groupKey = key) else section
                    }
                }

                _state.update {
                    it.copy(
                        bookmarks = sections,
                        bookmarkEntities = bookmarkEntities.associateBy { entity -> entity.tmdbId },
                        bookmarksLoading = sections.isEmpty() && (isSyncing || traktSyncing),
                    )
                }
                scheduleDiscoverReplenish()
            }
        }
    }

    private fun observeDownloaded() {
        viewModelScope.launch {
            downloadDao.observeCompleted().collect { downloads ->
                val media = downloads.distinctBy { it.tmdbId }.mapNotNull { it.toMediaOrNull() }
                val section = media.takeIf { it.isNotEmpty() }
                    ?.let { MediaSection("Downloaded", it) }
                _state.update { it.copy(
                    downloaded = section?.let(::listOf) ?: emptyList(),
                    downloadedByTmdbId = downloads.groupBy { it.tmdbId },
                ) }
            }
        }
    }

    private fun observeConnectivityRecovery() {
        viewModelScope.launch {
            connectivityObserver.isOnline.drop(1).distinctUntilChanged().collect { online ->
                if (online && _state.value.isOffline) {
                    // Re-arm the offline banner for the next offline period.
                    _state.update { it.copy(offlineBannerDismissed = false) }
                    load()
                }
            }
        }
    }

    fun dismissOfflineBanner() {
        _state.update { it.copy(offlineBannerDismissed = true) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            if (!connectivityObserver.isOnlineNow()) {
                android.util.Log.d("HomeVM", "Offline — skipping TMDB fetch, showing downloaded content")
                _state.update { it.copy(loading = false, error = null, isOffline = true) }
                return@launch
            }
            android.util.Log.d("HomeVM", "Loading home sections from TMDB...")
            runCatching {
                supervisorScope {
                    val trendMovies = async { repo.trendingMovies() }
                    val popularMovies = async { repo.popularMovies() }
                    val nowPlaying = async { repo.nowPlayingMovies() }
                    val topMovies = async { repo.topRatedMovies() }
                    val trendTv = async { repo.trendingTv() }
                    val popularTv = async { repo.popularTv() }
                    val topTv = async { repo.topRatedTv() }
                    val onAir = async { repo.onAirTv() }

                    val movies = trendMovies.await()
                    val tvShows = trendTv.await()
                    android.util.Log.d("HomeVM", "Loaded ${movies.size} trending movies and ${tvShows.size} trending TV shows")

                    // Combine trending movies and TV shows for the carousel
                    val combinedFeatured = (movies.take(10) + tvShows.take(10))
                        .shuffled()
                        .take(10)

                    val featuredWithLogos = combinedFeatured.map { media ->
                        async {
                            try {
                                if (media.type == "movie") {
                                    val detail = repo.movieDetail(media.id)
                                    media.copy(logoPath = detail.images?.logos?.firstOrNull()?.file_path)
                                } else {
                                    val detail = repo.tvDetail(media.id)
                                    media.copy(logoPath = detail.images?.logos?.firstOrNull()?.file_path)
                                }
                            } catch (e: Exception) {
                                media
                            }
                        }
                    }.awaitAll()

                    _state.update { it.copy(
                        loading = false,
                        isOffline = false,
                        featuredMedia = featuredWithLogos,
                        movieSections = listOf(
                            MediaSection("Most Popular", popularMovies.await(), MediaSectionSource.PopularMovies),
                            MediaSection("In Cinemas", nowPlaying.await(), MediaSectionSource.NowPlayingMovies),
                            MediaSection("Top Rated", topMovies.await(), MediaSectionSource.TopRatedMovies),
                            MediaSection("Trending", movies, MediaSectionSource.TrendingMovies),
                        ),
                        tvSections = listOf(
                            MediaSection("Most Popular", popularTv.await(), MediaSectionSource.PopularTv),
                            MediaSection("On the Air", onAir.await(), MediaSectionSource.OnAirTv),
                            MediaSection("Top Rated", topTv.await(), MediaSectionSource.TopRatedTv),
                            MediaSection("Trending", trendTv.await(), MediaSectionSource.TrendingTv),
                        ),
                        editorSections = listOf(
                            MediaSection("Editor Picks — Movies", topMovies.await(), MediaSectionSource.EditorMovies),
                            MediaSection("Editor Picks — Shows", topTv.await(), MediaSectionSource.EditorTv),
                        ),
                    ) }
                    discoverSourcePages.clear()
                    scheduleDiscoverReplenish()
                }
            }.onFailure { e ->
                android.util.Log.e("HomeVM", "Failed to load home sections", e)
                if (e is java.io.IOException) {
                    // Connectivity-shaped failure (DNS/connect/timeout) — show the offline
                    // downloaded-library experience instead of a raw exception message.
                    _state.update { it.copy(loading = false, error = null, isOffline = true) }
                } else {
                    _state.update { it.copy(loading = false, error = e.message ?: "No connection") }
                }
            }
        }
    }

    private fun scheduleDiscoverReplenish() {
        val snapshot = _state.value
        if (snapshot.movieSections.isEmpty()) return
        replenishDiscoverJob?.cancel()
        replenishDiscoverJob = viewModelScope.launch {
            val userIds = _state.value.userSections.flatMapTo(mutableSetOf()) { section -> section.items.map { it.id } }
            val target = if (_state.value.enableCarouselView) _state.value.homeSectionCarouselLimit else GRID_SECTION_SIZE
            val (movies, tv, editor) = supervisorScope {
                val movies = async { replenishSections(_state.value.movieSections, userIds, target) }
                val tv = async { replenishSections(_state.value.tvSections, userIds, target) }
                val editor = async { replenishSections(_state.value.editorSections, userIds, target) }
                Triple(movies.await(), tv.await(), editor.await())
            }
            _state.update { it.copy(movieSections = movies, tvSections = tv, editorSections = editor) }
        }
    }

    private suspend fun replenishSections(
        sections: List<MediaSection>,
        userIds: Set<Int>,
        target: Int,
    ): List<MediaSection> = supervisorScope {
        val kidsMode = _state.value.kidsModeEnabled
        sections.map { section ->
            async {
                val candidates = certRepo.filterForKids(section.items.distinctBy { it.id }, kidsMode).toMutableList()
                while (candidates.count { it.id !in userIds } < target + DISCOVER_REPLACEMENT_RESERVE) {
                    val source = section.source ?: break
                    val page = (discoverSourcePages[source] ?: 1) + 1
                    val result = runCatching { loadDiscoverPage(source, page) }.getOrNull() ?: break
                    discoverSourcePages[source] = page
                    val additions = certRepo.filterForKids(
                        result.filterNot { item -> candidates.any { it.id == item.id } },
                        kidsMode,
                    )
                    if (additions.isEmpty()) break
                    candidates += additions
                }
                section.copy(items = candidates)
            }
        }.awaitAll()
    }

    private suspend fun loadDiscoverPage(source: MediaSectionSource, page: Int): List<Media> = when (source) {
        MediaSectionSource.PopularMovies -> repo.popularMoviesPage(page).items
        MediaSectionSource.NowPlayingMovies -> repo.nowPlayingMoviesPage(page).items
        MediaSectionSource.TopRatedMovies, MediaSectionSource.EditorMovies -> repo.topRatedMoviesPage(page).items
        MediaSectionSource.TrendingMovies -> repo.trendingMoviesPage(page).items
        MediaSectionSource.PopularTv -> repo.popularTvPage(page).items
        MediaSectionSource.OnAirTv -> repo.onAirTvPage(page).items
        MediaSectionSource.TopRatedTv, MediaSectionSource.EditorTv -> repo.topRatedTvPage(page).items
        MediaSectionSource.TrendingTv -> repo.trendingTvPage(page).items
        else -> emptyList()
    }

    /** Pull-to-refresh: reload TMDB data + sync backend + sync Trakt (fire-and-forget each). */
    fun refresh() {
        load()
        viewModelScope.launch { runCatching { dataSyncManager.syncAllFromRemote() } }
        viewModelScope.launch {
            runCatching { traktRepo.syncHistory() }
            runCatching { traktRepo.syncWatchlist() }
        }
    }

    fun setTab(tab: HomeTab) = _state.update { it.copy(activeTab = tab) }
    fun setSearchGenre(id: Int?) {
        _state.update { it.copy(selectedSearchGenreId = id) }
        viewModelScope.launch {
            if (_state.value.searchQuery.isBlank()) {
                onSearchChange("")
            } else {
                applySearchFiltering()
            }
        }
    }

    fun setSearchTab(tab: HomeTab) {
        _state.update { it.copy(selectedSearchTab = tab) }
        viewModelScope.launch {
            if (_state.value.searchQuery.isBlank()) {
                onSearchChange("")
            } else {
                applySearchFiltering()
            }
        }
    }
    fun setSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun markFocusRequested() = _state.update { it.copy(initialFocusRequested = true) }

    fun cycleSearchSort() {
        _state.update {
            val next = when (it.selectedSearchSort) {
                DiscoverSort.POPULARITY -> DiscoverSort.RATING
                DiscoverSort.RATING -> DiscoverSort.RELEASE
                DiscoverSort.RELEASE -> DiscoverSort.POPULARITY
            }
            it.copy(selectedSearchSort = next)
        }
        onSearchChange(_state.value.searchQuery)
    }

    fun toggleSearchSortOrder() {
        _state.update {
            val next = if (it.selectedSearchSortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
            it.copy(selectedSearchSortOrder = next)
        }
        onSearchChange(_state.value.searchQuery)
    }

    // Live search results from TMDB (separate from carousel sections)
    private val _rawSearchResults = MutableStateFlow<List<Media>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Media>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun mapMovieGenreToTv(movieGenreId: Int): Int {
        return when (movieGenreId) {
            28, 12 -> 10759 // Action, Adventure -> Action & Adventure
            14, 878 -> 10765 // Fantasy, Sci-Fi -> Sci-Fi & Fantasy
            10752 -> 10768 // War -> War & Politics
            else -> movieGenreId
        }
    }

    private fun getTmdbSortString(): String {
        val s = _state.value
        val base = when (s.selectedSearchSort) {
            DiscoverSort.POPULARITY -> "popularity"
            DiscoverSort.RATING -> "vote_average"
            DiscoverSort.RELEASE -> if (s.selectedSearchTab == HomeTab.MOVIES) "primary_release_date" else "first_air_date"
        }
        val order = if (s.selectedSearchSortOrder == SortOrder.ASC) "asc" else "desc"
        return "$base.$order"
    }

    private suspend fun applySearchFiltering() {
        val raw = _rawSearchResults.value
        val genreId = _state.value.selectedSearchGenreId
        val tab = _state.value.selectedSearchTab
        val kidsMode = _state.value.kidsModeEnabled
        val query = _state.value.searchQuery

        val filtered = if (query.isNotBlank()) {
            // Search Mode: Global mixed search (Movies + TV), ignore tabs/genres
            raw
        } else {
            // Discover Mode: Strict filtering by Tab and Genre
            val effectiveGenreId = if (tab == HomeTab.TV && genreId != null) mapMovieGenreToTv(genreId) else genreId
            if (effectiveGenreId == null) {
                raw
            } else {
                raw.filter { it.genreIds?.contains(effectiveGenreId) == true }
            }
        }
        _searchResults.value = certRepo.filterForKids(filtered, kidsMode)
    }

    fun onSearchChange(q: String) {
        val tab = _state.value.selectedSearchTab
        isSearchingMore = false
        setSearch(q)
        searchGeneration++
        currentSearchPage = 1
        searchJob?.cancel()
        _rawSearchResults.value = emptyList()
        _searchResults.value = emptyList()

        if (q.isNotBlank()) {
            // Visual reset of genres when searching
            _state.update { it.copy(selectedSearchGenreId = null, isDiscoverMode = false, canLoadMore = false) }
        } else {
            _state.update { it.copy(canLoadMore = false, isDiscoverMode = _state.value.selectedSearchGenreId != null) }
        }

        if (q.isBlank() && _state.value.selectedSearchGenreId == null) {
            return
        }

        val generation = searchGeneration
        val genreId = _state.value.selectedSearchGenreId

        searchJob = viewModelScope.launch {
            if (q.isNotBlank()) delay(300) // debounce search only
            runCatching {
                if (q.isBlank()) {
                    // Discover mode
                    val effectiveGenreId = if (tab == HomeTab.TV) mapMovieGenreToTv(genreId!!) else genreId
                    val sortBy = getTmdbSortString()
                    val result = if (tab == HomeTab.MOVIES) {
                        repo.discoverMoviesPage(effectiveGenreId, sortBy, currentSearchPage)
                    } else {
                        repo.discoverTvPage(effectiveGenreId, sortBy, currentSearchPage)
                    }
                    if (searchGeneration != generation) return@launch
                    _state.update { it.copy(canLoadMore = result.canLoadMore) }
                    _rawSearchResults.value = result.items
                    applySearchFiltering()
                } else {
                    // Search mode (Global Mixed)
                    val firstResults = repo.search(q, currentSearchPage) { total ->
                        if (searchGeneration == generation) {
                            _state.update { it.copy(canLoadMore = currentSearchPage < total) }
                        }
                    }
                    if (searchGeneration != generation) return@launch
                    _rawSearchResults.value = firstResults
                    applySearchFiltering()
                }
            }
        }
    }

    fun searchLoadMore() {
        val q = state.value.searchQuery
        val genreId = state.value.selectedSearchGenreId
        if ((q.isBlank() && genreId == null) || !state.value.canLoadMore || isSearchingMore) return

        val generation = searchGeneration
        val tab = state.value.selectedSearchTab

        viewModelScope.launch {
            isSearchingMore = true
            try {
                currentSearchPage++

                runCatching {
                    if (q.isBlank()) {
                        // Discover mode load more
                        val effectiveGenreId = if (tab == HomeTab.TV) mapMovieGenreToTv(genreId!!) else genreId
                        val sortBy = getTmdbSortString()
                        val result = if (tab == HomeTab.MOVIES) {
                            repo.discoverMoviesPage(effectiveGenreId, sortBy, currentSearchPage)
                        } else {
                            repo.discoverTvPage(effectiveGenreId, sortBy, currentSearchPage)
                        }
                        if (searchGeneration == generation) {
                            _state.update { it.copy(canLoadMore = result.canLoadMore) }
                            _rawSearchResults.value = _rawSearchResults.value + result.items
                            applySearchFiltering()
                        }
                    } else {
                        // Search mode load more
                        val nextResults = repo.search(q, currentSearchPage) { total ->
                            if (searchGeneration == generation) {
                                _state.update { it.copy(canLoadMore = currentSearchPage < total) }
                            }
                        }
                        if (searchGeneration == generation) {
                            _rawSearchResults.value = _rawSearchResults.value + nextResults
                            applySearchFiltering()
                        }
                    }
                }.onFailure {
                    if (searchGeneration == generation) {
                        currentSearchPage--
                    }
                }
            } finally {
                isSearchingMore = false
            }
        }
    }
}
