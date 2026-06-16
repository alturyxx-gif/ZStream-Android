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
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var bridge: ExtensionBridge
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    // Container for fullscreen video
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
        "content-security-policy",
        "content-security-policy-report-only",
        "access-control-allow-origin",
        "access-control-allow-methods",
        "access-control-allow-headers",
        "access-control-allow-credentials",
        "x-frame-options"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            )
        }

        bridge = ExtensionBridge(webView, lifecycleScope)
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.addJavascriptInterface(PipBridge(), "AndroidPip")
        webView.addJavascriptInterface(DataExportBridge(), "AndroidDataExport")
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun go() = runOnUiThread { loadSiteOrError() }
        }, "AndroidRetry")

        // Hardware back gesture (replaces deprecated onBackPressed)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        // Pull-to-refresh
        swipeRefresh.setOnRefreshListener {
            loadSiteOrError()
        }

        val polyfill by lazy {
            assets.open("bridge_polyfill.js").bufferedReader().use { it.readText() }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Loading progress bar
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }

            // Console logging for debugging
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d("WebConsole", "[${msg.messageLevel()}] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }

            // Landscape lock when video goes fullscreen
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                originalOrientation = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                fullscreenContainer = FrameLayout(this@MainActivity).also {
                    it.addView(view, FrameLayout.LayoutParams(-1, -1))
                    window.decorView.let { decor ->
                        (decor as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
                    }
                }
                hideSystemUI()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                view.evaluateJavascript(polyfill, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(polyfill, null)
                swipeRefresh.isRefreshing = false

                if (url.contains("zstream.mov")) {
                    // Delay snapshot so the site's JS has time to populate localStorage
                    view.postDelayed({ snapshotLocalStorage(view) }, 3000)
                    if (!isOnline()) view.loadUrl("file:///android_asset/error.html")
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                // Only show error page for the main frame (not sub-resources)
                if (request.isForMainFrame) {
                    view.loadUrl("file:///android_asset/error.html")
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith("http")) return null
                val hostname = request.url.host ?: return null
                val rule = bridge.activeRules[hostname] ?: return null

                return try {
                    val reqBuilder = Request.Builder().url(url)
                    request.requestHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                    val ruleRequestHeaders = rule.optJSONObject("requestHeaders")
                    ruleRequestHeaders?.keys()?.forEach { k ->
                        reqBuilder.header(k, ruleRequestHeaders.getString(k))
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

                    val mimeType = contentType?.substringBefore(";")?.trim() ?: "application/octet-stream"
                    val encoding = contentType?.substringAfter("charset=", "")?.trim()?.ifEmpty { null }

                    WebResourceResponse(
                        mimeType, encoding, response.code,
                        response.message.ifEmpty { "OK" },
                        filteredHeaders, response.body?.byteStream()
                    )
                } catch (e: Exception) {
                    Log.e("ExtBridge", "Intercept failed for $url: ${e.message}")
                    null
                }
            }
        }

        // Restore WebView state after rotation, otherwise load fresh
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            if (webView.url.isNullOrEmpty()) loadSiteOrError()
        } else {
            loadSiteOrError()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadSiteOrError() {
        if (isOnline()) webView.loadUrl("https://zstream.mov/")
        else webView.loadUrl("file:///android_asset/error.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // Hide/show UI when entering/leaving PiP
    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        swipeRefresh.isEnabled = !isInPiP
        progressBar.visibility = View.GONE
        if (!isInPiP) hideSystemUI()
    }

    private fun hideCustomView() {
        customView ?: return
        (window.decorView as FrameLayout).removeView(fullscreenContainer)
        fullscreenContainer = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = originalOrientation
        hideSystemUI()
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
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    inner class PipBridge {
        @JavascriptInterface
        fun enter(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val w = if (width > 0) width else 16
            val h = if (height > 0) height else 9
            runOnUiThread {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(w, h))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private var pendingExportJson: String? = null
    private var localStorageSnapshot: String? = null
    private val exportRequestCode = 1001

    private fun snapshotLocalStorage(view: WebView) {
        val js = """
            (function() {
                var data = {};
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    try { data[k] = JSON.parse(localStorage.getItem(k)); }
                    catch(e) { data[k] = localStorage.getItem(k); }
                }
                return JSON.stringify(data);
            })()
        """.trimIndent()
        view.evaluateJavascript(js) { result ->
            if (result != null && result.length > 2) {
                localStorageSnapshot = result.substring(1, result.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\/", "/")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportRequestCode) return
        val json = pendingExportJson ?: return
        pendingExportJson = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        try {
            contentResolver.openOutputStream(data.data!!)?.use { it.write(json.toByteArray()) }
            webView.evaluateJavascript("window.__exportResult('done')", null)
        } catch (e: Exception) {
            webView.evaluateJavascript("window.__exportResult('error')", null)
        }
    }

    inner class DataExportBridge {
        @JavascriptInterface
        fun requestExport() {
            val snapshot = localStorageSnapshot
            if (snapshot.isNullOrEmpty() || snapshot == "{}") {
                runOnUiThread {
                    webView.evaluateJavascript("window.__exportResult('empty')", null)
                }
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
