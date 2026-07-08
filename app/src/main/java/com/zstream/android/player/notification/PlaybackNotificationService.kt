package com.zstream.android.player.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver
import coil.ImageLoader
import coil.request.ImageRequest
import com.zstream.android.MainActivity
import com.zstream.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "playback"
private const val NOTIFICATION_ID = 4201

class PlaybackNotificationService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mediaSession: MediaSessionCompat
    private var artworkJob: Job? = null
    private var lastArtworkUrl: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var cachedAppIconBitmap: Bitmap? = null

    private fun appIconBitmap(): Bitmap? {
        cachedAppIconBitmap?.let { return it }
        val bitmap = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        cachedAppIconBitmap = bitmap
        return bitmap
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        mediaSession = MediaSessionCompat(this, "ZStreamPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    NowPlayingController.controls?.play()
                }

                override fun onPause() {
                    NowPlayingController.controls?.pause()
                }

                override fun onStop() {
                    NowPlayingController.controls?.stop()
                }
            })
            isActive = true
        }

        scope.launch {
            NowPlayingController.state.collect { info ->
                if (info == null) {
                    stopSelf()
                    return@collect
                }
                updateSession(info)
                updateNotification(info)
            }
        }
    }

    private fun updateSession(info: NowPlayingInfo) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, info.durationMs.coerceAtLeast(0))
        if (info.artworkUrl == lastArtworkUrl) {
            lastArtworkBitmap?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            }
        }
        mediaSession.setMetadata(metadataBuilder.build())

        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (info.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    info.positionMs.coerceAtLeast(0),
                    if (info.isPlaying) 1f else 0f,
                )
                .build()
        )

        if (info.artworkUrl != lastArtworkUrl) {
            loadArtwork(info)
        }
    }

    private fun loadArtwork(info: NowPlayingInfo) {
        lastArtworkUrl = info.artworkUrl
        artworkJob?.cancel()
        val url = info.artworkUrl ?: run {
            lastArtworkBitmap = null
            return
        }
        artworkJob = scope.launch(Dispatchers.IO) {
            val loader = ImageLoader(applicationContext)
            val request = ImageRequest.Builder(applicationContext)
                .data(url)
                .allowHardware(false)
                .build()
            val result = runCatching { loader.execute(request).drawable }.getOrNull()
            val bitmap = result?.let { drawable ->
                Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            lastArtworkBitmap = bitmap
            launch(Dispatchers.Main) {
                NowPlayingController.state.value?.let { latest ->
                    if (latest.artworkUrl == url) {
                        updateSession(latest)
                        updateNotification(latest)
                    }
                }
            }
        }
    }

    private fun updateNotification(info: NowPlayingInfo) {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseAction = if (info.isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_notification, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_notification, "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY),
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(info.title)
            .setContentText(info.subtitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(lastArtworkBitmap ?: appIconBitmap())
            .setContentIntent(contentIntent)
            .setOngoing(info.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()

        if (info.isPlaying) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else 0,
            )
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        scope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Now playing controls"
                        setShowBadge(false)
                    }
                )
            }
        }
    }
}
