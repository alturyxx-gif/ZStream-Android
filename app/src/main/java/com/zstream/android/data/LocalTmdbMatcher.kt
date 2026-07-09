package com.zstream.android.data

import com.zstream.android.data.model.Media

object LocalTmdbMatcher {
    fun best(title: String, expectedType: String?, results: List<Media>): Media? {
        val typed = expectedType?.let { type -> results.filter { it.type == type } }.orEmpty()
        val candidates = typed.ifEmpty { results }
        val normalizedTitle = title.normalized()
        return candidates.firstOrNull { it.displayTitle.normalized() == normalizedTitle }
            ?: candidates.firstOrNull { it.posterPath != null }
            ?: candidates.firstOrNull()
    }

    private fun String.normalized(): String = lowercase()
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
}
