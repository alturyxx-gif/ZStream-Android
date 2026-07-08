package com.zstream.android.plugin

/**
 * App-side data shapes for the plugin boundary. These are NOT shared with the plugin as
 * compiled classes — they exist purely so the rest of the app has typed values to work with
 * after PluginManager parses the plugin's JSON responses. See PluginJson.kt for the wire format.
 */
data class SourceInfo(
    val id: String,
    val displayName: String,
)

data class MediaRequest(
    val type: Type,
    val tmdbId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val preferredVariantId: String? = null,
) {
    enum class Type { MOVIE, SHOW }
}

data class Caption(
    val url: String,
    val language: String,
    val langIso: String,
    val type: String,
)

sealed class StreamResult {
    data class Success(
        val streamUrl: String,
        val streamType: String,
        val captions: List<Caption> = emptyList(),
        val headers: Map<String, String> = emptyMap(),
        val codec: String = "",
        val variants: List<Variant> = emptyList(),
        val skipProbe: Boolean = false,
    ) : StreamResult()

    data class Variant(
        val id: String,
        val name: String,
        val quality: String,
        val codec: String,
        val tag: String,
        val streamUrl: String,
        val streamType: String = "hls",
        val headers: Map<String, String> = emptyMap(),
        val requiresRefreshOnSwitch: Boolean = false,
    )

    object NotFound : StreamResult()

    data class Error(val message: String) : StreamResult()
}
