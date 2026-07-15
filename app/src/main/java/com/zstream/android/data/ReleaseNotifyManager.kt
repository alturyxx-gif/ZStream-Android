package com.zstream.android.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zstream.android.MainActivity
import com.zstream.android.R
import com.zstream.android.data.local.AppDatabase
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import com.zstream.android.data.model.hasAired
import com.zstream.android.data.model.hasReleased
import com.zstream.android.data.remote.TmdbApi
import com.zstream.android.di.TmdbTokenCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

const val OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA = "open_tracked_release_media_type"
const val OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA = "open_tracked_release_tmdb_id"

/**
 * Polls TMDB in the background for movies/episodes the user has flagged as "notify me when this
 * releases", firing a local notification once the release/air date has passed. There's no
 * server-side push involved -- the phone itself wakes up periodically (at a randomized interval,
 * self-chained via a WorkManager one-off request each run rather than a fixed PeriodicWorkRequest)
 * and re-checks TMDB directly, same as ReleaseUpdateManager does for GitHub releases.
 */
@Singleton
class ReleaseNotifyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() {
        createNotificationChannel()
        // KEEP: don't disturb an already-pending/running check just because the app was reopened,
        // otherwise an active user's timer keeps getting pushed out and never fires.
        val request = OneTimeWorkRequestBuilder<ReleaseNotifyWorker>()
            .setInitialDelay(randomDelayMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Used by the worker to re-arm itself after each run; REPLACE is correct here. */
    fun scheduleNext(delayMinutes: Long = randomDelayMinutes()) {
        val request = OneTimeWorkRequestBuilder<ReleaseNotifyWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Checks every tracked release against TMDB; notifies + untracks any that have gone live. */
    suspend fun checkNow() {
        val dao = AppDatabase.getInstance(context).trackedReleaseDao()
        val tracked = dao.getAllSync()
        if (tracked.isEmpty()) return
        val api = buildTmdbApi()
        for (entry in tracked) {
            val released = try {
                isReleased(api, entry)
            } catch (e: TimeoutCancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReleaseNotifyManager", "Check failed for ${entry.key}", e)
                null
            } ?: continue
            if (released && showReleaseNotification(entry)) {
                dao.deleteByKey(entry.key)
            }
        }
    }

    private suspend fun isReleased(api: TmdbApi, entry: TrackedReleaseEntity): Boolean {
        return if (entry.mediaType == "movie") {
            api.movieDetail(entry.tmdbId).hasReleased()
        } else {
            val season = entry.seasonNumber ?: return false
            val episode = entry.episodeNumber ?: return false
            val ep = api.season(entry.tmdbId, season).episodes
                .orEmpty()
                .firstOrNull { it.episodeNumber == episode }
            // Treat a missing air_date as "not yet known to have aired" here, unlike Episode.hasAired()'s
            // UI-facing default of true (which exists so unknown-date episodes aren't hidden from playback).
            ep?.airDate?.takeIf { it.isNotBlank() } != null && ep.hasAired()
        }
    }

    private fun buildTmdbApi(): TmdbApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val key = TmdbTokenCache.token ?: com.zstream.android.BuildConfig.TMDB_API_KEY
                val original = chain.request()
                val url = original.url.newBuilder().addQueryParameter("api_key", key).build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(com.zstream.android.Urls.TMDB_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(TmdbApi::class.java)
    }

    /** Returns true only if a notification was actually posted (so callers know it's safe to untrack). */
    private fun showReleaseNotification(entry: TrackedReleaseEntity): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA, entry.mediaType)
            putExtra(OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA, entry.tmdbId)
        }
        val notificationId = entry.key.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, message) = if (entry.mediaType == "movie") {
            "${entry.title} is out now" to "The movie you were waiting on just released."
        } else {
            "${entry.title} S${entry.seasonNumber}E${entry.episodeNumber} is out now" to
                (entry.episodeTitle?.takeIf { it.isNotBlank() } ?: "A new episode just aired.")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_group_megaphone)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Release notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies you when a movie or episode you're tracking releases"
            },
        )
    }

    companion object {
        private const val WORK_NAME = "tracked-release-check"
        private const val CHANNEL_ID = "tracked_releases"

        /** Random 3-8 hour spread so the check isn't a predictable fixed-interval poll. */
        private fun randomDelayMinutes(): Long = Random.nextLong(3 * 60L, 8 * 60L)
    }
}

class ReleaseNotifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = ReleaseNotifyManager(applicationContext)
        return try {
            manager.checkNow()
            manager.scheduleNext()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReleaseNotifyWorker", "Check run failed, rescheduling anyway", e)
            manager.scheduleNext()
            Result.success()
        }
    }
}
