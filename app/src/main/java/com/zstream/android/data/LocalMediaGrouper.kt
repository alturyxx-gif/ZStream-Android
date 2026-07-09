package com.zstream.android.data

data class LocalMediaGuess(
    val groupTitle: String,
    val mediaKind: String,
    val season: Int? = null,
    val episode: Int? = null,
)

object LocalMediaGrouper {
    private val seasonFolder = Regex("""(?i)^season\D*(\d{1,2})$""")
    private val episodePatterns = listOf(
        Regex("""(?i)(.*?)\bS(\d{1,2})E(\d{1,3})\b"""),
        Regex("""(?i)(.*?)\b(\d{1,2})x(\d{1,3})\b"""),
        Regex("""(?i)(.*?)\bSeason\D*(\d{1,2})\D+Episode\D*(\d{1,3})\b"""),
    )

    fun infer(relativePath: String, metadataTitle: String? = null): LocalMediaGuess {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        val fileName = parts.lastOrNull().orEmpty()
        val name = fileName.substringBeforeLast('.', fileName)

        val parent = parts.dropLast(1).lastOrNull()
        val grandParent = parts.dropLast(2).lastOrNull()
        val season = parent?.let { seasonFolder.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (season != null && grandParent != null) {
            episodeNumber(name)?.let { episode ->
                return LocalMediaGuess(cleanTitle(grandParent), "show", season, episode)
            }
        }

        episodePatterns.forEach { pattern ->
            val match = pattern.find(name) ?: return@forEach
            val titleFallback = if (season != null && grandParent != null) grandParent else parent ?: metadataTitle ?: name
            val title = cleanTitle(match.groupValues[1]).ifBlank { cleanTitle(titleFallback) }
            return LocalMediaGuess(title, "show", match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }

        return LocalMediaGuess(cleanTitle(metadataTitle ?: parent ?: name), "movie")
    }

    private fun episodeNumber(name: String): Int? {
        val explicit = Regex("""(?i)E(\d{1,3})\b""").find(name)?.groupValues?.get(1)?.toIntOrNull()
        if (explicit != null) return explicit
        return Regex("""\b(\d{1,3})\b""").find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun cleanTitle(value: String): String = value
        .substringBeforeLast('.', value)
        .replace(Regex("""[._-]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
