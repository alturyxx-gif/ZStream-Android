package com.zstream.android.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.ProgressRepository
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreState(
    val title: String = "",
    val groupKey: String? = null,
    val media: List<Media> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val bookmarks: Map<String, BookmarkEntity> = emptyMap(),
    val editable: Boolean = false,
    val bookmarkSection: Boolean = false,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val repo: TmdbRepository,
    private val progressRepo: ProgressRepository,
    private val bookmarkRepo: BookmarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sourceName = savedStateHandle.get<String>("source").orEmpty()
    private val groupKey = savedStateHandle.get<String>("group")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(MoreState(
        title = prettyTitle(),
        groupKey = groupKey,
        editable = sourceName in LOCAL_SOURCES,
        bookmarkSection = sourceName == MediaSectionSource.Bookmarks.name || sourceName == MediaSectionSource.BookmarkGroup.name,
    ))
    val state = _state.asStateFlow()
    private var currentPage = 1
    private var loadingRequest = false
    private var localItems: List<Media> = emptyList()
    private var fullItems: List<Media> = emptyList()

    init {
        observeSource()
    }

    private fun observeSource() {
        viewModelScope.launch {
            when (sourceName) {
                MediaSectionSource.ContinueWatching.name -> progressRepo.observeAllProgress().collectLatest { progress ->
                    fullItems = progress.toContinueWatchingMedia()
                    refreshFromSource()
                }
                MediaSectionSource.Bookmarks.name -> bookmarkRepo.observeAllBookmarks().collectLatest { bookmarks ->
                    fullItems = bookmarks.toBookmarkMedia()
                    _state.value = _state.value.copy(bookmarks = bookmarks.associateBy { it.tmdbId })
                    refreshFromSource()
                }
                MediaSectionSource.BookmarkGroup.name -> bookmarkRepo.observeAllBookmarks().collectLatest { bookmarks ->
                    fullItems = bookmarks.filter { groupKey in it.groups.orEmpty() }.toBookmarkMedia()
                    _state.value = _state.value.copy(bookmarks = bookmarks.associateBy { it.tmdbId })
                    refreshFromSource()
                }
                else -> loadRemotePage(1)
            }
        }
    }

    private fun refreshFromSource() {
        currentPage = 1
        localItems = fullItems.take(PAGE_SIZE)
        _state.value = _state.value.copy(
            media = localItems,
            loading = false,
            loadingMore = false,
            canLoadMore = fullItems.size > localItems.size,
            error = null,
        )
    }

    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.canLoadMore || loadingRequest) return
        when (sourceName) {
            MediaSectionSource.ContinueWatching.name, MediaSectionSource.Bookmarks.name -> {
                loadingRequest = true
                val next = (currentPage * PAGE_SIZE).coerceAtMost(fullItems.size)
                val more = fullItems.drop(next).take(PAGE_SIZE)
                currentPage += 1
                localItems = localItems + more
                _state.value = _state.value.copy(
                    media = localItems,
                    loadingMore = false,
                    canLoadMore = fullItems.size > localItems.size,
                )
                loadingRequest = false
            }
            MediaSectionSource.BookmarkGroup.name -> {
                loadingRequest = true
                val next = (currentPage * PAGE_SIZE).coerceAtMost(fullItems.size)
                val more = fullItems.drop(next).take(PAGE_SIZE)
                currentPage += 1
                localItems = localItems + more
                _state.value = _state.value.copy(
                    media = localItems,
                    loadingMore = false,
                    canLoadMore = fullItems.size > localItems.size,
                )
                loadingRequest = false
            }
            else -> loadRemotePage(currentPage + 1)
        }
    }

    fun remove(media: Media) {
        viewModelScope.launch {
            when (sourceName) {
                MediaSectionSource.ContinueWatching.name -> progressRepo.removeProgress(media.id.toString())
                MediaSectionSource.Bookmarks.name -> bookmarkRepo.removeBookmark(media.id.toString())
                MediaSectionSource.BookmarkGroup.name -> _state.value.bookmarks[media.id.toString()]?.let {
                    bookmarkRepo.setBookmarkGroups(it.tmdbId, it.groups.orEmpty() - listOfNotNull(groupKey))
                }
            }
        }
    }

    fun updateBookmark(bookmark: BookmarkEntity, title: String, year: Int?, groups: List<String>) {
        viewModelScope.launch {
            bookmarkRepo.addBookmark(
                tmdbId = bookmark.tmdbId,
                title = title.ifBlank { bookmark.title },
                type = bookmark.type,
                year = year ?: bookmark.year,
                posterPath = bookmark.posterPath,
                groups = groups.ifEmpty { null },
            )
        }
    }

    fun renameGroup(newGroup: String) {
        val oldGroup = groupKey ?: return
        viewModelScope.launch { bookmarkRepo.renameGroup(oldGroup, newGroup) }
    }

    private fun loadRemotePage(page: Int) {
        viewModelScope.launch {
            loadingRequest = true
            _state.value = _state.value.copy(loading = page == 1, loadingMore = page > 1, error = null)
            runCatching {
                val result = loadRemote(page)
                currentPage = page
                if (page == 1) {
                    localItems = result.items
                } else {
                    localItems = localItems + result.items
                }
                _state.value = _state.value.copy(
                    title = prettyTitle(),
                    media = localItems,
                    loading = false,
                    loadingMore = false,
                    canLoadMore = result.canLoadMore,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, loadingMore = false, error = it.message ?: "No connection")
            }
            loadingRequest = false
        }
    }

    private data class PageResult(val items: List<Media>, val canLoadMore: Boolean)

    private suspend fun loadRemote(page: Int): PageResult {
        return when (sourceName) {
            MediaSectionSource.PopularMovies.name -> repo.popularMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.NowPlayingMovies.name -> repo.nowPlayingMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TopRatedMovies.name -> repo.topRatedMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TrendingMovies.name -> repo.trendingMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.PopularTv.name -> repo.popularTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.OnAirTv.name -> repo.onAirTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TopRatedTv.name -> repo.topRatedTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TrendingTv.name -> repo.trendingTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.EditorMovies.name -> repo.topRatedMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.EditorTv.name -> repo.topRatedTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            else -> PageResult(emptyList(), false)
        }
    }

    private fun prettyTitle(): String = when (sourceName) {
        MediaSectionSource.ContinueWatching.name -> "Continue Watching"
        MediaSectionSource.Bookmarks.name -> "My Bookmarks"
        MediaSectionSource.BookmarkGroup.name -> groupKey?.let(::normalizeGroupTitle) ?: "Bookmarks"
        else -> normalizeTitle(sourceName)
    }

    private fun normalizeTitle(value: String): String =
        value.replace('_', ' ').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")

    private fun normalizeGroupTitle(group: String): String =
        group.replace(Regex("^\\[[^\\]]+]\\s*"), "").trim()

    private fun List<ProgressEntity>.toContinueWatchingMedia(): List<Media> {
        val latestPerShow = groupBy { it.tmdbId }.mapValues { (_, entries) ->
            val nonCompleted = entries.filter { it.duration <= 0 || it.watched < it.duration * 0.95f }
            val candidates = if (nonCompleted.isNotEmpty()) nonCompleted else entries
            candidates.maxByOrNull {
                (it.seasonNumber ?: 0) * 100000 + (it.episodeNumber ?: 0)
            } ?: candidates.first()
        }
        return latestPerShow.values.map { p ->
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
                genreIds = null,
            )
        }
    }

    private fun List<BookmarkEntity>.toBookmarkMedia(): List<Media> =
        map { b ->
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
                genreIds = null,
            )
        }

    companion object {
        private const val PAGE_SIZE = 24
        private val LOCAL_SOURCES = setOf(
            MediaSectionSource.ContinueWatching.name,
            MediaSectionSource.Bookmarks.name,
            MediaSectionSource.BookmarkGroup.name,
        )
    }
}
