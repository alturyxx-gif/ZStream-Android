package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

enum class HomeTab { MOVIES, TV, EDITOR }

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

data class MediaSection(
    val title: String,
    val items: List<Media>,
    val source: MediaSectionSource? = null,
    val groupKey: String? = null,
)

data class HomeState(
    val movieSections: List<MediaSection> = emptyList(),
    val tvSections: List<MediaSection> = emptyList(),
    val editorSections: List<MediaSection> = emptyList(),
    val continueWatching: List<MediaSection> = emptyList(),
    val bookmarks: List<MediaSection> = emptyList(),
    val bookmarkEntities: Map<String, BookmarkEntity> = emptyMap(),
    val progressMap: Map<String, ProgressEntity> = emptyMap(),
    val enableDiscover: Boolean = true,
    val enableFeatured: Boolean = false,
    val enableImageLogos: Boolean = true,
    val enableLowPerformanceMode: Boolean = false,
    val featuredMedia: List<Media> = emptyList(),
    val activeTab: HomeTab = HomeTab.MOVIES,
    val selectedGenreId: Int? = null,
    val searchQuery: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val enableCarouselView: Boolean = true,
    val gridRows: Int = 2,
    val canLoadMore: Boolean = false,
    val initialFocusRequested: Boolean = false
) {
    val userSections: List<MediaSection> get() {
        val userContent = mutableListOf<MediaSection>()
        if (continueWatching.isNotEmpty()) userContent.addAll(continueWatching)
        if (bookmarks.isNotEmpty()) userContent.addAll(bookmarks)
        return userContent
    }

    val baseSections: List<MediaSection> get() {
        val base = when (activeTab) {
            HomeTab.MOVIES -> movieSections
            HomeTab.TV -> tvSections
            HomeTab.EDITOR -> editorSections
        }
        val seenIds = userSections.flatMap { it.items }.map { it.id }.toMutableSet()
        return base.map { section ->
            val uniqueItems = section.items.filter { seenIds.add(it.id) }
            section.copy(items = uniqueItems)
        }.filter { it.items.isNotEmpty() }
    }
}

private fun normalizeGroupName(group: String): String {
    val match = Regex("^\\[[^\\]]+]\\s*").find(group)
    return if (match != null) group.removePrefix(match.value).trim() else group.trim()
}

