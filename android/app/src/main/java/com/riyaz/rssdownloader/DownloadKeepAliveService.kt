package com.riyaz.rssdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps RSS Downloader's process alive while downloads may be running.
 * The service is intentionally lightweight; the downloader runtime remains
 * the source of truth for job state and persistence.
 */
class DownloadKeepAliveService : Service() {
    companion object {
        const val ACTION_START = "com.riyaz.rssdownloader.action.START_DOWNLOAD_KEEP_ALIVE"
        const val ACTION_STOP = "com.riyaz.rssdownloader.action.STOP_DOWNLOAD_KEEP_ALIVE"
        private const val CHANNEL_ID = "rss_downloader_downloads"
        private const val NOTIFICATION_ID = 4101

        fun startIntent(context: Context): Intent = Intent(context, DownloadKeepAliveService::class.java)
            .setAction(ACTION_START)

        fun stopIntent(context: Context): Intent = Intent(context, DownloadKeepAliveService::class.java)
            .setAction(ACTION_STOP)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.rss_downloader_logo)
        .setContentTitle("RSS Downloader")
        .setContentText("Downloads are running in the background")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "RSS Downloader downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps RSS Downloader active while downloads are running."
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
