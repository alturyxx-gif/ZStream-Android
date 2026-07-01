package com.zstream.android.data.adb

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GithubReleaseCatalogTest {
    @Test
    fun repositoryLinkExtractsOwnerAndName() {
        assertEquals(
            GithubRepository("owner", "repo"),
            parseGithubRepository("https://github.com/owner/repo/releases/latest"),
        )
        assertThrows(IllegalArgumentException::class.java) { parseGithubRepository("https://example.com/owner/repo") }
    }

    @Test
    fun releaseParserKeepsOnlyApksAndAssetUploadMetadata() {
        val releases = JsonParser.parseString(
            """[{"id":7,"name":"Version 1.2.3","tag_name":"v1.2.3","target_commitish":"abc123","body":"Notes","html_url":"https://github.com/o/r/releases/7","draft":false,"prerelease":true,"created_at":"release-created","published_at":"release-published","author":{"login":"author"},"assets":[{"id":9,"name":"tv-v1.2.3.apk","label":"TV","size":42,"content_type":"application/vnd.android.package-archive","state":"uploaded","download_count":5,"digest":"sha256:abc","created_at":"asset-created","updated_at":"asset-updated","browser_download_url":"https://example.com/tv.apk","uploader":{"login":"uploader"}},{"id":10,"name":"source.zip"}]}]""",
        ).asJsonArray

        val apk = parseGithubReleaseApks(releases).single()
        assertEquals("tv-v1.2.3.apk", apk.apkName)
        assertEquals("1.2.3", apk.version)
        assertEquals("asset-created", apk.assetCreatedAt)
        assertEquals("uploader", apk.assetUploader)
        assertEquals("abc123", apk.targetCommit)
    }

    @Test
    fun releasePagesContainExactlyTenRows() {
        val rows = (0 until 25).toList()
        assertEquals((0 until 10).toList(), releasePage(rows, 0))
        assertEquals((10 until 20).toList(), releasePage(rows, 1))
        assertEquals((20 until 25).toList(), releasePage(rows, 2))
    }
}
