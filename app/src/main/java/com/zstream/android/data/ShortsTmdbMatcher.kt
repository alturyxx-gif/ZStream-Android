package com.zstream.android.data

import com.zstream.android.data.remote.TmdbApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ShortsTmdbMatch(val tmdbId: Int, val mediaType: String)

private val bracketNoiseRegex = Regex("""[\[(].*?[\])]""")
private val hashtagRegex = Regex("""#\S+""")
private val yearRegex = Regex("""\b(19|20)\d{2}\b""")
private val nonWordRegex = Regex("""[^a-z0-9\s]""")
private val whitespaceRegex = Regex("""\s+""")

private val boilerplateWords = listOf(
    "official clip", "official scene", "movie clip", "movieclips",
    "full scene", "hd", "4k", "ending explained", "scene", "clip",
    "funny moments", "best moments", "compilation",
)

private const val MIN_POPULARITY = 0.5

private fun cleanClipTitle(title: String): String {
    var t = title.lowercase()
    t = bracketNoiseRegex.replace(t, " ")
    t = hashtagRegex.replace(t, " ")
    for (w in boilerplateWords) t = t.replace(w, " ")
    t = nonWordRegex.replace(t, " ")
    t = whitespaceRegex.replace(t, " ")
    return t.trim()
}

private fun extractYear(title: String): String? = yearRegex.find(title)?.value

private fun titleCandidates(rawTitle: String): List<String> {
    val withYear = mutableListOf<String>()
    val withoutYear = mutableListOf<String>()
    for (rawSeg in rawTitle.split("|")) {
        val seg = rawSeg.trim()
        if (seg.isEmpty()) continue
        val hasYear = yearRegex.containsMatchIn(seg)
        val cleaned = cleanClipTitle(bracketNoiseRegex.replace(seg, " "))
        if (cleaned.isEmpty()) continue
        if (hasYear) withYear.add(cleaned) else withoutYear.add(cleaned)
    }
    val out = (withYear + withoutYear).toMutableList()
    val whole = cleanClipTitle(rawTitle)
    if (whole.isNotEmpty()) out.add(whole)
    return out
}

private fun yearWithinOne(a: String, b: String): Boolean {
    val an = a.toIntOrNull() ?: return true
    val bn = b.toIntOrNull() ?: return true
    return abs(an - bn) <= 1
}

private fun tokensContain(a: String, b: String): Boolean {
    val aClean = nonWordRegex.replace(a, " ")
    val bClean = nonWordRegex.replace(b.lowercase(), " ")
    val (shorter, longer) = if (bClean.length < aClean.length) bClean to aClean else aClean to bClean
    val words = shorter.split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    var matched = 0
    for (w in words) {
        if (w.length < 3 || longer.contains(w)) matched++
    }
    return matched >= (words.size + 1) / 2
}

@Singleton
class ShortsTmdbMatcher @Inject constructor(
    private val tmdbApi: TmdbApi,
) {
    suspend fun match(rawTitle: String): ShortsTmdbMatch? {
        val year = extractYear(rawTitle)
        for (candidate in titleCandidates(rawTitle)) {
            searchOnce(candidate, year)?.let { return it }
        }
        return null
    }

    private suspend fun searchOnce(query: String, year: String?): ShortsTmdbMatch? {
        if (query.isBlank()) return null
        val results = runCatching { tmdbApi.search(query).results }.getOrNull() ?: return null

        for (r in results) {
            if (r.mediaType != "movie" && r.mediaType != "tv") continue
            if ((r.popularity ?: 0.0) < MIN_POPULARITY) continue
            val resultTitle = if (r.mediaType == "tv") r.name else r.title
            val resultDate = if (r.mediaType == "tv") r.firstAirDate else r.releaseDate
            if (resultTitle.isNullOrEmpty()) continue
            if (year != null) {
                val resultYear = resultDate?.takeIf { it.length >= 4 }?.substring(0, 4)
                if (resultYear != null && !yearWithinOne(year, resultYear)) continue
            }
            if (!tokensContain(query, resultTitle)) continue
            return ShortsTmdbMatch(r.id, r.mediaType!!)
        }
        return null
    }
}
