package com.android.runbeat.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** 下载任务实时状态 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Paused(val progress: Float, val bytesDownloaded: Long) : DownloadState
    data class Success(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * 应用内自实现的 APK 下载器（替代 DownloadManager）。
 * - HTTP 流式写入文件，支持断点续传（Range）、暂停/继续/取消
 * - 进度通过 [state] 暴露，供 UI 与前台服务通知共同消费
 * - 无任何系统默认下载通知，通知样式完全自定义
 */
class AppDownloader private constructor(private val appContext: Context) {

    data class TaskInfo(
        val url: String,
        val destFile: File,
        val versionName: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val pausedFlag = AtomicBoolean(false)
    private val cancelledFlag = AtomicBoolean(false)

    @Volatile
    private var task: TaskInfo? = null

    @Volatile
    private var job: Job? = null

    /** 开始（或从已有 .part 断点续传） */
    fun start(task: TaskInfo) {
        val cur = _state.value
        if (cur is DownloadState.Downloading || cur is DownloadState.Paused) return
        this.task = task
        pausedFlag.set(false)
        cancelledFlag.set(false)
        _state.value = DownloadState.Downloading(0f, 0L, 0L)
        job = scope.launch { download(task) }
    }

    fun pause() {
        pausedFlag.set(true)
    }

    /** 暂停后继续（基于 .part 断点续传） */
    fun resume() {
        val t = task ?: return
        if (_state.value is DownloadState.Paused) {
            pausedFlag.set(false)
            cancelledFlag.set(false)
            job = scope.launch { download(t) }
        }
    }

    fun cancel() {
        cancelledFlag.set(true)
        task?.let { t ->
            runCatching { File(t.destFile.parentFile, t.destFile.name + ".part").delete() }
        }
        task = null
        _state.value = DownloadState.Idle
    }

    fun isActive(): Boolean = _state.value is DownloadState.Downloading || _state.value is DownloadState.Paused

    private suspend fun download(task: TaskInfo) {
        var attempts = 0
        while (true) {
            try {
                downloadOnce(task)
                return
            } catch (e: PausedException) {
                val part = File(task.destFile.parentFile, task.destFile.name + ".part")
                _state.value = DownloadState.Paused(0f, if (part.exists()) part.length() else 0L)
                return
            } catch (e: CancelledDownloadException) {
                cleanupPart(task)
                _state.value = DownloadState.Idle
                return
            } catch (e: Exception) {
                if (cancelledFlag.get()) {
                    cleanupPart(task)
                    _state.value = DownloadState.Idle
                    return
                }
                attempts++
                if (attempts > UpdateConfig.DOWNLOAD_RETRIES) {
                    Log.e(TAG, "下载失败（重试 $attempts 次后放弃）", e)
                    _state.value = DownloadState.Failed(describe(e))
                    return
                }
                Log.w(TAG, "下载中断，第 $attempts 次重试（断点续传）: ${e.message}")
                delay(UpdateConfig.DOWNLOAD_RETRY_DELAY_MS)
            }
        }
    }

    /** 单次下载：支持断点续传；完成前校验字节完整性，不完整视为失败 */
    private suspend fun downloadOnce(task: TaskInfo) {
        val dest = task.destFile
        val part = File(dest.parentFile, dest.name + ".part")
        var downloaded = if (part.exists()) part.length() else 0L

        val conn = (URL(task.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
            readTimeout = UpdateConfig.DOWNLOAD_READ_TIMEOUT_MS
            if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
            connect()
        }
        var raf: RandomAccessFile? = null
        try {
            val code = conn.responseCode
            val len = conn.contentLengthLong
            val total: Long = when {
                code == HttpURLConnection.HTTP_PARTIAL -> downloaded + len.coerceAtLeast(0)
                code in 200..299 -> {
                    if (downloaded > 0) {
                        part.delete()
                        downloaded = 0
                    }
                    len.coerceAtLeast(0)
                }
                else -> throw IOException("HTTP $code")
            }

            dest.parentFile?.mkdirs()
            raf = RandomAccessFile(part, "rw")
            raf.seek(downloaded)
            val input = conn.inputStream
            val buf = ByteArray(64 * 1024)
            while (true) {
                if (cancelledFlag.get()) throw CancelledDownloadException()
                if (pausedFlag.get()) throw PausedException()
                val read = input.read(buf)
                if (read < 0) break
                raf!!.write(buf, 0, read)
                downloaded += read
                _state.value = DownloadState.Downloading(progressOf(downloaded, total), downloaded, total)
            }
            // 完整性校验：服务端声明了总大小但没下完 → 失败并重试
            if (total > 0 && downloaded < total) {
                throw IOException("下载不完整（$downloaded/$total）")
            }
            raf?.close()
            raf = null
            part.renameTo(dest)
            Log.i(TAG, "下载完成: $dest")
            _state.value = DownloadState.Success(dest)
        } finally {
            raf?.runCatching { close() }
            conn.disconnect()
        }
    }

    private fun cleanupPart(task: TaskInfo) {
        runCatching { File(task.destFile.parentFile, task.destFile.name + ".part").delete() }
    }

    private class CancelledDownloadException : Exception()
    private class PausedException : Exception()

    private fun progressOf(downloaded: Long, total: Long): Float =
        if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    private fun describe(e: Exception): String =
        if (e is IOException) "网络异常：${e.message}" else "下载失败：${e.message}"

    companion object {
        private const val TAG = "RunBeatUpdate"
        const val DOWNLOAD_DIR = "updates"

        @Volatile
        private var instance: AppDownloader? = null

        fun getInstance(context: Context): AppDownloader =
            instance ?: synchronized(this) {
                instance ?: AppDownloader(context.applicationContext).also { instance = it }
            }

        /** 目标安装包路径：优先外部存储，其次应用内部目录 */
        fun resolveDestFile(context: Context, manifest: UpdateManifest): File {
            context.getExternalFilesDir(DOWNLOAD_DIR)?.let { dir ->
                return File(dir, apkName(manifest))
            }
            return File(File(context.filesDir, DOWNLOAD_DIR), apkName(manifest))
        }

        private fun apkName(manifest: UpdateManifest): String =
            "runbeat-${manifest.versionCode}-${manifest.versionName}.apk"
    }
}
