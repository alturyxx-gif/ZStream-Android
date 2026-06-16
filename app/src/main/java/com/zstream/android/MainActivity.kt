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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
    private lateinit var systemUi: SystemUiController
    private lateinit var downloader: DownloadHandler
    private lateinit var storage: LocalStorageManager
    private lateinit var interceptor: ExtensionInterceptor

    private var fullscreenContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private var pendingExportJson: String? = null
    private val exportRequestCode = 1001
    private val blobDownloadRequestCode = 1002

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val polyfill by lazy {
        assets.open("bridge_polyfill.js").bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instantiate and immediately destroy a WebView to warm up the renderer process
        // before setContentView, reducing first-load latency
        WebView(applicationContext).also { it.destroy() }

        prefetchDns()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        systemUi = SystemUiController(window)
        downloader = DownloadHandler(this, webView)
        storage = LocalStorageManager(webView)
        bridge = ExtensionBridge(webView, lifecycleScope)
        interceptor = ExtensionInterceptor(httpClient, bridge)

        configureWebView()
        setupMediaSession()
        registerJsBridges()
        setupBackHandler()
        setupSwipeRefresh()

        webView.webChromeClient = buildChromeClient()
        webView.webViewClient = buildWebViewClient()
        webView.setDownloadListener { url, ua, cd, mime, _ -> downloader.handleDownload(url, ua, cd, mime) }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            if (webView.url.isNullOrEmpty()) loadLastUrlOrSite()
        } else {
            handleIntent(intent) ?: loadLastUrlOrSite()
        }
    }

    private fun prefetchDns() {
        lifecycleScope.launch(Dispatchers.IO) {
            try { java.net.InetAddress.getByName(SITE_HOST) } catch (_: Exception) {}
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = true
        }
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE))
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION))
            WebSettingsCompat.setWebAuthenticationSupport(webView.settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ZStreamMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { webView.evaluateJavascript("document.querySelector('video')?.play()", null) }
                override fun onPause() { webView.evaluateJavascript("document.querySelector('video')?.pause()", null) }
                override fun onSeekTo(pos: Long) {
                    webView.evaluateJavascript("document.querySelector('video').currentTime=${pos / 1000.0}", null)
                }
            })
            isActive = true
        }
        MediaPlaybackService.mediaSession = mediaSession
    }

    private fun registerJsBridges() {
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.addJavascriptInterface(PipBridge(), "AndroidPip")
        webView.addJavascriptInterface(MediaBridge(), "AndroidMedia")
        webView.addJavascriptInterface(ZoomBridge(), "AndroidZoom")
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun go() = runOnUiThread { loadSiteOrError() }
        }, "AndroidRetry")
        webView.addJavascriptInterface(downloader.BlobReceiverBridge { bytes, mimeType, cd ->
            downloader.pendingBlobBytes = bytes
            runOnUiThread { startActivityForResult(downloader.createSaveIntent(mimeType, cd), blobDownloadRequestCode) }
        }, "AndroidDownload")
        webView.addJavascriptInterface(storage.ExportBridge {
            if (storage.isEmpty()) {
                webView.evaluateJavascript("window.__exportResult('empty')", null)
                return@ExportBridge
            }
            pendingExportJson = storage.snapshot
            runOnUiThread { startActivityForResult(storage.createExportIntent(), exportRequestCode) }
        }, "AndroidDataExport")
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = when {
                customView != null -> hideCustomView()
                webView.canGoBack() -> webView.goBack()
                else -> finish()
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            vibrate()
            loadSiteOrError()
        }
    }

    private fun buildChromeClient() = object : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
            swipeRefresh.isEnabled = false
            fullscreenContainer = FrameLayout(this@MainActivity).also {
                it.addView(view, FrameLayout.LayoutParams(-1, -1))
                (window.decorView as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
            }
            systemUi.hideForFullscreen()
        }

        override fun onHideCustomView() = hideCustomView()
    }

    private fun buildWebViewClient() = object : WebViewClient() {
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
                view.postDelayed({ storage.takeSnapshot() }, 3000)
                if (!isOnline()) view.loadUrl("file:///android_asset/error.html")
            }
        }

        override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) view.loadUrl("file:///android_asset/error.html")
        }

        // Rebuild the WebView in-place if the renderer crashes
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (!detail.didCrash()) return false
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

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
            interceptor.intercept(request)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): String? {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Regex("https?://\\S+").find(it)?.value }
            else -> null
        } ?: return null
        return if (url.contains(SITE_HOST)) {
            if (isOnline()) webView.loadUrl(url) else webView.loadUrl("file:///android_asset/error.html")
            url
        } else null
    }

    // Periodic localStorage snapshot while the app is in the foreground
    private val snapshotHandler = Handler(Looper.getMainLooper())
    private val snapshotRunnable = object : Runnable {
        override fun run() {
            if (webView.url?.contains(SITE_HOST) == true) storage.takeSnapshot()
            snapshotHandler.postDelayed(this, 60_000)
        }
    }

    override fun onPause() {
        super.onPause()
        snapshotHandler.removeCallbacks(snapshotRunnable)
        if (webView.url?.contains(SITE_HOST) == true) {
            storage.takeSnapshot()
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
        // Prevent WebView from pausing timers/media when the activity is fully obscured
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
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
                } catch (_: Exception) {
                    webView.evaluateJavascript("window.__exportResult('error')", null)
                }
            }
            blobDownloadRequestCode -> {
                val bytes = downloader.pendingBlobBytes ?: return
                downloader.pendingBlobBytes = null
                try { contentResolver.openOutputStream(data.data!!)?.use { it.write(bytes) } } catch (_: Exception) {}
            }
        }
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
        systemUi.show()
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun loadSiteOrError() {
        if (isOnline()) webView.loadUrl("$SITE_URL/") else webView.loadUrl("file:///android_asset/error.html")
    }

    private fun loadLastUrlOrSite() {
        if (!isOnline()) { webView.loadUrl("file:///android_asset/error.html"); return }
        val last = getSharedPreferences("zstream", MODE_PRIVATE).getString("last_url", null)
        webView.loadUrl(if (!last.isNullOrEmpty() && last.startsWith(SITE_URL)) last else "$SITE_URL/")
    }

    private fun saveCurrentUrl() {
        val url = webView.url ?: return
        if (url.startsWith(SITE_URL))
            getSharedPreferences("zstream", MODE_PRIVATE).edit().putString("last_url", url).apply()
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

    inner class ZoomBridge {
        private val prefs get() = getSharedPreferences("zstream", MODE_PRIVATE)
        @JavascriptInterface fun getZoom(): Int = prefs.getInt("zoom", 100)
        @JavascriptInterface fun setZoom(percent: Int) = prefs.edit().putInt("zoom", percent.coerceIn(50, 150)).apply()
        @JavascriptInterface fun isHidden(): Boolean = prefs.getBoolean("zoom_hidden", false)
        @JavascriptInterface fun setHidden(hidden: Boolean) = prefs.edit().putBoolean("zoom_hidden", hidden).apply()
    }

    inner class PipBridge {
        @JavascriptInterface
        fun enter(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val aspectRatio = Rational(if (width > 0) width else 16, if (height > 0) height else 9)
            runOnUiThread {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build())
            }
        }
    }

    inner class MediaBridge {
        @JavascriptInterface
        fun setSwipeRefresh(enabled: Boolean) = runOnUiThread {
            if (enabled && customView == null) swipeRefresh.isEnabled = true
            else if (!enabled) swipeRefresh.isEnabled = false
        }

        @JavascriptInterface
        fun updateState(playing: Boolean, title: String, duration: Long, position: Long) {
            updateMediaSession(playing, title, duration, position)
            runOnUiThread {
                // Keep screen on only while video is actively playing
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val intent = Intent(this@MainActivity, MediaPlaybackService::class.java)
                    .putExtra("playing", playing)
                    .putExtra("title", title.ifEmpty { "ZStream" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            }
        }

        private fun updateMediaSession(playing: Boolean, title: String, duration: Long, position: Long) {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        position,
                        if (playing) 1f else 0f,
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
        }
    }
}
