package com.zstream.android.download

import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.plugin.MediaRequest
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.SourceInfo
import com.zstream.android.plugin.SourceOrderStore
import com.zstream.android.plugin.StreamResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Source ids tried first, in this order, for downloads triggered outside the player (detail
 * page / episode rows / season bulk download) — any other configured sources are tried after
 * these as a fallback. Matches the real plugin ids in the zstream-plugin submodule. */
private val PRIORITY_SOURCE_IDS = listOf("artemis", "stellar", "nesterov")

/**
 * Resolves a playable stream and enqueues a download without ever opening the player screen.
 * Mirrors the ordered-source-trying loop in PlayerViewModel.loadInternal() but with no
 * Compose/UI-observed state — just tries sources in priority order and enqueues the first hit.
 */
@Singleton
class DownloadResolver @Inject constructor(
    private val pluginManager: PluginManager,
    private val sourceOrderStore: SourceOrderStore,
    private val settingsPrefs: SettingsPreferences,
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val appContext: android.content.Context,
) {
    suspend fun resolveAndEnqueue(
        mediaType: String,
        tmdbId: String,
        title: String,
        posterPath: String?,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
    ): Result<Long> = runCatching {
        val pluginSources = pluginManager.availableSources()
        val ordered = orderedWithPriority(sourceOrderStore.getOrderedSources(pluginSources))
        check(ordered.isNotEmpty()) { "No sources available" }

        val media = MediaRequest(
            type = if (mediaType == "tv") MediaRequest.Type.SHOW else MediaRequest.Type.MOVIE,
            tmdbId = tmdbId,
            season = season,
            episode = episode,
        )

        var success: StreamResult.Success? = null
        var successSourceId: String? = null
        for (source in ordered) {
            val result = pluginManager.resolve(media, source.id)
            if (result is StreamResult.Success) {
                success = result
                successSourceId = source.id
                break
            }
        }
        val result = requireNotNull(success) { "No playable stream found" }
        val sourceId = requireNotNull(successSourceId)
        val sourceDisplayName = pluginSources.firstOrNull { it.id == sourceId }?.displayName ?: sourceId

        val target = if (mediaType == "tv") {
            DownloadTarget.Episode(
                showTitle = title,
                season = season ?: 1,
                episode = episode ?: 1,
                episodeTitle = episodeTitle,
            )
        } else {
            DownloadTarget.Movie(title = title)
        }

        val captions = result.captions.map { cap ->
            com.zstream.android.plugin.Caption(url = cap.url, language = cap.language, langIso = cap.langIso, type = cap.type, source = cap.source)
        }

        val request = DownloadRequest(
            tmdbId = tmdbId,
            target = target,
            sourceId = sourceId,
            sourceDisplayName = sourceDisplayName,
            variantId = result.variants.firstOrNull { it.streamUrl == result.streamUrl }?.id ?: sourceId,
            qualityLabel = result.variants.firstOrNull { it.streamUrl == result.streamUrl }?.quality ?: "Auto",
            streamUrl = result.streamUrl,
            streamType = result.streamType,
            headers = result.headers,
            captions = captions,
            posterPath = posterPath,
        )
        val downloadId = downloadRepository.enqueue(request)
        DownloadService.enqueue(appContext, downloadId, request)
        downloadId
    }

    private fun orderedWithPriority(sources: List<SourceInfo>): List<SourceInfo> {
        val priority = PRIORITY_SOURCE_IDS.mapNotNull { id -> sources.firstOrNull { it.id.equals(id, ignoreCase = true) } }
        val rest = sources.filterNot { source -> priority.any { it.id == source.id } }
        return priority + rest
    }
}
