package com.android.runbeat.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 下载完成接收器：应用进程被系统回收时兜底处理下载完成事件。
 * 与轮询线程共用 [UpdateManager.processDownloadCompleted]（跨进程去重），
 * 保证下载完成后仍能引导安装，不丢失更新包。
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return
        UpdateManager.getInstance(context).processDownloadCompleted(downloadId)
    }
}
