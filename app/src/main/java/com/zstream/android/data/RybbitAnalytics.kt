package com.zstream.android.data

import com.google.gson.Gson
import com.zstream.android.BuildConfig
import com.zstream.android.CrashLog
import com.zstream.android.data.remote.RybbitApi
import com.zstream.android.data.remote.RybbitTrackRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class RybbitAnalytics @Inject constructor(
    private val api: RybbitApi,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        CrashLog.breadcrumb("Rybbit", "send failed: ${e.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val gson = Gson()
    private val siteId get() = BuildConfig.RYBBIT_SITE_ID

    fun trackScreen(pathname: String, title: String? = null) {
        if (siteId.isBlank()) return
        scope.launch {
            api.track(
                RybbitTrackRequest(
                    siteId = siteId,
                    type = "pageview",
                    pathname = pathname,
                    hostname = "android-app",
                    pageTitle = title,
                    language = Locale.getDefault().toLanguageTag(),
                )
            )
        }
    }

    fun trackEvent(name: String, properties: Map<String, Any?> = emptyMap()) {
        if (siteId.isBlank()) return
        scope.launch {
            api.track(
                RybbitTrackRequest(
                    siteId = siteId,
                    type = "custom_event",
                    eventName = name,
                    properties = if (properties.isEmpty()) null else gson.toJson(properties),
                    hostname = "android-app",
                    language = Locale.getDefault().toLanguageTag(),
                )
            )
        }
    }
}
