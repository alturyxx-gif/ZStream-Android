package com.zstream.android

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request

private val HEADERS_TO_STRIP = setOf(
    "content-security-policy", "content-security-policy-report-only",
    "access-control-allow-origin", "access-control-allow-methods",
    "access-control-allow-headers", "access-control-allow-credentials",
    "x-frame-options",
)

class ExtensionInterceptor(private val httpClient: OkHttpClient, private val bridge: ExtensionBridge) {

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!url.startsWith("http")) return null
        val hostname = request.url.host ?: return null
        val rule = bridge.activeRules[hostname] ?: return null
        return try {
            val response = httpClient.newCall(buildRequest(url, request, rule)).execute()
            buildResponse(response, rule)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequest(url: String, request: WebResourceRequest, rule: org.json.JSONObject): Request {
        val builder = Request.Builder().url(url)
        request.requestHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        rule.optJSONObject("requestHeaders")?.keys()?.forEach { k ->
            builder.header(k, rule.optJSONObject("requestHeaders")!!.getString(k))
        }
        builder.method(request.method ?: "GET", null)
        return builder.build()
    }

    private fun buildResponse(response: okhttp3.Response, rule: org.json.JSONObject): WebResourceResponse {
        val contentType = response.header("content-type", "application/octet-stream")
        val headers = response.headers.toMultimap()
            .filterKeys { it.lowercase() !in HEADERS_TO_STRIP }
            .mapValues { it.value.firstOrNull() ?: "" }
            .toMutableMap()
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, PATCH, OPTIONS"
        headers["Access-Control-Allow-Headers"] = "*"
        rule.optJSONObject("responseHeaders")?.keys()?.forEach { k ->
            headers[k] = rule.optJSONObject("responseHeaders")!!.getString(k)
        }
        return WebResourceResponse(
            contentType?.substringBefore(";")?.trim() ?: "application/octet-stream",
            contentType?.substringAfter("charset=", "")?.trim()?.ifEmpty { null },
            response.code,
            response.message.ifEmpty { "OK" },
            headers,
            response.body?.byteStream(),
        )
    }
}
