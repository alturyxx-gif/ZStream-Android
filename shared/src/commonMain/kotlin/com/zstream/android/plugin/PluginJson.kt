package com.zstream.android.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire format for the plugin boundary (see Entry.availableSourcesJson / Entry.resolveJson on the
 * plugin side). Parsed field-by-field with kotlinx.serialization's JsonElement API rather than a
 * data-class-based decoder so a missing field degrades to a default instead of throwing, and an
 * unknown extra field is silently ignored -- that tolerance is the entire point of moving this
 * boundary to JSON. (Ported from the original org.json-based androidMain-only implementation --
 * org.json isn't available on Kotlin/Native, kotlinx.serialization is portable to commonMain.)
 *
 * schemaVersion is carried in every envelope for the rare case a future breaking change needs to
 * branch on it. Non-breaking evolution (new optional field) needs no version bump.
 */
private const val SCHEMA_VERSION = 1

private val json = Json { ignoreUnknownKeys = true }

fun MediaRequest.toJson(): String = buildJsonObject {
    put("schemaVersion", SCHEMA_VERSION)
    put("type", type.name)
    put("tmdbId", tmdbId)
    put("season", season)
    put("episode", episode)
    put("preferredVariantId", preferredVariantId)
    put("title", title)
    put("year", year)
    put("febboxKey", febboxKey)
}.toString()

fun parseSourcesJson(jsonText: String): List<SourceInfo> {
    val root = json.parseToJsonElement(jsonText).jsonObject
    val arr = root["sources"]?.jsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el.jsonObject
        val id = o.optString("id", "")
        if (id.isBlank()) return@mapNotNull null
        SourceInfo(id = id, displayName = o.optString("displayName", id))
    }
}

fun parseStreamResultJson(jsonText: String): StreamResult {
    val root = json.parseToJsonElement(jsonText).jsonObject
    return when (root.optString("type", "error")) {
        "success" -> StreamResult.Success(
            streamUrl = root.optString("streamUrl", ""),
            streamType = root.optString("streamType", "hls"),
            captions = root["captions"]?.jsonArray.toCaptions(),
            headers = root["headers"]?.jsonObject.toStringMap(),
            codec = root.optString("codec", ""),
            variants = root["variants"]?.jsonArray.toVariants(),
            skipProbe = root.optBoolean("skipProbe", false),
        )
        "notFound" -> StreamResult.NotFound
        else -> StreamResult.Error(root.optString("message", "Plugin error"))
    }
}

private fun JsonArray?.toCaptions(): List<Caption> {
    if (this == null) return emptyList()
    return mapNotNull { el ->
        val o = el.jsonObject
        Caption(
            url = o.optString("url", ""),
            language = o.optString("language", ""),
            langIso = o.optString("langIso", ""),
            type = o.optString("type", "vtt"),
        )
    }
}

private fun JsonArray?.toVariants(): List<StreamResult.Variant> {
    if (this == null) return emptyList()
    return mapNotNull { el ->
        val o = el.jsonObject
        StreamResult.Variant(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            quality = o.optString("quality", ""),
            codec = o.optString("codec", ""),
            tag = o.optString("tag", ""),
            streamUrl = o.optString("streamUrl", ""),
            streamType = o.optString("streamType", "hls"),
            headers = o["headers"]?.jsonObject.toStringMap(),
            requiresRefreshOnSwitch = o.optBoolean("requiresRefreshOnSwitch", false),
        )
    }
}

private fun JsonObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return entries.associate { (k, v) -> k to (v.jsonPrimitive.contentOrNull ?: "") }
}

private fun JsonObject.optString(key: String, default: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: default

private fun JsonObject.optBoolean(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default
