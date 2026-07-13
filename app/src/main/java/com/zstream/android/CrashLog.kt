package com.zstream.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CrashLog"

object CrashLog {
    private val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
    private val breadcrumbFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Ring buffer of recent lifecycle events -- a raw stack trace only tells us where the crash
    // landed, not what launch stage led up to it (plugin init, account/session load, home data
    // sync, etc). Bundling this trail into the crash report lets us diagnose "crashes a few
    // seconds after launch" bugs without needing a live logcat session on the user's device.
    private val breadcrumbs = ArrayDeque<String>()
    private const val MAX_BREADCRUMBS = 200

    fun breadcrumb(tag: String, message: String) {
        val line = "${breadcrumbFormatter.format(Date())} [$tag] $message"
        Log.d(TAG, line)
        synchronized(breadcrumbs) {
            breadcrumbs.addLast(line)
            while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        }
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
                .onFailure { Log.e(TAG, "Failed to write crash log", it) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = formatter.format(Date())
        val text = buildString {
            appendLine("ZStream crash log")
            appendLine("time=$timestamp")
            appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("thread=${thread.name}")
            appendLine()
            appendLine("-- recent events --")
            synchronized(breadcrumbs) { breadcrumbs.forEach { appendLine(it) } }
            appendLine()
            appendLine("-- stack trace --")
            append(stackTrace(throwable))
        }

        context.filesDir.resolve("logs").mkdirs()
        context.filesDir.resolve("logs/latest-crash.txt").writeText(text)
        writeDownloads(context, "ZStream-crash-$timestamp.txt", text)
    }

    private fun writeDownloads(context: Context, name: String, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, name).writeText(text)
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create Downloads crash log")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray())
        } ?: error("Failed to open Downloads crash log")
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
