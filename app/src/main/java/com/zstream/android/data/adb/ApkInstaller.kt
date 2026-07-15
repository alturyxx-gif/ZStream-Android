package com.zstream.android.data.adb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed class ApkDownloadProgress {
    object Connecting : ApkDownloadProgress()
    data class Downloading(val bytes: Long, val totalBytes: Long) : ApkDownloadProgress()
    object Done : ApkDownloadProgress()
}

/**
 * Downloads the latest APK to this app's cache dir and hands it to the system's
 * PackageInstaller via a FileProvider content:// URI (the standard non-Play-Store
 * "unknown sources" install flow). Works the same way on Android TV/Google TV — there's
 * no separate install API for TV, just the same ACTION_VIEW intent — but TV devices only
 * expose the "allow installs from this app" toggle deep in Settings with no easy way to
 * tap it via remote, which is why the ADB-based [TvAdbManager] install path exists
 * separately for TV and remains the primary TV update flow.
 */
object ApkInstaller {
    private val client = OkHttpClient()

    private val _progress = MutableStateFlow<ApkDownloadProgress?>(null)
    val progress = _progress.asStateFlow()

    /** True if the OS will let us launch the install-package intent without extra permission setup. */
    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" settings page scoped to this app, for API 26+. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    suspend fun downloadAndInstall(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
        _progress.value = ApkDownloadProgress.Connecting
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val dest = File(dir, "update.apk")
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty download response")
                val total = body.contentLength()
                var written = 0L
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            written += read
                            _progress.value = ApkDownloadProgress.Downloading(written, total)
                        }
                    }
                }
            }
            _progress.value = ApkDownloadProgress.Done
            withContext(Dispatchers.Main) { install(context, dest) }
        } finally {
            _progress.value = null
        }
    }

    private fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
