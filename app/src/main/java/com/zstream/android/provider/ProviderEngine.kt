package com.zstream.android.provider

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "ProviderEngine"

@Singleton
class ProviderEngine @Inject constructor() {

    private lateinit var activity: android.app.Activity
    private var activeWebView: WebView? = null
    val proxy = WebViewProxyServer().also { it.start() }

    fun init(activity: android.app.Activity) {
        this.activity = activity
    }

    fun getActiveWebView(): WebView? = activeWebView

    suspend fun runAll(mediaInput: Map<String, Any>, onEvent: (JSONObject) -> Unit): JSONObject {
        val type = mediaInput["type"] as? String ?: "movie"
        val tmdbId = mediaInput["tmdbId"] as? String ?: return JSONObject("""{"ok":false,"error":"missing tmdbId"}""")
        val season = (mediaInput["season"] as? Map<*, *>)?.get("number")
        val episode = (mediaInput["episode"] as? Map<*, *>)?.get("number")

        val embedUrl = if (type == "show" && season != null && episode != null)
            "https://vidlink.pro/tv/$tmdbId/$season/$episode"
        else
            "https://vidlink.pro/movie/$tmdbId"

        Log.d(TAG, "loading embed: $embedUrl")
        onEvent(JSONObject("""{"event":"init","sourceIds":["vidlink"]}"""))
        onEvent(JSONObject("""{"event":"start","id":"vidlink"}"""))

        return withTimeout(60_000L) {
            suspendCancellableCoroutine { cont ->
                var resolved = false
                var webView: WebView? = null

                fun resolve(result: JSONObject) {
                    if (resolved) return
                    resolved = true
                    // Don't destroy — keep webView alive for playback
                    if (cont.isActive) cont.resume(result)
                }

                activity.runOnUiThread {
                    webView = createWebView { url ->
                        Log.d(TAG, "intercepted: $url")
                        val streamUrl = extractStreamUrl(url) ?: return@createWebView false
                        Log.d(TAG, "stream found: $streamUrl")
                        activeWebView = webView
                        resolve(JSONObject("""{"ok":true,"data":{"sourceId":"vidlink","stream":{"type":"hls","playlist":"$streamUrl","captions":[]}}}"""))
                        true
                    }.also { wv ->
                        activity.window.decorView.let { root ->
                            (root as android.view.ViewGroup).addView(wv, android.view.ViewGroup.LayoutParams(1, 1))
                        }
                        wv.loadUrl(embedUrl)
                    }

                    cont.invokeOnCancellation { activity.runOnUiThread { webView?.destroy() } }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(onStream: (String) -> Boolean): WebView {
        return WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    onStream(request.url.toString())
                    return null
                }
            }
        }
    }

    private fun extractStreamUrl(url: String): String? {
        if (url.contains("127.0.0.1")) return null
        if (url.contains(".m3u8")) return url
        if (url.contains(".mp4") && !url.contains("thumbnail") && !url.contains("poster")) return url
        return null
    }
}
