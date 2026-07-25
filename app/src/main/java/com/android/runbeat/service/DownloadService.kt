package com.android.runbeat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.android.runbeat.MainActivity
import com.android.runbeat.R
import com.android.runbeat.update.AppDownloader
import com.android.runbeat.update.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新下载前台服务：托管品牌化下载进度通知（暂停/继续/取消），
 * 实际下载由进程级 [AppDownloader] 完成。下载结束自动退出前台。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_STICKY
                val destPath = intent.getStringExtra(EXTRA_DEST) ?: return START_STICKY
                val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
                promoteToForeground(progress = 0f, paused = false)
                val task = AppDownloader.TaskInfo(url, File(destPath), version)
                AppDownloader.getInstance(this).start(task)
                observe(task)
            }
            ACTION_PAUSE -> AppDownloader.getInstance(this).pause()
            ACTION_RESUME -> AppDownloader.getInstance(this).resume()
            ACTION_CANCEL -> {
                AppDownloader.getInstance(this).cancel()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun observe(task: AppDownloader.TaskInfo) {
        scope.launch {
            AppDownloader.getInstance(this@DownloadService).state.collect { s ->
                when (s) {
                    is DownloadState.Downloading -> updateNotification(s.progress, paused = false)
                    is DownloadState.Paused -> updateNotification(s.progress, paused = true)
                    is DownloadState.Success -> {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is DownloadState.Failed -> {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    DownloadState.Idle -> Unit
                }
            }
        }
    }

    // ---------------------------------------------------------------- 通知

    private fun promoteToForeground(progress: Float, paused: Boolean) {
        val notification = buildNotification(progress, paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Float, paused: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(progress, paused))
        } catch (_: SecurityException) {
        }
    }

    private fun buildNotification(progress: Float, paused: Boolean): Notification {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setSmallIcon(R.drawable.ic_download)
            .setContentTitle(if (paused) "下载已暂停" else "正在下载更新")
            .setContentText(if (paused) "点击继续" else "$percent%")
            .setProgress(100, percent, progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

        val actionText = if (paused) "继续" else "暂停"
        val actionIntent = Intent(this, DownloadService::class.java).apply {
            action = if (paused) ACTION_RESUME else ACTION_PAUSE
        }
        builder.addAction(buildAction(actionText, actionIntent))
        builder.addAction(buildAction("取消", Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL }))
        return builder.build()
    }

    private fun buildAction(title: String, intent: Intent): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_download),
            title,
            PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "update_download"

        const val ACTION_START = "com.android.runbeat.download.START"
        const val ACTION_PAUSE = "com.android.runbeat.download.PAUSE"
        const val ACTION_RESUME = "com.android.runbeat.download.RESUME"
        const val ACTION_CANCEL = "com.android.runbeat.download.CANCEL"

        private const val EXTRA_URL = "url"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_VERSION = "version"

        fun startIntent(
            context: Context,
            url: String,
            destFile: File,
            versionName: String,
        ): Intent = Intent(context, DownloadService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_DEST, destFile.absolutePath)
            putExtra(EXTRA_VERSION, versionName)
        }

        fun cancelIntent(context: Context) =
            Intent(context, DownloadService::class.java).apply { action = ACTION_CANCEL }
    }
}
