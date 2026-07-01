package com.zstream.android.data.adb

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

internal const val APK_ROWS_PER_PAGE = 10

data class GithubRepository(val owner: String, val name: String)

data class GithubApkAsset(
    val assetId: Long,
    val apkName: String,
    val label: String?,
    val version: String,
    val size: Long,
    val contentType: String,
    val state: String,
    val downloadCount: Long,
    val digest: String?,
    val assetCreatedAt: String,
    val assetUpdatedAt: String,
    val assetUploader: String,
    val downloadUrl: String,
    val releaseId: Long,
    val releaseName: String,
    val releaseTag: String,
    val targetCommit: String,
    val releaseDescription: String,
    val releaseAuthor: String,
    val releaseCreatedAt: String,
    val releasePublishedAt: String?,
    val releaseUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
)

class GithubReleaseCatalog(private val client: OkHttpClient = OkHttpClient()) {
    fun loadAllApks(repositoryUrl: String): List<GithubApkAsset> {
        val repository = parseGithubRepository(repositoryUrl)
        val result = mutableListOf<GithubApkAsset>()
        var page = 1
        do {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${repository.owner}/${repository.name}/releases?per_page=100&page=$page")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ZStream-Android")
                .build()
            val releaseCount = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JsonParser.parseString(body).asJsonObject.string("message") }.getOrNull()
                    throw IOException(message ?: "GitHub releases request failed: HTTP ${response.code}")
                }
                val releases = JsonParser.parseString(body).asJsonArray
                result += parseGithubReleaseApks(releases)
                releases.size()
            }
            page++
        } while (releaseCount == 100)
        return result
    }
}

internal fun parseGithubRepository(url: String): GithubRepository {
    val uri = try {
        URI(url.trim())
    } catch (e: Exception) {
        throw IllegalArgumentException("Enter a valid GitHub repository link", e)
    }
    require(uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true)) {
        "Enter a https://github.com/owner/repository link"
    }
    val parts = uri.path.split('/').filter(String::isNotBlank)
    require(parts.size >= 2) { "GitHub repository link is missing its owner or repository" }
    return GithubRepository(parts[0], parts[1].removeSuffix(".git"))
}

internal fun parseGithubReleaseApks(releases: JsonArray): List<GithubApkAsset> = buildList {
    releases.forEach { releaseElement ->
        val release = releaseElement.asJsonObject
        val tag = release.string("tag_name")
        val releaseName = release.string("name").ifBlank { tag }
        release.array("assets").forEach { assetElement ->
            val asset = assetElement.asJsonObject
            val apkName = asset.string("name")
            if (!apkName.endsWith(".apk", ignoreCase = true)) return@forEach
            add(
                GithubApkAsset(
                    assetId = asset.long("id"),
                    apkName = apkName,
                    label = asset.nullableString("label"),
                    version = findVersion(tag, releaseName, apkName),
                    size = asset.long("size"),
                    contentType = asset.string("content_type"),
                    state = asset.string("state"),
                    downloadCount = asset.long("download_count"),
                    digest = asset.nullableString("digest"),
                    assetCreatedAt = asset.string("created_at"),
                    assetUpdatedAt = asset.string("updated_at"),
                    assetUploader = asset.obj("uploader").string("login"),
                    downloadUrl = asset.string("browser_download_url"),
                    releaseId = release.long("id"),
                    releaseName = releaseName,
                    releaseTag = tag,
                    targetCommit = release.string("target_commitish"),
                    releaseDescription = release.nullableString("body").orEmpty(),
                    releaseAuthor = release.obj("author").string("login"),
                    releaseCreatedAt = release.string("created_at"),
                    releasePublishedAt = release.nullableString("published_at"),
                    releaseUrl = release.string("html_url"),
                    draft = release.boolean("draft"),
                    prerelease = release.boolean("prerelease"),
                ),
            )
        }
    }
}

internal fun findVersion(vararg values: String): String {
    val version = Regex("(?i)(?:^|[-_ ])v?(\\d+(?:\\.\\d+){1,3}(?:[-+][0-9a-z.-]+)?)")
    return values.firstNotNullOfOrNull { version.find(it)?.groupValues?.get(1) }
        ?: values.first().ifBlank { "Unknown" }
}

internal fun <T> releasePage(items: List<T>, page: Int): List<T> =
    items.drop(page.coerceAtLeast(0) * APK_ROWS_PER_PAGE).take(APK_ROWS_PER_PAGE)

private fun JsonObject.string(name: String): String = nullableString(name).orEmpty()
private fun JsonObject.nullableString(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0
private fun JsonObject.boolean(name: String): Boolean = get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
private fun JsonObject.obj(name: String): JsonObject = getAsJsonObject(name) ?: JsonObject()
private fun JsonObject.array(name: String): JsonArray = getAsJsonArray(name) ?: JsonArray()
