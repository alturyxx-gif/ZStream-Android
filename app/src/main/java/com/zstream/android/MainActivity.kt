package com.zstream.android

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var bridge: ExtensionBridge

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Headers the extension sets on stream source responses
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

        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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

        // Forward console.log/error to Logcat for debugging
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d("WebConsole", "[${msg.messageLevel()}] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }
        }

        val polyfill by lazy {
            assets.open("bridge_polyfill.js").bufferedReader().use { it.readText() }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                view.evaluateJavascript(polyfill, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(polyfill, null)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith("http")) return null

                val hostname = request.url.host ?: return null

                // Only intercept domains that were registered via prepareStream
                val rule = bridge.activeRules[hostname] ?: return null

                return try {
                    val reqBuilder = Request.Builder().url(url)
                    request.requestHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                    // Apply any request headers from the rule
                    val ruleRequestHeaders = rule.optJSONObject("requestHeaders")
                    ruleRequestHeaders?.keys()?.forEach { k ->
                        reqBuilder.header(k, ruleRequestHeaders.getString(k))
                    }

                    reqBuilder.method(request.method ?: "GET", null)

                    val response = httpClient.newCall(reqBuilder.build()).execute()
                    val contentType = response.header("content-type", "application/octet-stream")

                    // Strip restricted headers, keep the rest
                    val filteredHeaders = response.headers.toMultimap()
                        .filterKeys { it.lowercase() !in headersToStrip }
                        .mapValues { it.value.firstOrNull() ?: "" }
                        .toMutableMap()

                    // Apply CORS headers
                    filteredHeaders["Access-Control-Allow-Origin"] = "*"
                    filteredHeaders["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, PATCH, OPTIONS"
                    filteredHeaders["Access-Control-Allow-Headers"] = "*"

                    // Apply any custom response headers from the rule
                    val ruleResponseHeaders = rule.optJSONObject("responseHeaders")
                    ruleResponseHeaders?.keys()?.forEach { k ->
                        filteredHeaders[k] = ruleResponseHeaders.getString(k)
                    }

                    val mimeType = contentType?.substringBefore(";")?.trim() ?: "application/octet-stream"
                    val encoding = contentType?.substringAfter("charset=", "")?.trim()?.ifEmpty { null }

                    WebResourceResponse(
                        mimeType,
                        encoding,
                        response.code,
                        response.message.ifEmpty { "OK" },
                        filteredHeaders,
                        response.body?.byteStream()
                    )
                } catch (e: Exception) {
                    Log.e("ExtBridge", "Failed to intercept $url: ${e.message}")
                    null
                }
            }
        }

        webView.loadUrl("https://zstream.mov/")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
