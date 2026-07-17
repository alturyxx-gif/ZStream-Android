package com.zstream.android.data.model

import com.google.gson.annotations.SerializedName

/** See: https://developer.themoviedb.org/reference/tv-episode-group-details */
object EpisodeGroupType {
    const val ORIGINAL_AIR_DATE = 1
    const val ABSOLUTE = 2
    const val DVD = 3
    const val DIGITAL = 4
    const val STORY_ARC = 5
    const val PRODUCTION = 6
    const val TV = 7
}

data class EpisodeGroupsResponse(
    val id: Int? = null,
    val results: List<EpisodeGroupSummary> = emptyList(),
)

data class EpisodeGroupSummary(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val type: Int = 0,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("group_count") val groupCount: Int = 0,
    val network: Any? = null,
)

data class EpisodeGroupDetail(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val type: Int = 0,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("group_count") val groupCount: Int = 0,
    val groups: List<EpisodeGroupSeason> = emptyList(),
)

data class EpisodeGroupSeason(
    val id: String? = null,
    val name: String? = null,
    val order: Int = 0,
    val episodes: List<EpisodeGroupEpisode> = emptyList(),
)

data class EpisodeGroupEpisode(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("still_path") val stillPath: String? = null,
    val order: Int = 0,
)

data class TvSeasonCatalog(
    val seasons: List<Season>,
    val usingEpisodeGroups: Boolean,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupType: Int? = null,
) {
    fun season(number: Int): Season? = seasons.firstOrNull { it.seasonNumber == number }

    fun findEpisode(
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeId: String? = null,
    ): Episode? {
        val all = seasons.flatMap { it.episodes.orEmpty() }
        if (!episodeId.isNullOrBlank()) {
            all.firstOrNull { it.id.toString() == episodeId }?.let { return it }
        }
        if (seasonNumber != null && episodeNumber != null) {
            all.firstOrNull {
                it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber
            }?.let { return it }


            // Handle legacy progress
            all.firstOrNull {
                it.sourceSeasonNumber == seasonNumber && it.sourceEpisodeNumber == episodeNumber
            }?.let { return it }
        }
        return null
    }

    /** Next aired episode after [seasonNumber]/[episodeNumber] in catalog order. */
    fun nextEpisode(seasonNumber: Int, episodeNumber: Int): Episode? {
        val ordered = seasons
            .sortedBy { it.seasonNumber }
            .flatMap { season ->
                season.episodes.orEmpty()
                    .airedEpisodes()
                    .sortedBy { it.episodeNumber }
            }
        var i = ordered.indexOfFirst {
            it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber
        }
        if (i < 0) {
            val current = findEpisode(seasonNumber, episodeNumber) ?: return null
            i = ordered.indexOfFirst { it.id == current.id }
            if (i < 0 || i >= ordered.lastIndex) return null
            return ordered[i + 1]
        }
        if (i >= ordered.lastIndex) return null
        return ordered[i + 1]
    }
}

object EpisodeGroupResolver {
    /** Prefer streaming/digital splits, then production seasons, then story arcs. */
    private val preferredTypes = setOf(
        EpisodeGroupType.DIGITAL,
        EpisodeGroupType.PRODUCTION,
        EpisodeGroupType.STORY_ARC,
    )

    /**
     * Absolute-style dumps often cram dozens/hundreds of episodes into one TMDB season.
     * Multi-season Western shows stay well under this per season.
     */
    const val LARGE_SEASON_EPISODE_THRESHOLD = 50

    /** Single-season shows with at least this many episodes are candidates for group remapping. */
    const val SINGLE_SEASON_EPISODE_THRESHOLD = 13

    fun shouldPreferEpisodeGroups(detail: TvDetail): Boolean {
        val real = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
        if (real.isEmpty()) return false
        if (real.size == 1) {
            return (real.first().episodeCount ?: 0) >= SINGLE_SEASON_EPISODE_THRESHOLD
        }
        return real.any { (it.episodeCount ?: 0) >= LARGE_SEASON_EPISODE_THRESHOLD }
    }

    fun pickBestGroup(summaries: List<EpisodeGroupSummary>): EpisodeGroupSummary? {
        val eligible = summaries.filter {
            it.groupCount >= 2 && it.type in preferredTypes && it.episodeCount > 0
        }
        if (eligible.isEmpty()) return null
        return eligible.maxByOrNull { score(it) }
    }

    private fun score(group: EpisodeGroupSummary): Long {
        return when (group.type) {
            EpisodeGroupType.DIGITAL -> 300_000L
            EpisodeGroupType.PRODUCTION -> 200_000L
            EpisodeGroupType.STORY_ARC -> 100_000L
            else -> 0L
        }
    }

