package com.zstream.android.data

data class LocalMediaGuess(
    val groupTitle: String,
    val mediaKind: String,
    val season: Int? = null,
    val episode: Int? = null,
    val groupKey: String = "${mediaKind}:${groupTitle.lowercase()}",
    val matchSource: String = "filename",
)

object LocalMediaGrouper {
    const val UNCATEGORIZED = "Uncategorized"

    private val seasonFolder = Regex("""(?i)^(?:s|season)\D*(\d{1,2})$""")
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
        val greatGrandParent = parts.dropLast(3).lastOrNull()
        val season = parent?.let { seasonFolder.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (season != null && grandParent != null) {
            episodeNumber(name)?.let { episode ->
                val title = cleanTitle(grandParent)
                return LocalMediaGuess(title, "show", season, episode, groupKey("show", title), "folder")
            }
        }
        val nestedSeason = grandParent?.let { seasonFolder.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (nestedSeason != null && greatGrandParent != null) {
            episodeNumber(parent.orEmpty())?.let { episode ->
                val title = cleanTitle(greatGrandParent)
                return LocalMediaGuess(title, "show", nestedSeason, episode, groupKey("show", title), "folder")
            }
        }

        parseEpisode(metadataTitle)?.let { (title, metadataSeason, metadataEpisode) ->
            return LocalMediaGuess(title, "show", metadataSeason, metadataEpisode, groupKey("show", title), "metadata")
        }

        episodePatterns.forEach { pattern ->
            val match = pattern.find(name) ?: return@forEach
            val title = cleanTitle(match.groupValues[1])
            if (title.isNotBlank()) {
                return LocalMediaGuess(title, "show", match.groupValues[2].toInt(), match.groupValues[3].toInt(), groupKey("show", title), "filename")
            }
        }

        cleanTitle(metadataTitle.orEmpty()).takeIf { it.isNotBlank() }?.let { title ->
            return LocalMediaGuess(title, "movie", groupKey = groupKey("movie", title), matchSource = "metadata")
        }

        val cleanName = cleanTitle(name)
        val cleanParent = cleanTitle(parent.orEmpty())
        if (cleanName.isNotBlank() && cleanName.equals(cleanParent, ignoreCase = true)) {
            return LocalMediaGuess(cleanName, "movie", groupKey = groupKey("movie", cleanName), matchSource = "folder")
        }

        return LocalMediaGuess(UNCATEGORIZED, "unknown", groupKey = "uncategorized", matchSource = "uncategorized")
    }

    private fun parseEpisode(value: String?): Triple<String, Int, Int>? {
        if (value.isNullOrBlank()) return null
        episodePatterns.forEach { pattern ->
            val match = pattern.find(value) ?: return@forEach
            val title = cleanTitle(match.groupValues[1])
            if (title.isNotBlank()) return Triple(title, match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
        return null
    }

    private fun episodeNumber(name: String): Int? {
        val explicit = Regex("""(?i)E(\d{1,3})\b""").find(name)?.groupValues?.get(1)?.toIntOrNull()
        if (explicit != null) return explicit
        return Regex("""\b(\d{1,3})\b""").find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun groupKey(kind: String, title: String) = "$kind:${cleanTitle(title).lowercase()}"

    private fun cleanTitle(value: String): String = value
        .substringBeforeLast('.', value)
        .replace(Regex("""[._-]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
