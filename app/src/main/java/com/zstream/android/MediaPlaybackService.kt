package com.zstream.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MediaPlaybackService : Service() {
    companion object {
        const val CHANNEL_ID = "zstream_media"
        const val NOTIF_ID = 1
        const val ACTION_PLAY = "com.zstream.android.PLAY"
        const val ACTION_PAUSE = "com.zstream.android.PAUSE"
        var mediaSession: MediaSessionCompat? = null
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> mediaSession?.controller?.transportControls?.play()
            ACTION_PAUSE -> mediaSession?.controller?.transportControls?.pause()
        }
        val playing = intent?.getBooleanExtra("playing", true) ?: true
        startForeground(NOTIF_ID, buildNotification())
        // Stop only when explicitly told there's no media (no video element), not on pause
        if (intent?.getBooleanExtra("noMedia", false) == true) stopSelf()
        return START_NOT_STICKY
    }

    fun buildNotification(): Notification {
        val session = mediaSession
        val state = session?.controller?.playbackState?.state
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        val title = session?.controller?.metadata
            ?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE)
            ?: "ZStream"

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("ZStream")
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .apply {
                session?.sessionToken?.let { token ->
                    setStyle(MediaStyle().setMediaSession(token).setShowActionsInCompactView(0))
                }
            }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
