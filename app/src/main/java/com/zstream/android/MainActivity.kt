package com.zstream.android

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        const val SITE_HOST = "zstream.mov"
        const val SITE_URL = "https://zstream.mov"
    }

    private lateinit var webView: WebView
    private lateinit var bridge: ExtensionBridge
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var mediaSession: MediaSessionCompat

    private var fullscreenContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val headersToStrip = setOf(
        "content-security-policy", "content-security-policy-report-only",
        "access-control-allow-origin", "access-control-allow-methods",
        "access-control-allow-headers", "access-control-allow-credentials",
        "x-frame-options"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // (1) PREWARM: instantiate WebView before setContentView to warm up the renderer
        WebView(applicationContext).also { it.destroy() }
        // DNS prefetch for zstream.mov
        lifecycleScope.launch(Dispatchers.IO) {
            try { java.net.InetAddress.getByName(SITE_HOST) } catch (_: Exception) {}
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // (2) CACHING: use cache-first, allocate 50 MB app cache
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = true
        }
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        // Safe browsing opt-out (site is legitimate, avoids false-positive interstitials)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE))
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, false)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            )
        }

        // (7) MEDIA SESSION setup — shared with MediaPlaybackService for lock screen controls
        mediaSession = MediaSessionCompat(this, "ZStreamMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                     MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { webView.evaluateJavascript("document.querySelector('video')?.play()", null) }
                override fun onPause() { webView.evaluateJavascript("document.querySelector('video')?.pause()", null) }
                override fun onSeekTo(pos: Long) {
                    webView.evaluateJavascript("document.querySelector('video').currentTime=${pos/1000.0}", null)
                }
            })
            isActive = true
        }
        MediaPlaybackService.mediaSession = mediaSession

        bridge = ExtensionBridge(webView, lifecycleScope)
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.addJavascriptInterface(PipBridge(), "AndroidPip")
        webView.addJavascriptInterface(DataExportBridge(), "AndroidDataExport")
        webView.addJavascriptInterface(MediaBridge(), "AndroidMedia")
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun go() = runOnUiThread { loadSiteOrError() }
        }, "AndroidRetry")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        // (6) HAPTIC: vibrate when pull-to-refresh is triggered
        swipeRefresh.setOnRefreshListener {
            vibrate()
            loadSiteOrError()
        }

        val polyfill by lazy {
            assets.open("bridge_polyfill.js").bufferedReader().use { it.readText() }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }

            override fun onConsoleMessage(msg: ConsoleMessage) = true

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                originalOrientation = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                // (5) DISABLE swipe-to-refresh when fullscreen video is showing
                swipeRefresh.isEnabled = false
                fullscreenContainer = FrameLayout(this@MainActivity).also {
                    it.addView(view, FrameLayout.LayoutParams(-1, -1))
                    (window.decorView as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
                }
                hideSystemUI()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:")) {
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
            } else {
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                    )
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
            }
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun receiveBlob(dataUrl: String, mimeType: String, contentDisposition: String) {
                val bytes = android.util.Base64.decode(dataUrl.substringAfter(","), android.util.Base64.DEFAULT)
                val fileName = android.webkit.URLUtil.guessFileName("", contentDisposition, mimeType)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType.ifEmpty { "application/octet-stream" }
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                pendingBlobBytes = bytes
                runOnUiThread { startActivityForResult(intent, blobDownloadRequestCode) }
            }
        }, "AndroidDownload")

        // (3) CRASH RECOVERY: handle WebView renderer crash
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url == "about:blank") return
                view.evaluateJavascript(polyfill, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == "about:blank") return
                view.evaluateJavascript(polyfill, null)
                swipeRefresh.isRefreshing = false
                if (url.contains(SITE_HOST)) {
                    saveCurrentUrl()
                    view.postDelayed({ snapshotLocalStorage(view) }, 3000)
                    if (!isOnline()) view.loadUrl("file:///android_asset/error.html")
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) view.loadUrl("file:///android_asset/error.html")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!detail.didCrash()) return false
                // Renderer crashed — rebuild the WebView and reload
                (view.parent as? android.view.ViewGroup)?.let { parent ->
                    val index = parent.indexOfChild(view)
                    parent.removeView(view)
                    view.destroy()
                    webView = WebView(this@MainActivity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        layoutParams = view.layoutParams
                    }
                    parent.addView(webView, index)
                    loadSiteOrError()
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith("http")) return null
                val hostname = request.url.host ?: return null
                val rule = bridge.activeRules[hostname] ?: return null
                return try {
                    val reqBuilder = Request.Builder().url(url)
                    request.requestHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                    rule.optJSONObject("requestHeaders")?.keys()?.forEach { k ->
                        reqBuilder.header(k, rule.optJSONObject("requestHeaders")!!.getString(k))
                    }
                    reqBuilder.method(request.method ?: "GET", null)
                    val response = httpClient.newCall(reqBuilder.build()).execute()
                    val contentType = response.header("content-type", "application/octet-stream")
                    val filteredHeaders = response.headers.toMultimap()
                        .filterKeys { it.lowercase() !in headersToStrip }
                        .mapValues { it.value.firstOrNull() ?: "" }
                        .toMutableMap()
                    filteredHeaders["Access-Control-Allow-Origin"] = "*"
                    filteredHeaders["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, PATCH, OPTIONS"
                    filteredHeaders["Access-Control-Allow-Headers"] = "*"
                    rule.optJSONObject("responseHeaders")?.keys()?.forEach { k ->
                        filteredHeaders[k] = rule.optJSONObject("responseHeaders")!!.getString(k)
                    }
                    WebResourceResponse(
                        contentType?.substringBefore(";")?.trim() ?: "application/octet-stream",
                        contentType?.substringAfter("charset=", "")?.trim()?.ifEmpty { null },
                        response.code, response.message.ifEmpty { "OK" },
                        filteredHeaders, response.body?.byteStream()
                    )
                } catch (e: Exception) { null }
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            if (webView.url.isNullOrEmpty()) loadLastUrlOrSite()
        } else {
            handleIntent(intent) ?: loadLastUrlOrSite()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Returns the URL loaded if the intent had one, null otherwise
    private fun handleIntent(intent: Intent?): String? {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Regex("https?://\\S+").find(it)?.value }
            else -> null
        } ?: return null
        // Only handle zstream.mov URLs; open others in browser
        return if (url.contains(SITE_HOST)) {
            if (isOnline()) webView.loadUrl(url) else webView.loadUrl("file:///android_asset/error.html")
            url
        } else null
    }

    private val snapshotHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val snapshotRunnable = object : Runnable {
        override fun run() {
            val url = webView.url ?: ""
            if (url.contains(SITE_HOST)) snapshotLocalStorage(webView)
            snapshotHandler.postDelayed(this, 60_000)
        }
    }

    override fun onPause() {
        super.onPause()
        snapshotHandler.removeCallbacks(snapshotRunnable)
        val url = webView.url ?: ""
        if (url.contains(SITE_HOST)) {
            snapshotLocalStorage(webView)
            saveCurrentUrl()
        }
        webView.evaluateJavascript("document.querySelector('video')?.pause()", null)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        snapshotHandler.postDelayed(snapshotRunnable, 60_000)
    }

    override fun onStop() {
        super.onStop()
        // Prevent WebView from pausing media when fully backgrounded
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadSiteOrError() {
        if (isOnline()) webView.loadUrl("$SITE_URL/")
        else webView.loadUrl("file:///android_asset/error.html")
    }

    private fun loadLastUrlOrSite() {
        if (!isOnline()) { webView.loadUrl("file:///android_asset/error.html"); return }
        val prefs = getSharedPreferences("zstream", MODE_PRIVATE)
        val last = prefs.getString("last_url", null)
        webView.loadUrl(if (!last.isNullOrEmpty() && last.startsWith(SITE_URL)) last else "$SITE_URL/")
    }

    private fun saveCurrentUrl() {
        val url = webView.url ?: return
        if (url.startsWith(SITE_URL)) {
            getSharedPreferences("zstream", MODE_PRIVATE).edit().putString("last_url", url).apply()
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                else it.vibrate(40)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        swipeRefresh.isEnabled = !isInPiP
        progressBar.visibility = View.GONE
    }

    private fun hideCustomView() {
        customView ?: return
        (window.decorView as FrameLayout).removeView(fullscreenContainer)
        fullscreenContainer = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = originalOrientation
        swipeRefresh.isEnabled = true
        showSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    inner class PipBridge {
        @JavascriptInterface
        fun enter(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val w = if (width > 0) width else 16
            val h = if (height > 0) height else 9
            runOnUiThread {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(w, h)).build()
                )
            }
        }
    }

    // (7) MEDIA SESSION bridge — JS calls these to update lock screen controls
    inner class MediaBridge {
        @JavascriptInterface
        fun setSwipeRefresh(enabled: Boolean) = runOnUiThread {
            if (enabled && customView == null) swipeRefresh.isEnabled = true
            else if (!enabled) swipeRefresh.isEnabled = false
        }

        @JavascriptInterface
        fun updateState(playing: Boolean, title: String, duration: Long, position: Long) {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        position, if (playing) 1f else 0f
                    )
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO)
                    .build()
            )
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.ifEmpty { "ZStream" })
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .build()
            )
            runOnUiThread {
                val intent = Intent(this@MainActivity, MediaPlaybackService::class.java)
                    .putExtra("playing", playing).putExtra("title", title.ifEmpty { "ZStream" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            }
        }
    }

    private var pendingExportJson: String? = null
    private var pendingBlobBytes: ByteArray? = null
    private var localStorageSnapshot: String? = null
    private val exportRequestCode = 1001
    private val blobDownloadRequestCode = 1002

    private fun snapshotLocalStorage(view: WebView) {
        val js = """
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
        view.evaluateJavascript(js) { result ->
            if (result != null && result.length > 2) {
                localStorageSnapshot = result.removePrefix("\"").removeSuffix("\"")
                    .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        when (requestCode) {
            exportRequestCode -> {
                val json = pendingExportJson ?: return
                pendingExportJson = null
                try {
                    contentResolver.openOutputStream(data.data!!)?.use { it.write(json.toByteArray()) }
                    webView.evaluateJavascript("window.__exportResult('done')", null)
                } catch (e: Exception) {
                    webView.evaluateJavascript("window.__exportResult('error')", null)
                }
            }
            blobDownloadRequestCode -> {
                val bytes = pendingBlobBytes ?: return
                pendingBlobBytes = null
                try { contentResolver.openOutputStream(data.data!!)?.use { it.write(bytes) } } catch (_: Exception) {}
            }
        }
    }

    inner class DataExportBridge {
        @JavascriptInterface
        fun requestExport() {
            val snapshot = localStorageSnapshot
            if (snapshot.isNullOrEmpty() || snapshot == "{}" ||
                (snapshot.contains("\"bookmarks\":{}") && snapshot.contains("\"progress\":{}") && snapshot.contains("\"watchHistory\":{}"))) {
                runOnUiThread { webView.evaluateJavascript("window.__exportResult('empty')", null) }
                return
            }
            pendingExportJson = snapshot
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "zstream-data-$date.json")
            }
            runOnUiThread { startActivityForResult(intent, exportRequestCode) }
        }
    }
}
