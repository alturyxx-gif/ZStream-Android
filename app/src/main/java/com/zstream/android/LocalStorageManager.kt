package com.zstream.android

import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOCAL_STORAGE_SNAPSHOT_JS = """
    (function() {
        function get(key) {
            try { var s = localStorage.getItem(key); return s ? JSON.parse(s).state : null; }
            catch(e) { return null; }
        }
        var auth = get('__MW::auth') || {};
        var bookmarks = get('__MW::bookmarks') || {};
        var progress = get('__MW::progress') || {};
        var watchHistory = get('__MW::watchHistory') || {};
        var groupOrder = get('__MW::groupOrder') || {};
        var prefs = get('__MW::preferences') || {};
        var subtitles = get('__MW::subtitles') || {};
        var theme = get('__MW::theme') || {};
        var locale = get('__MW::locale') || {};
        return JSON.stringify({
            account: auth.account ? { profile: auth.account.profile, deviceName: auth.account.deviceName } : null,
            bookmarks: bookmarks.bookmarks || {},
            progress: progress.items || {},
            watchHistory: watchHistory.items || {},
            groupOrder: groupOrder.groupOrder || [],
            settings: Object.assign({}, prefs, { defaultSubtitleLanguage: subtitles.lastSelectedLanguage || null }),
            theme: theme.theme || null,
            language: locale.language || null,
            exportDate: new Date().toISOString()
        });
    })()
""".trimIndent()

class LocalStorageManager(private val webView: WebView) {

    var snapshot: String? = null
        private set

    fun takeSnapshot() {
        webView.evaluateJavascript(LOCAL_STORAGE_SNAPSHOT_JS) { result ->
            if (result != null && result.length > 2) {
                snapshot = result.removePrefix("\"").removeSuffix("\"")
                    .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
            }
        }
    }

    fun isEmpty(): Boolean {
        val s = snapshot
        return s.isNullOrEmpty() || s == "{}" ||
            (s.contains("\"bookmarks\":{}") && s.contains("\"progress\":{}") && s.contains("\"watchHistory\":{}"))
    }

    fun createExportIntent(): Intent {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "zstream-data-$date.json")
        }
    }

    inner class ExportBridge(private val onRequest: () -> Unit) {
        @JavascriptInterface
        fun requestExport() = onRequest()
    }
}
