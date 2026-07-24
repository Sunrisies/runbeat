package com.android.runbeat.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.android.runbeat.R
import java.io.File

/** 下载任务实时状态 */
data class DownloadProgress(
    val downloadId: Long,
    val status: Int,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
) {
    /** 0..1；总大小未知时返回 0 */
    val progress: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * 基于系统 DownloadManager 的后台下载封装。
 * 系统级保证断点续传、网络切换与系统下载通知；应用内通过轮询展示进度。
 */
class AppDownloader(private val context: Context) {

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** 发起新版本 APK 下载，返回 downloadId */
    fun start(manifest: UpdateManifest): Long {
        val request = DownloadManager.Request(Uri.parse(manifest.updateUrl))
            .setTitle("RunBeat ${manifest.versionName}")
            .setDescription(context.getString(R.string.update_downloading_desc))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        request.setDestinationInExternalFilesDir(context, DOWNLOAD_DIR, apkName(manifest))
        return downloadManager.enqueue(request)
    }

    /** 查询下载进度 */
    fun query(downloadId: Long): DownloadProgress {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return DownloadProgress(downloadId, -1)
        cursor.use {
            if (!it.moveToFirst()) return DownloadProgress(downloadId, -1)
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(downloadId, status, downloaded, total)
        }
    }

    /** 下载完成后的本地安装包 */
    fun destinationFile(downloadId: Long, manifest: UpdateManifest): File? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use {
            if (it.moveToFirst()) {
                val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                if (localUri != null && localUri.isNotEmpty()) {
                    return runCatching { File(Uri.parse(localUri).path ?: "") }.getOrNull()
                }
            }
        }
        // 兜底：按已知路径构造
        return context.getExternalFilesDir(DOWNLOAD_DIR)?.let { File(it, apkName(manifest)) }
    }

    /** 取消/清理下载任务 */
    fun cancel(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    private fun apkName(manifest: UpdateManifest): String =
        "runbeat-${manifest.versionCode}-${manifest.versionName}.apk"

    companion object {
        const val DOWNLOAD_DIR = "updates"
    }
}
