package com.zstream.android.download

import com.zstream.android.plugin.MediaRequest
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.SourceOrderStore
import com.zstream.android.plugin.StreamResult
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DownloadResolver @Inject constructor(
    private val pluginManager: PluginManager,
    private val sourceOrderStore: SourceOrderStore,
    private val downloadRepository: DownloadRepository,
    private val httpClient: OkHttpClient,
    private val settingsPreferences: com.zstream.android.data.local.preferences.SettingsPreferences,
    private val auroraKeyManager: com.zstream.android.data.AuroraKeyManager,
    @ApplicationContext private val appContext: android.content.Context,
) {
    suspend fun resolveAndEnqueue(
        mediaType: String,
        tmdbId: String,
        title: String,
        year: Int? = null,
        posterPath: String?,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        destinationTreeUri: String? = null,
    ): Result<Long> = runCatching {
        val pluginSources = pluginManager.availableSources()
        val settings = settingsPreferences.settings.first()
        val ordered = sourceOrderStore.getDownloadOrder(hasArtemisVipKey = !settings.artemisVipKey.isNullOrBlank())
        check(ordered.isNotEmpty()) { "No sources available" }

        auroraKeyManager.ensureActiveKey()
        val media = MediaRequest(
            type = if (mediaType == "tv") MediaRequest.Type.SHOW else MediaRequest.Type.MOVIE,
            tmdbId = tmdbId,
            season = season,
            episode = episode,
            title = title,
            year = year,
            febboxKey = settings.febboxKey,
            artemisVipKey = settings.artemisVipKey,
        )

        var success: StreamResult.Success? = null
        var successSourceId: String? = null
        for (source in ordered) {
            val result = pluginManager.resolve(media, source.id)
            if (result !is StreamResult.Success) continue
            if (result.streamType == "hls" && !result.skipProbe) {
                if (!probeHlsSegment(httpClient, result.streamUrl, result.headers)) continue
            }
            success = result
            successSourceId = source.id
            break
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
            destinationTreeUri = destinationTreeUri,
        )
        val downloadId = downloadRepository.enqueue(request)
        DownloadService.enqueue(appContext, downloadId, request)
        downloadId
    }
}