    fun mapGroupDetailToCatalog(detail: EpisodeGroupDetail): TvSeasonCatalog {
        val sorted = detail.groups.sortedWith(
            compareBy<EpisodeGroupSeason> { it.order }.thenBy { it.name.orEmpty() },
        )

        val used = mutableSetOf<Int>()
        var nextSequential = 1
        val seasons = sorted.mapNotNull { group ->
            if (group.episodes.isEmpty()) return@mapNotNull null

            val preferred = preferredDisplaySeasonNumber(group, nextSequential)
            used.add(preferred)
            if (preferred > 0) {
                while (nextSequential in used) nextSequential++
            }

            val episodes = mapGroupEpisodes(group, preferred)
            Season(
                id = stableSeasonId(detail.id, preferred),
                seasonNumber = preferred,
                name = group.name?.takeIf { it.isNotBlank() }
                    ?: if (preferred == 0) "Specials" else "Season $preferred",
                episodeCount = episodes.size,
                posterPath = null,
                episodes = episodes,
            )
        }.filter { !it.episodes.isNullOrEmpty() }

        return TvSeasonCatalog(
            seasons = seasons,
            usingEpisodeGroups = true,
            groupId = detail.id,
            groupName = detail.name,
            groupType = detail.type,
        )
    }

    internal fun preferredDisplaySeasonNumber(group: EpisodeGroupSeason, nextSequential: Int): Int {
        if (isSpecialsGroup(group)) return 0
        parseSeasonNumberFromName(group.name)?.let { return it }
        if (group.order > 0) return group.order
        return nextSequential
    }

    /**
     * Prevent special groups (often index 0 of Episode Groups) from being marked as Season 0
     */
    internal fun isSpecialsGroup(group: EpisodeGroupSeason): Boolean {
        val name = group.name.orEmpty().lowercase()
        if (name.contains("special")) return true
        if (name.contains("ova") && !name.contains("season")) return true
        if (name.contains("ona") && !name.contains("season")) return true
        if (name.contains("extra") && !name.contains("season")) return true
        if (parseSeasonNumberFromName(group.name) == 0) return false // For ACTUAL pre-seasons

        val episodes = group.episodes
        if (episodes.isNotEmpty()) {
            val s0 = episodes.count { it.seasonNumber == 0 }
            if (s0 * 2 >= episodes.size) return true // More than half the episodes are "S0" (Likely a group of specials)
        }

        if (group.order == 0 && parseSeasonNumberFromName(group.name) == null) {
            return true
        }
        return false
    }

    /** Parses "Season 2", "S2", "2nd Season", etc. Returns null if no clear number. */
    internal fun parseSeasonNumberFromName(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        Regex("""(?i)\bseason\s*(\d+)\b""").find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let { return it }
        Regex("""(?i)^s(\d+)\b""").find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let { return it }
        Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b""").find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let { return it }
        return null
    }

    private fun mapGroupEpisodes(group: EpisodeGroupSeason, displaySeason: Int): List<Episode> {
        if (group.episodes.isEmpty()) return emptyList()
        val minOrder = group.episodes.minOf { it.order }
        return group.episodes
            .sortedBy { it.order }
            .map { ep ->
                val displayEpisode = if (minOrder <= 0) ep.order + 1 else ep.order
                Episode(
                    id = ep.id,
                    name = ep.name,
                    episodeNumber = displayEpisode.coerceAtLeast(1),
                    seasonNumber = displaySeason,
                    stillPath = ep.stillPath,
                    overview = ep.overview,
                    airDate = ep.airDate,
                    sourceSeasonNumber = ep.seasonNumber,
                    sourceEpisodeNumber = ep.episodeNumber,
                )
            }
    }

    /** Stable positive id for Room/UI that doesn't collide with TMDB season ids often. */
    private fun stableSeasonId(groupId: String, displaySeason: Int): Int {
        var h = 17
        for (c in groupId) h = 31 * h + c.code
        h = 31 * h + displaySeason
        // Keep non-zero; flip negatives from hash overflow.
        return if (h == 0) displaySeason else h
    }

    fun defaultCatalogFromDetail(detail: TvDetail): TvSeasonCatalog {
        val seasons = detail.seasons.orEmpty()
            .filter { it.seasonNumber > 0 }
            .map { it.copy(episodes = null) }
        return TvSeasonCatalog(
            seasons = seasons,
            usingEpisodeGroups = false,
        )
    }
}