private fun groupSortKey(group: String): String {
    val label = normalizeGroupName(group)
    return label.lowercase()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TmdbRepository,
    private val progressRepo: com.zstream.android.data.ProgressRepository,
    private val bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    private val settingsPrefs: SettingsPreferences,
    val userPrefs: UserPreferences,
    private val watchPartyManager: WatchPartyManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    private var currentSearchPage = 1
    private var isSearchingMore = false
    private var searchGeneration = 0

    init { 
        load()
        observeUserContent()
        observeSettings()
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
                _state.update { it.copy(
                    enableDiscover = s.enableDiscover,
                    enableFeatured = s.enableFeatured,
                    enableImageLogos = s.enableImageLogos,
                    enableLowPerformanceMode = s.enableLowPerformanceMode,
                    enableCarouselView = s.enableCarouselView,
                    gridRows = s.gridRows,
                ) }
            }
        }
    }

    private fun observeUserContent() {
        viewModelScope.launch {
            progressRepo.observeAllProgress().collect { progress ->
                // Group by tmdbId, keep only the latest episode per show
                val latestPerShow = progress.groupBy { it.tmdbId }
                    .mapValues { (_, entries) ->
                        val nonCompleted = entries.filter { it.duration <= 0 || it.watched < it.duration * 0.95f }
                        val candidates = if (nonCompleted.isNotEmpty()) nonCompleted else entries
                        candidates.maxByOrNull {
                            (it.seasonNumber ?: 0) * 100000 + (it.episodeNumber ?: 0)
                        } ?: candidates.first()
                    }
                val mediaMap = mutableMapOf<String, ProgressEntity>()
                val watchingMedia = latestPerShow.map { (tmdbId, p) ->
                    mediaMap[tmdbId] = p
                    Media(
                        id = p.tmdbId.toIntOrNull() ?: 0,
                        title = if (p.type == "movie") p.title else null,
                        name = if (p.type == "show") p.title else null,
                        overview = null,
                        posterPath = p.posterPath,
                        backdropPath = null,
                        releaseDate = p.year?.toString(),
                        firstAirDate = p.year?.toString(),
                        voteAverage = null,
                        mediaType = if (p.type == "show") "tv" else p.type,
                        genreIds = null
                    )
                }
                _state.update { it.copy(
                    progressMap = mediaMap,
                    continueWatching = if (watchingMedia.isNotEmpty()) listOf(MediaSection("Continue Watching", watchingMedia, MediaSectionSource.ContinueWatching)) else emptyList(),
                ) }
            }
        }
        
        viewModelScope.launch {
            combine(bookmarkRepo.observeAllBookmarks(), settingsPrefs.settings) { bookmarks, settings ->
                bookmarks to settings.groupOrder
            }.collect { (bookmarkEntities, groupOrder) ->
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
                val orderedGroups = groupOrder.filter { it in availableGroups } +
                    availableGroups.filterNot { it in groupOrder }

                val sectionsByGroup = buildMap<String, MediaSection> {
                    orderedGroups.forEach { group ->
                        grouped[group]?.takeIf { it.isNotEmpty() }?.let {
                            put(group, MediaSection(group, it.sortedBy { media -> media.displayTitle.lowercase() }))
                        }
                    }
                    if (regular.isNotEmpty()) put("bookmarks", MediaSection("My Bookmarks", regular, MediaSectionSource.Bookmarks))
                }
                val sectionOrder = groupOrder.filter { it in sectionsByGroup } +
                    sectionsByGroup.keys.filterNot { it in groupOrder }
                val sections = sectionOrder.mapNotNull { key ->
                    sectionsByGroup[key]?.let { section ->
                        if (key.startsWith("[")) section.copy(source = MediaSectionSource.BookmarkGroup, groupKey = key) else section
                    }
                }

                _state.update { it.copy(bookmarks = sections, bookmarkEntities = bookmarkEntities.associateBy { it.tmdbId }) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
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
                            MediaSection("Editor Picks — Movies", topMovies.await().take(10), MediaSectionSource.EditorMovies),
                            MediaSection("Editor Picks — Shows", topTv.await().take(10), MediaSectionSource.EditorTv),
                        ),
                    ) }
                }
            }.onFailure { e ->
                android.util.Log.e("HomeVM", "Failed to load home sections", e)
                _state.update { it.copy(loading = false, error = e.message ?: "No connection") }
            }
        }
    }

    fun setTab(tab: HomeTab) = _state.update { it.copy(activeTab = tab) }
    fun setGenre(id: Int?) = _state.update { it.copy(selectedGenreId = id) }
    fun setSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun markFocusRequested() = _state.update { it.copy(initialFocusRequested = true) }

    // Live search results from TMDB (separate from carousel sections)
    private val _searchResults = MutableStateFlow<List<Media>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onSearchChange(q: String) {
        isSearchingMore = false
        setSearch(q)
        searchGeneration++
        currentSearchPage = 1
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _state.update { it.copy(canLoadMore = false) }
        if (q.isBlank()) {
            return
        }
        val generation = searchGeneration
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            runCatching {
                val firstResults = repo.search(q, currentSearchPage) { total ->
                    if (searchGeneration == generation) {
                        _state.update { it.copy(canLoadMore = currentSearchPage < total) }
                    }
                }
                if (searchGeneration != generation) return@launch
                _searchResults.value = firstResults
            }
        }
    }
    fun searchLoadMore() {
        val q = state.value.searchQuery
        if (q.isBlank() || !state.value.canLoadMore || isSearchingMore) return

        val generation = searchGeneration
        viewModelScope.launch {
            isSearchingMore = true
            try {
                currentSearchPage++

                runCatching {
                    val nextResults = repo.search(q, currentSearchPage) { total ->
                        if (searchGeneration == generation) {
                            _state.update { it.copy(canLoadMore = currentSearchPage < total) }
                        }
                    }
                    if (searchGeneration == generation) {
                        _searchResults.value = _searchResults.value + nextResults
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
