package com.riyaz.rssdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.Executors

/** Runs only while at least one download is active and owns the progress notification. */
class DownloadKeepAliveService : Service() {
    companion object {
        const val ACTION_START = "com.riyaz.rssdownloader.action.START_DOWNLOAD_KEEP_ALIVE"
        const val ACTION_STOP = "com.riyaz.rssdownloader.action.STOP_DOWNLOAD_KEEP_ALIVE"
        private const val CHANNEL_ID = "rss_downloader_downloads"
        private const val NOTIFICATION_ID = 4101
        private val ACTIVE_STATUSES = setOf("QUEUED", "PENDING", "STARTING", "DOWNLOADING", "PROCESSING", "RUNNING")

        fun startIntent(context: Context): Intent = Intent(context, DownloadKeepAliveService::class.java).setAction(ACTION_START)
        fun stopIntent(context: Context): Intent = Intent(context, DownloadKeepAliveService::class.java).setAction(ACTION_STOP)
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var stopping = false
    private val lastStatuses = mutableMapOf<String, String>()
    private val api by lazy {
        // The host is application-controlled. Never read legacy user-editable host/token preferences.
        NativeHostApi(
            BuildConfig.RSS_HOST_BASE_URL,
            BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
            getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("app_key", null)
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopping = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        stopping = false
        startForeground(NOTIFICATION_ID, buildNotification("Download active • checking status…", 0))
        pollDownloads()
        return START_NOT_STICKY
    }

    private fun pollDownloads() {
        if (stopping) return
        executor.execute {
            api.listDownloads { result ->
                if (stopping) return@listDownloads
                result.onSuccess { jobs ->
                    jobs.forEach { job ->
                        val status = job.status.uppercase(Locale.US)
                        val previous = lastStatuses.put(job.jobId, status)
                        if (previous != null && previous != status) {
                            val prefs = getSharedPreferences("rss-downloader", MODE_PRIVATE)
                            val title = job.title ?: "RSS Downloader"
                            if (status in setOf("COMPLETED", "COMPLETE", "SUCCESS", "FINISHED") && prefs.getBoolean("notifyCompleted", true)) notifyTerminal(job.jobId, "Download completed", title, false)
                            else if (status in setOf("FAILED", "ERROR", "CANCELLED", "CANCELED") && prefs.getBoolean("notifyFailed", true)) notifyTerminal(job.jobId, "Download failed", job.error ?: title, true)
                        }
                    }
                    val active = jobs.filter { it.status.uppercase(Locale.US) in ACTIVE_STATUSES }
                    if (active.isEmpty()) {
                        stopping = true
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val job = active.first()
                        val progress = (job.progress ?: 0).coerceIn(0, 100)
                        val title = job.title ?: "RSS Downloader"
                        val text = if (active.size == 1) "$title • $progress%" else "${active.size} downloads • $progress%"
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, progress))
                        schedulePoll()
                    }
                }.onFailure {
                    if (!stopping) schedulePoll()
                }
            }
        }
    }

    private fun schedulePoll() {
        if (stopping) return
        executor.execute {
            try { Thread.sleep(5000) } catch (_: InterruptedException) { return@execute }
            if (!stopping) pollDownloads()
        }
    }

    private fun notifyTerminal(jobId: String, title: String, message: String, failed: Boolean) {
        val notificationId = NOTIFICATION_ID + (jobId.hashCode() and 0x0FFF)
        getSystemService(NotificationManager::class.java).notify(
            notificationId,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.rss_downloader_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    private fun buildNotification(text: String, progress: Int): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.rss_downloader_logo)
        .setContentTitle("RSS Downloader")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(100, progress, false)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "RSS Downloader downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows RSS Downloader progress only while downloads are running."
            },
        )
    }

    override fun onDestroy() {
        stopping = true
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
