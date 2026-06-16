package com.zstream.android

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ExtensionBridge(
    private val webView: WebView,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Domains registered via prepareStream that need header modification
    // key: hostname, value: rule body (requestHeaders, responseHeaders)
    val activeRules = ConcurrentHashMap<String, JSONObject>()

    @JavascriptInterface
    fun postMessage(json: String) {
        scope.launch(Dispatchers.IO) {
            val msg = JSONObject(json)
            val id = msg.getString("id")
            val name = msg.getString("name")
            val body = msg.optJSONObject("body")

            val result = try {
                when (name) {
                    "hello" -> handleHello()
                    "makeRequest" -> handleMakeRequest(body)
                    "prepareStream" -> handlePrepareStream(body)
                    "openPage" -> JSONObject("""{"success":true}""")
                    else -> JSONObject("""{"success":false,"error":"unknown message: $name"}""")
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }
            }

            // Escape for JS single-quoted string
            val escaped = result.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val js = "window.__bridgeResolve('$id', '$escaped');"
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun handleHello(): JSONObject = JSONObject().apply {
        put("success", true)
        put("version", "1.0.0")
        put("allowed", true)
        put("hasPermission", true)
    }

    private fun handleMakeRequest(body: JSONObject?): JSONObject {
        if (body == null) return JSONObject("""{"success":false,"error":"No body"}""")

        val baseUrl = body.optString("baseUrl", "")
        val path = body.optString("url", "")
        val fullUrl = buildUrl(path, baseUrl, body.optJSONObject("query"))
        val method = body.optString("method", "GET").uppercase()
        val headersJson = body.optJSONObject("headers")
        val requestBodyRaw = body.opt("body")
        val bodyType = body.optString("bodyType", "")

        val reqBody = when {
            requestBodyRaw == null || method == "GET" || method == "HEAD" -> null
            bodyType == "object" -> requestBodyRaw.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            bodyType == "URLSearchParams" -> requestBodyRaw.toString()
                .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

            else -> requestBodyRaw.toString().toRequestBody(null)
        }

        val reqBuilder = Request.Builder().url(fullUrl)
        headersJson?.keys()?.forEach { key ->
            reqBuilder.addHeader(key, headersJson.getString(key))
        }
        reqBuilder.method(method, reqBody)

        val response = client.newCall(reqBuilder.build()).execute()
        val contentType = response.header("content-type", "")
        val responseBodyStr = response.body?.string() ?: ""

        val responseBody: Any = if (contentType?.contains("application/json") == true) {
            try {
                JSONObject(responseBodyStr)
            } catch (e: Exception) {
                responseBodyStr
            }
        } else {
            responseBodyStr
        }

        val headersOut = JSONObject()
        response.headers.names().forEach { name ->
            headersOut.put(name.lowercase(), response.header(name) ?: "")
        }

        return JSONObject().apply {
            put("success", true)
            put("response", JSONObject().apply {
                put("statusCode", response.code)
                put("headers", headersOut)
                put("body", responseBody)
                put("finalUrl", response.request.url.toString())
            })
        }
    }

    private fun handlePrepareStream(body: JSONObject?): JSONObject {
        if (body == null) return JSONObject("""{"success":false,"error":"No body"}""")

        // Register each target domain so shouldInterceptRequest can modify its responses
        val targetDomains = body.optJSONArray("targetDomains")
        if (targetDomains != null) {
            for (i in 0 until targetDomains.length()) {
                val domain = targetDomains.getString(i)
                activeRules[domain] = body
            }
        }

        return JSONObject("""{"success":true}""")
    }

    private fun buildUrl(path: String, baseUrl: String, query: JSONObject?): String {
        val base = if (baseUrl.isNotEmpty()) {
            val b = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val p = if (path.startsWith("/")) path.substring(1) else path
            b + p
        } else path

        if (query == null || query.length() == 0) return base
        val params = query.keys().asSequence()
            .map { "$it=${query.getString(it)}" }
            .joinToString("&")
        return if (base.contains("?")) "$base&$params" else "$base?$params"
    }
}
