package com.android.runbeat.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 更新系统的持久化：
 *  - 「不再提示」的远端版本号（只对当前版本生效，更高版本仍会提示）
 *  - 下载任务 id 与完成状态（用于下载完成后去重触发安装）
 */
class UpdatePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户选择「不再提示」的远端版本号；未设置时为 null */
    var suppressedVersionCode: Int?
        get() = prefs.getInt(KEY_SUPPRESSED, -1).takeIf { it > 0 }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SUPPRESSED) else putInt(KEY_SUPPRESSED, value)
            }.apply()
        }

    /** 当前后台下载任务的 id；无任务时为 null */
    var pendingDownloadId: Long?
        get() = prefs.getLong(KEY_PENDING_DOWNLOAD, -1L).takeIf { it >= 0 }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PENDING_DOWNLOAD) else putLong(KEY_PENDING_DOWNLOAD, value)
            }.apply()
        }

    /** 已处理（已引导安装）的下载任务 id，用于跨进程去重 */
    var processedDownloadId: Long?
        get() = prefs.getLong(KEY_PROCESSED_DOWNLOAD, -1L).takeIf { it >= 0 }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PROCESSED_DOWNLOAD) else putLong(KEY_PROCESSED_DOWNLOAD, value)
            }.apply()
        }

    companion object {
        const val PREFS_NAME = "update_prefs"
        private const val KEY_SUPPRESSED = "suppressed_version_code"
        private const val KEY_PENDING_DOWNLOAD = "pending_download_id"
        private const val KEY_PROCESSED_DOWNLOAD = "processed_download_id"
    }
}
