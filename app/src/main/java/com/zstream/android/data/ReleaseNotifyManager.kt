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
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zstream.android.MainActivity
import com.zstream.android.R
import com.zstream.android.data.local.dao.TrackedReleaseDao
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.remote.TmdbApi
import com.zstream.android.di.TmdbTokenCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

const val OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA = "open_tracked_release_media_type"
const val OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA = "open_tracked_release_tmdb_id"

class NotificationUnavailableException(message: String) : IllegalStateException(message)

/** TMDB supplies dates without times, so same-day releases wait until local noon. */
internal fun hasReachedReleaseDate(rawDate: String?, now: LocalDateTime = LocalDateTime.now()): Boolean {
    val date = rawDate?.takeIf(String::isNotBlank)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    } ?: return false
    return date.isBefore(now.toLocalDate()) ||
        (date == now.toLocalDate() && !now.toLocalTime().isBefore(LocalTime.NOON))
}

@Singleton
class ReleaseNotifyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackedReleaseDao,
    private val api: TmdbApi,
    private val settingsPreferences: SettingsPreferences,
    private val accountRepository: AccountRepository,
) {
    /** Cold-start safety net; preserves an already queued/running check. */
    fun ensureStarted() {
        createNotificationChannel()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request(randomDelayMinutes()),
        )
    }

    /** A new row must leave a check queued even if the previous worker is just finishing empty. */
    fun scheduleAfterSubscription() {
        createNotificationChannel()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request(randomDelayMinutes()),
        )
    }

    /** Appends after the running unique worker instead of replacing/cancelling that worker. */
    fun scheduleNext(delayMinutes: Long = randomDelayMinutes()) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(delayMinutes),
        )
    }

    /** Returns whether any owner still has subscriptions and therefore needs another check. */
    suspend fun checkNow(): Boolean {
        createNotificationChannel()
        // The injected TMDB interceptor reads this cache at request time. Explicitly hydrate it
        // because a worker may be the first component started in a fresh process.
        TmdbTokenCache.token = settingsPreferences.settings.first().tmdbApiKey

        val ownerId = accountRepository.currentReleaseOwner()
        val tracked = dao.getAllSync(ownerId)

        tracked.filter { it.mediaType == "movie" }.forEach { entry ->
            check(entry) {
                hasReachedReleaseDate(api.movieDetail(entry.tmdbId, append = "").releaseDate)
            }
        }

        tracked.filter { it.mediaType == "tv" }
            .groupBy { it.tmdbId to it.seasonNumber }
            .forEach { (showSeason, entries) ->
                val seasonNumber = showSeason.second ?: return@forEach
                val season = try {
                    api.season(showSeason.first, seasonNumber)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Check failed for tv:${showSeason.first}:$seasonNumber", e)
                    return@forEach
                }
                val byEpisode = season.episodes.orEmpty().associateBy { it.episodeNumber }
                entries.forEach { entry ->
                    val episode = entry.episodeNumber?.let(byEpisode::get)
                    if (hasReachedReleaseDate(episode?.airDate)) deliverIfCurrent(ownerId, entry)
                }
            }

        return dao.countAll() > 0
    }

    /** Immediate phone-side acknowledgement for a TV-originated subscription. */
    fun showSubscriptionConfirmation(request: ReleaseSubscriptionRequest): Boolean {
        val subject = if (request.mediaType == "movie") {
            request.title
        } else {
            context.getString(
                R.string.system_release_episode_subject,
                request.title,
                request.seasonNumber.toString(),
                request.episodeNumber.toString(),
            )
        }
        return postNotification(
            notificationId = "release-subscription:${request.key}".hashCode(),
            title = context.getString(R.string.system_release_subscription_title),
            message = context.getString(R.string.system_release_subscription_message, subject),
            pendingIntent = null,
        )
    }

    /** Null means posting is currently possible; a message explains why a new add must fail. */
    fun notificationUnavailableReason(): String? {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return context.getString(R.string.system_release_allow_notifications)
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return context.getString(R.string.system_release_notifications_disabled)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return context.getString(R.string.system_release_channel_disabled)
            }
        }
        return null
    }

    private suspend fun check(entry: TrackedReleaseEntity, released: suspend () -> Boolean) {
        val isReleased = try {
            released()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Check failed for ${entry.key}", e)
            false
        }
        if (isReleased) deliverIfCurrent(entry.ownerId, entry)
    }

    private suspend fun deliverIfCurrent(ownerId: String, snapshot: TrackedReleaseEntity) {
        // Once a released row reaches delivery, finish the post+generation delete together even if
        // a newly-added subscription replaces this worker. Otherwise cancellation can land after
        // notify() and before the delete, causing the replacement worker to notify it again.
        withContext(NonCancellable) {
            if (accountRepository.currentReleaseOwner() != ownerId) return@withContext
            val current = dao.get(ownerId, snapshot.key)
            if (current?.id != snapshot.id) return@withContext
            if (showReleaseNotification(snapshot)) {
                // Generation-safe: never delete a row created by an untrack/re-track during polling.
                dao.deleteGeneration(ownerId, snapshot.key, snapshot.id)
            }
        }
    }

    private fun showReleaseNotification(entry: TrackedReleaseEntity): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA, entry.mediaType)
            putExtra(OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA, entry.tmdbId)
            entry.seasonNumber?.let { putExtra(OPEN_TRACKED_RELEASE_SEASON_EXTRA, it) }
            entry.episodeNumber?.let { putExtra(OPEN_TRACKED_RELEASE_EPISODE_EXTRA, it) }
        }
        val notificationId = "${entry.ownerId}:${entry.key}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, message) = if (entry.mediaType == "movie") {
            context.getString(R.string.system_release_movie_title, entry.title) to
                context.getString(R.string.system_release_movie_message)
        } else {
            context.getString(
                R.string.system_release_episode_title,
                entry.title,
                entry.seasonNumber.toString(),
                entry.episodeNumber.toString(),
            ) to (entry.episodeTitle?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.system_release_episode_message))
        }
        return postNotification(notificationId, title, message, pendingIntent)
    }

    private fun postNotification(
        notificationId: Int,
        title: String,
        message: String,
        pendingIntent: PendingIntent?,
    ): Boolean {
        val blocked = notificationUnavailableReason()
        if (blocked != null) {
            Log.w(TAG, "Notification retained: $blocked")
            return false
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply { pendingIntent?.let(::setContentIntent) }
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification retained because posting was denied", e)
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.system_release_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.system_release_channel_description)
            },
        )
    }

    private fun request(delayMinutes: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ReleaseNotifyWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

    companion object {
        private const val TAG = "ReleaseNotifyManager"
        private const val WORK_NAME = "tracked-release-check"
        private const val CHANNEL_ID = "tracked_releases"

        private fun randomDelayMinutes(): Long = Random.nextLong(3 * 60L, 8 * 60L)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReleaseNotifyManagerEntryPoint {
    fun releaseNotifyManager(): ReleaseNotifyManager
}

class ReleaseNotifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            ReleaseNotifyManagerEntryPoint::class.java,
        ).releaseNotifyManager()
        return try {
            if (manager.checkNow()) manager.scheduleNext()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReleaseNotifyWorker", "Release check failed; using WorkManager backoff", e)
            Result.retry()
        }
    }
}
