package com.zstream.android.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format for the plugin boundary (see Entry.availableSourcesJson / Entry.resolveJson on the
 * plugin side). Parsed by hand with org.json rather than Gson/reflection so a missing field
 * degrades to a default instead of an NPE, and an unknown extra field is silently ignored —
 * that tolerance is the entire point of moving this boundary to JSON.
 *
 * schemaVersion is carried in every envelope for the rare case a future breaking change needs to
 * branch on it. Non-breaking evolution (new optional field) needs no version bump.
 */
private const val SCHEMA_VERSION = 1

fun MediaRequest.toJson(): String = JSONObject().apply {
    put("schemaVersion", SCHEMA_VERSION)
    put("type", type.name)
    put("tmdbId", tmdbId)
    put("season", season ?: JSONObject.NULL)
    put("episode", episode ?: JSONObject.NULL)
    put("preferredVariantId", preferredVariantId ?: JSONObject.NULL)
    put("title", title)
    put("year", year ?: JSONObject.NULL)
    put("febboxKey", febboxKey ?: JSONObject.NULL)
    put("artemisVipKey", artemisVipKey ?: JSONObject.NULL)
}.toString()

fun parseSourcesJson(json: String): List<SourceInfo> {
    val root = JSONObject(json)
    val arr = root.optJSONArray("sources") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id", "")
        if (id.isBlank()) return@mapNotNull null
        SourceInfo(id = id, displayName = o.optString("displayName", id))
    }
}

fun parseStreamResultJson(json: String): StreamResult {
    val root = JSONObject(json)
    return when (root.optString("type", "error")) {
        "success" -> StreamResult.Success(
            streamUrl = root.optString("streamUrl", ""),
            streamType = root.optString("streamType", "hls"),
            captions = root.optJSONArray("captions").toCaptions(),
            headers = root.optJSONObject("headers").toStringMap(),
            codec = root.optString("codec", ""),
            variants = root.optJSONArray("variants").toVariants(),
            skipProbe = root.optBoolean("skipProbe", false),
        )
        "notFound" -> StreamResult.NotFound
        else -> StreamResult.Error(root.optString("message", "Plugin error"))
    }
}

private fun JSONArray?.toCaptions(): List<Caption> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val o = optJSONObject(i) ?: return@mapNotNull null
        Caption(
            url = o.optString("url", ""),
            language = o.optString("language", ""),
            langIso = o.optString("langIso", ""),
            type = o.optString("type", "vtt"),
        )
    }
}

private fun JSONArray?.toVariants(): List<StreamResult.Variant> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val o = optJSONObject(i) ?: return@mapNotNull null
        StreamResult.Variant(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            quality = o.optString("quality", ""),
            codec = o.optString("codec", ""),
            tag = o.optString("tag", ""),
            streamUrl = o.optString("streamUrl", ""),
            streamType = o.optString("streamType", "hls"),
            headers = o.optJSONObject("headers").toStringMap(),
            requiresRefreshOnSwitch = o.optBoolean("requiresRefreshOnSwitch", false),
        )
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optString(it, "") }
}
