package com.zstream.android.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.zstream.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PluginLoader"

/**
 * Handles all filesystem and classloader operations for the plugin.
 *
 * Responsibilities:
 *  - Atomic download: stream to .tmp → verify hash → mark read-only → rename
 *  - APK signing certificate verification against pinned cert SHA-256
 *  - SHA-256 hash verification
 *  - DexClassLoader load + StreamPlugin cast
 *  - Rollback guard: refuses to load a version lower than [highestSeenVersion]
 *
 * This class has no coroutine scope and no state. It is called by PluginManager.
 */
@Singleton
class PluginLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {

    /**
     * SHA-256 fingerprint of the plugin APK's signing certificate (hex, lowercase).
     * Replace this with the actual fingerprint before the first plugin release.
     *
     * To obtain it after signing the plugin APK:
     *   keytool -printcert -jarfile plugin.apk
     * or:
     *   apksigner verify --print-certs plugin.apk
     *
     * ponytail: placeholder value — will cause every plugin to fail signature check
     * until replaced with the real cert fingerprint.
     */
    private val PINNED_CERT_SHA256 = "36f25bd39f566958bae8274e6b87b4c0ad91774e64834ec319cca9df6177cf0e"

    // -------------------------------------------------------------------------
    // Directories
    // -------------------------------------------------------------------------

    fun pluginDir(): File = context.filesDir.resolve(PluginConstants.PLUGIN_DIR).also { it.mkdirs() }

    private fun dexCacheDir(): File =
        context.codeCacheDir.resolve(PluginConstants.DEX_CACHE_DIR).also { it.mkdirs() }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Downloads [url] to a temp file, verifies [expectedHash], marks read-only,
     * renames to final name. Returns the final file.
     *
     * Caller supplies [onProgress] receiving 0f–1f. Throws on any failure.
     * The temp file is always deleted on failure.
     */
    fun download(
        url: String,
        version: Int,
        expectedHash: String,
        onProgress: (Float) -> Unit = {},
    ): File {
        val tmp = pluginDir().resolve("plugin-v$version.apk.tmp")
        val final = pluginDir().resolve("plugin-v$version.apk")
        tmp.delete()

        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body ?: throw IllegalStateException("Empty response body")

            val totalBytes = body.contentLength().takeIf { it > 0 }
            var bytesRead = 0L
            tmp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        bytesRead += n
                        if (totalBytes != null) {
                            onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                        }
                    }
                }
            }

            if (!verifyHash(tmp, expectedHash)) {
                throw SecurityException("Plugin hash mismatch after download")
            }

            tmp.setReadOnly()

            if (final.exists()) final.delete()
            check(tmp.renameTo(final)) { "Failed to rename plugin temp file to final path" }
            final.setReadOnly()

            onProgress(1f)
            Log.i(TAG, "Downloaded plugin v$version to ${final.name}")
            return final

        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Verification
    // -------------------------------------------------------------------------

    /** Returns true if the file's SHA-256 matches [expectedHash] ("sha256:<hex>"). */
    fun verifyHash(file: File, expectedHash: String): Boolean {
        val hex = expectedHash.removePrefix("sha256:")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val match = constantTimeEquals(actual, hex)
        if (!match) Log.w(TAG, "Hash mismatch: expected $hex, got $actual")
        return match
    }

    /**
     * Returns true if the APK at [file] is signed by the pinned certificate.
     * Uses PackageManager to extract the signing certificate from the archive.
     */
    fun verifySignature(file: File): Boolean {
        // ponytail: skip cert pin in debug builds — replace PINNED_CERT_SHA256 before release
        if (BuildConfig.DEBUG) return true
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = pm.getPackageArchiveInfo(file.absolutePath, flags)
                ?: run { Log.w(TAG, "getPackageArchiveInfo returned null"); return false }

            val certs: Array<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }?.toTypedArray()
                    ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }?.toTypedArray() ?: emptyArray()
            }

            if (certs.isEmpty()) {
                Log.w(TAG, "No signing certificates found in plugin APK")
                return false
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val certFingerprint = digest.digest(certs[0])
                .joinToString("") { "%02x".format(it) }

            val match = constantTimeEquals(certFingerprint, PINNED_CERT_SHA256)
            if (!match) Log.w(TAG, "Plugin cert mismatch: $certFingerprint")
            match

        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed: ${e.message}", e)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Loads the plugin APK via DexClassLoader and returns the raw Entry instance.
     * Marks [file] read-only before loading.
     * Extracts native libraries from the APK into a private dir so DexClassLoader can find them.
     *
     * The plugin has no compiled contract with the app — PluginManager talks to it entirely via
     * reflection (availableSourcesJson()/resolveJson()), so there is nothing to cast to here.
     */
    fun load(file: File): Any {
        file.setReadOnly()

        // Extract native libs from the APK into a private dir keyed by APK name.
        // DexClassLoader requires a filesystem path — it cannot load .so directly from a ZIP.
        val nativeLibDir = extractNativeLibs(file)

        val classLoader = dalvik.system.DexClassLoader(
            file.absolutePath,
            dexCacheDir().absolutePath,
            nativeLibDir?.absolutePath,
            PluginLoader::class.java.classLoader,
        )
        val clazz = classLoader.loadClass(PluginConstants.ENTRY_CLASS)
        return clazz.getDeclaredConstructor().newInstance()
    }

    /**
     * Extracts .so files for the current ABI from the plugin APK into a private directory.
     * Returns the directory, or null if no native libs are present.
     */
    private fun extractNativeLibs(apkFile: File): File? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        val outDir = context.filesDir.resolve("plugin-native/${apkFile.nameWithoutExtension}/$abi")
        outDir.mkdirs()

        var found = false
        java.util.zip.ZipFile(apkFile).use { zip ->
            val prefix = "lib/$abi/"
            zip.entries().asSequence()
                .filter { it.name.startsWith(prefix) && it.name.endsWith(".so") }
                .forEach { entry ->
                    val soFile = outDir.resolve(entry.name.removePrefix(prefix))
                    zip.getInputStream(entry).use { input ->
                        soFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    found = true
                }
        }
        return if (found) outDir else null
    }

    // -------------------------------------------------------------------------
    // Rollback guard
    // -------------------------------------------------------------------------

    /**
     * Throws [SecurityException] if [version] is lower than [highestSeenVersion].
     * Call before activating any plugin file.
     */
    fun checkRollback(version: Int, highestSeenVersion: Int) {
        if (version < highestSeenVersion) {
            throw SecurityException(
                "Plugin rollback refused: requested v$version but highest seen is v$highestSeenVersion"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Constant-time string comparison to prevent timing side-channels. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
