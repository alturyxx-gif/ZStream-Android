package com.zstream.android.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import kotlin.math.min
import kotlin.math.roundToInt

// TV rows are wide enough to fit the ad at native size, but that reads as oversized on a
// living-room screen -- cap TV at half scale instead of fitting to the full row width.
private const val TV_MAX_SCALE = 0.6f

private const val AD_BASE_URL = "https://zstream.mov/"
private const val AD_SCRIPT_URL = "https://aqle3.com/btag.min.js"

// The creative itself is a fixed 728x90 zone -- the ad exchange serves that exact size, so we
// can't ask for a smaller one. Instead the WebView renders it at native size and a CSS transform
// scales it to whatever's actually available. Width and height scale independently (never a
// uniform scale-then-crop): a wider-than-container view forces the AndroidView itself to overflow
// its bounds, and clipping a hardware-accelerated WebView surface at the Compose layer
// (clipToBounds/Modifier.clip) is a known native RenderThread crash on some GPU drivers, so the
// banner is deliberately always sized to fit exactly -- no clip ever needed.
private fun adHtml(zoneId: String, width: Int, height: Int, scaleX: Float, scaleY: Float): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <style>
            html, body {
                margin: 0; padding: 0; background: transparent;
                width: 100%; height: 100%; overflow: hidden;
            }
            /* Fixed-size clip window decoupled from document flow: whatever the ad tag injects
               into #ad-wrap (iframes, companion nodes, async resizes) is clipped against this
               box's own bounds rather than against body's scroll extent, so it can never push the
               page's scrollable content taller and leave the WebView showing a scrolled-down,
               bottom-clipped view. */
            #stage {
                position: fixed; top: 0; left: 0;
                width: ${(width * scaleX).roundToInt()}px; height: ${(height * scaleY).roundToInt()}px;
                overflow: hidden;
                display: flex; align-items: center; justify-content: center;
            }
            #ad-wrap {
                width: ${width}px; height: ${(height * scaleY).roundToInt()}px;
                display: flex; align-items: center; justify-content: center;
                overflow: hidden;
            }
        </style>
    </head>
    <body>
        <div id="stage">
            <div id="ad-wrap">
                <script async data-cfasync="false" data-size="${width}x${height}" data-category="common"
                    data-id="dl-banner-${width}x${height}" data-zone="$zoneId" src="$AD_SCRIPT_URL"></script>
            </div>
        </div>
    </body>
    </html>
