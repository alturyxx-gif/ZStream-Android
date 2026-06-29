package com.zstream.android.provider

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

private const val TAG = "StreamExtractor"

/**
 * Provider-agnostic stream discovery.
 *
 * Loads a [StreamSource]'s embed page in a hidden 1×1 WebView, lets the page's JavaScript
 * run, and captures the first request the source recognises as the stream (via
 * [StreamSource.isStreamUrl]). As soon as the URL is captured the page is torn down
 * (about:blank) so the embedded player stops downloading the stream and running ads /
 * trackers — playback then happens entirely through the native [WebViewProxyServer].
 *
 * Why a WebView at all: the signed stream URL only exists after the provider's obfuscated
 * JavaScript computes it. We run that JS in the WebView purely as a discovery step; the
 * WebView plays no part in actual playback.
 */
class StreamExtractor(private val activity: Activity) {

    /**
     * Set during [extract] when the WebView makes a request to the vidlink API
     * (pattern: vidlink.pro/api/b/…). Contains the full API URL with the encrypted
     * TMDB ID, ready to be fetched by [ProviderEngine] to obtain caption data.
     */
    @Volatile
    var vidlinkApiUrl: String? = null
        private set

    /**
     * @return the discovered stream URL, or null if none was found within [timeoutMs].
     */
    suspend fun extract(source: StreamSource, media: MediaRequest, timeoutMs: Long = 60_000L): String? {
        val embedUrl = source.embedUrl(media)
        Log.d(TAG, "[${source.id}] loading embed: $embedUrl")
        vidlinkApiUrl = null

        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var resolved = false
                var webView: WebView? = null

                fun finish(url: String?) {
                    if (resolved) return
                    resolved = true
                    webView?.let { wv -> 
                        activity.runOnUiThread { 
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            (activity.window.decorView as ViewGroup).removeView(wv)
                            wv.destroy()
                        } 
                    }
                    if (cont.isActive) cont.resume(url)
                }

                activity.runOnUiThread {
                    webView = createWebView { url ->
                        Log.d(TAG, "[${source.id}] request: $url")
                        if (url.contains("vidlink.pro/api/b/")) {
                            vidlinkApiUrl = url
                            Log.d(TAG, "[${source.id}] captured vidlink API URL: $url")
                        }
                        if (!source.isStreamUrl(url)) return@createWebView false
                        Log.d(TAG, "[${source.id}] stream found: $url")
                        finish(url)
                        true
                    }.also { wv ->
                        val parent = activity.window.decorView as ViewGroup
                        parent.addView(wv, ViewGroup.LayoutParams(1, 1))
                        wv.loadUrl(embedUrl)
                    }
                    cont.invokeOnCancellation { 
                        activity.runOnUiThread { 
                            val parent = activity.window.decorView as ViewGroup
                            webView?.let { wv ->
                                parent.removeView(wv)
                                wv.destroy()
                            }
                        } 
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(onRequest: (String) -> Boolean): WebView = WebView(activity).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                onRequest(request.url.toString())   // observe only; never block during discovery
                return null
            }
        }
    }
}
