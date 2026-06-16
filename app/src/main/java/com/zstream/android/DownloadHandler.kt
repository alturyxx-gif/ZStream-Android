package com.zstream.android

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView

class DownloadHandler(private val context: Context, private val webView: WebView) {

    var pendingBlobBytes: ByteArray? = null

    fun handleDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (url.startsWith("blob:")) {
            readBlobViaJs(url, mimeType, contentDisposition)
        } else {
            enqueueSystemDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun readBlobViaJs(url: String, mimeType: String, contentDisposition: String) {
        // Blob URLs can't be downloaded directly — read via JS and pass bytes to Kotlin
        val js = """
            (function() {
                fetch('$url')
                    .then(r => r.blob())
                    .then(b => {
                        var reader = new FileReader();
                        reader.onload = function() {
                            AndroidDownload.receiveBlob(reader.result, '$mimeType', '$contentDisposition');
                        };
                        reader.readAsDataURL(b);
                    });
            })()
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun enqueueSystemDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType),
            )
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    inner class BlobReceiverBridge(private val onReceived: (ByteArray, String, String) -> Unit) {
        @JavascriptInterface
        fun receiveBlob(dataUrl: String, mimeType: String, contentDisposition: String) {
            val bytes = android.util.Base64.decode(dataUrl.substringAfter(","), android.util.Base64.DEFAULT)
            onReceived(bytes, mimeType, contentDisposition)
        }
    }

    fun createSaveIntent(mimeType: String, contentDisposition: String): Intent {
        val fileName = URLUtil.guessFileName("", contentDisposition, mimeType)
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType.ifEmpty { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }
}
