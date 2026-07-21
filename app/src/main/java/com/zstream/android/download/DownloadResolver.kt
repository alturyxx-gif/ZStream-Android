package com.zstream.android.download

import com.zstream.android.plugin.MediaRequest
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.SourceOrderStore
import com.zstream.android.plugin.StreamResult
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val ARTEMIS_RATE_LIMIT_MAX_RETRIES = 5
private const val ARTEMIS_RATE_LIMIT_BACKOFF_MS = 4000L

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
        displaySeason: Int? = null,
        displayEpisode: Int? = null,
        episodeTitle: String? = null,
        destinationTreeUri: String? = null,
    ): Result<Long> = runCatching {
        val pluginSources = pluginManager.availableSources()
        val settings = settingsPreferences.settings.first()
        val ordered = sourceOrderStore.getDownloadOrder()
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
        )

        var success: StreamResult.Success? = null
        var successSourceId: String? = null
        for (source in ordered) {
            var result = pluginManager.resolve(media, source.id)
            // Artemis's own rate limiter (8 req/25s) surfaces as a distinguishable
            // StreamResult.Error("RATE_LIMITED") rather than NotFound -- don't treat that as
            // "Artemis has nothing" and fall through to the next source in priority order, back
            // off and retry Artemis itself instead, same as we'd want for a season batch that's
            // hammering it episode after episode.
            if (source.id == "artemis") {
                var attempt = 0
                while (result is StreamResult.Error && result.message == "RATE_LIMITED" &&
                    attempt < ARTEMIS_RATE_LIMIT_MAX_RETRIES
                ) {
                    attempt++
                    delay(ARTEMIS_RATE_LIMIT_BACKOFF_MS)
                    result = pluginManager.resolve(media, source.id)
                }
            }
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
                displaySeason = displaySeason ?: season ?: 1,
                displayEpisode = displayEpisode ?: episode ?: 1,
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