""".trimIndent()

/**
 * Renders the aqle3 btag ad banner in a locked-down WebView, scaled to fit [modifier]'s width.
 *
 * On TV, the WebView itself is neither focusable nor clickable -- D-pad focus stays on the outer
 * Compose wrapper, so the ad can be scrolled past and highlighted without any real touch/click
 * ever reaching the page. Pressing select while focused synthesizes a single tap at the banner's
 * center instead (see [dispatchCenterTap]), so the ad's own click-out still fires and gets routed
 * to the system browser by [buildAdWebView]'s WebViewClient/WebChromeClient -- it's just never
 * reachable by accident.
 */
@Composable
fun AdBannerWebView(
    zoneId: String,
    theme: ZStreamTheme,
    modifier: Modifier = Modifier,
    width: Int = 728,
    height: Int = 90,
    isTv: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val webView: @Composable (scaleX: Float, scaleY: Float, displayWidth: Dp, displayHeight: Dp) -> Unit = { scaleX, scaleY, displayWidth, displayHeight ->
        AndroidView(
            modifier = Modifier
                .width(displayWidth)
                .height(displayHeight)
                .then(
                    if (isTv) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) {
                                    webViewRef?.let { dispatchCenterTap(it) }
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            factory = { ctx -> buildAdWebView(ctx, isTv).also { webViewRef = it } },
            update = { view ->
                view.loadDataWithBaseURL(AD_BASE_URL, adHtml(zoneId, width, height, scaleX, scaleY), "text/html", "utf-8", null)
            },
            onRelease = { view ->
                view.stopLoading()
                view.webViewClient = WebViewClient()
                view.webChromeClient = null
                view.loadUrl("about:blank")
                view.clearHistory()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                webViewRef = null
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Fit the fixed-ratio 728x90 creative exactly; the tag adjusts its iframe to this same
        // aspect ratio asynchronously, so the WebView must not reserve a taller host around it.
        val maxScale = if (isTv) TV_MAX_SCALE else 1f
        val scaleX = min(maxScale, maxWidth / width.dp)
        val displayWidth = (width * scaleX).dp
        val rawHeight = (height * scaleX).dp
        val displayHeight = rawHeight
        val scaleY = displayHeight / height.dp

        val sized: @Composable () -> Unit = {
            Box(
                modifier = Modifier.width(displayWidth).height(displayHeight),
                contentAlignment = Alignment.Center,
            ) {
                webView(scaleX, scaleY, displayWidth, displayHeight)
            }
        }

        if (isTv) {
            ZsOutlinedWrapper(
                visible = focused,
                outlineColor = theme.colors.global.accentA.copy(alpha = 0.6f),
                gap = 3.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.wrapContentSize(),
            ) { sized() }
        } else {
            sized()
        }
    }
}

/** Synthesizes a single tap at the view's center so the ad's own click-out handler runs. */
private fun dispatchCenterTap(webView: WebView) {
    if (webView.width == 0 || webView.height == 0) return
    val x = webView.width / 2f
    val y = webView.height / 2f
    val downTime = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
    webView.dispatchTouchEvent(down)
    webView.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
}

private fun openExternally(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildAdWebView(context: Context, isTv: Boolean): WebView {
    val webView = WebView(context)

    // Not part of the normal focus/key-click chain on TV -- D-pad focus stays on the outer
    // Compose wrapper. Selecting it dispatches a synthetic tap (see dispatchCenterTap) instead of
    // Android's default DPAD_CENTER-to-performClick() mapping, which requires isClickable=true.
    webView.isFocusable = !isTv
    webView.isFocusableInTouchMode = !isTv
    webView.isClickable = !isTv

    // The page is always sized to exactly fill the WebView (see #stage in adHtml) -- any
    // scrolling would just be the container drifting off its top-left origin, showing the ad
    // clipped/cut off at the bottom. Lock it down entirely.
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER

    webView.setBackgroundColor(Color.TRANSPARENT)
    // A small always-embedded WebView doesn't need GPU compositing, and running it in software
    // sidesteps a class of native RenderThread crashes some GPU drivers hit when a hardware
    // surface is embedded inside a Compose layer tree.
    webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

    webView.settings.apply {
        javaScriptEnabled = true
        // btag.min.js needs DOM storage to render; nothing below needs file/db/content access.
        domStorageEnabled = true
        // We already size the ad explicitly via CSS and the Compose container. Overview mode adds
        // a second fit-to-screen pass that can shrink the creative again, which shows up as a tiny
        // banner floating inside the correctly sized slot on the home screen.
        loadWithOverviewMode = false
        useWideViewPort = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = true

        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        databaseEnabled = false

        setSupportMultipleWindows(true) // needed so onCreateWindow below sees target=_blank click-outs
        javaScriptCanOpenWindowsAutomatically = false
    }

    // No addJavascriptInterface -- nothing here needs a JS<->native bridge, and it's the
    // classic WebView RCE vector.

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, false)
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    // Click-throughs open in the real browser, never navigate the embedded WebView.
                    openExternally(context, uri.toString())
                    true
                }
                // intent://, market://, and other custom schemes are the classic
                // Play-Store-hijack vector -- drop them silently.
                else -> true
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            // A bad ad payload crashing the renderer shouldn't take the whole app down.
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            return true
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            // Only follow through on a real tap (ours, synthetic-but-gesture-flagged, or a
            // genuine phone touch) -- an unsolicited JS-triggered popup has isUserGesture=false
            // and is dropped. For a real gesture, capture the popup's destination URL via a
            // throwaway transport WebView and open it externally instead of letting a second
            // WebView window actually open.
            if (!isUserGesture || resultMsg == null) return false
            val transport = WebView(context)
            transport.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    openExternally(context, request.url.toString())
                    return true
                }
            }
            @Suppress("UNCHECKED_CAST")
            val transportObj = resultMsg.obj as? WebView.WebViewTransport
            transportObj?.webView = transport
            resultMsg.sendToTarget()
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }
    }

    return webView
}
