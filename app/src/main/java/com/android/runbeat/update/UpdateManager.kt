package com.android.runbeat.update

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import com.android.runbeat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** 更新流程 UI 状态 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val manifest: UpdateManifest, val forced: Boolean) : UpdateUiState
    data object NoUpdate : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class DownloadFailed(val message: String) : UpdateUiState
    data object InstallPermissionNeeded : UpdateUiState
    data object Installing : UpdateUiState
}

/** 非弹窗的即时反馈事件（提示文案） */
data class UpdateNotice(val message: String)

/**
 * 更新系统门面（进程级单例）：
 * 版本检测 → 决策 → 下载 → 安装 全流程，负责状态机与跨进程去重。
 */
class UpdateManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = UpdatePrefs(appContext)
    private val downloader = AppDownloader(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<UpdateNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<UpdateNotice> = _notices.asSharedFlow()

    @Volatile
    private var lastAvailableManifest: UpdateManifest? = null

    @Volatile
    private var lastDownloadedFile: File? = null

    // ---------------------------------------------------------------- 版本检测

    /**
     * 检查更新。自动检查失败时静默（不打扰用户）；手动检查失败时发出提示。
     */
    fun checkForUpdates(manual: Boolean = false) {
        if (_state.value is UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        scope.launch {
            val localCode = localVersionCode()
            val source = if (UpdateConfig.USE_MOCK) {
                MockUpdateSource(appContext)
            } else {
                HttpUpdateSource(UpdateConfig.CHECK_URL)
            }
            val manifest = try {
                UpdateChecker(source).fetchWithRetry()
            } catch (e: IOException) {
                _state.value = UpdateUiState.Idle
                if (manual) _notices.emit(UpdateNotice(appContext.getString(R.string.update_check_failed)))
                return@launch
            }

            val decision = UpdatePolicy.decide(
                remoteVersionCode = manifest.versionCode,
                localVersionCode = localCode,
                suppressedVersionCode = prefs.suppressedVersionCode,
                forceUpdate = manifest.forceUpdate,
            )
            when (decision) {
                UpdatePolicy.Decision.NO_UPDATE -> {
                    _state.value = UpdateUiState.Idle
                    if (manual) _notices.emit(UpdateNotice(appContext.getString(R.string.update_latest)))
                }
                UpdatePolicy.Decision.SUPPRESSED -> {
                    _state.value = UpdateUiState.Idle
                }
                UpdatePolicy.Decision.SHOW -> {
                    lastAvailableManifest = manifest
                    _state.value = UpdateUiState.Available(manifest, forced = false)
                }
                UpdatePolicy.Decision.SHOW_FORCED -> {
                    lastAvailableManifest = manifest
                    _state.value = UpdateUiState.Available(manifest, forced = true)
                }
            }
        }
    }

    /** 「暂不下载」：关闭弹窗，本次会话不再提示（下次启动仍会检测） */
    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    /** 「不再提示」：记录当前远端版本，仅对该版本不再提示 */
    fun suppress() {
        lastAvailableManifest?.let { prefs.suppressedVersionCode = it.versionCode }
        _state.value = UpdateUiState.Idle
    }

    // ---------------------------------------------------------------- 下载

    /** 「立即下载」：直接发起后台下载（安装权限在下载完成时再引导，避免反复拦截） */
    fun downloadNow() {
        val manifest = lastAvailableManifest ?: return
        scope.launch {
            val downloadId = runCatching { downloader.start(manifest) }.getOrNull()
            if (downloadId == null) {
                _state.value = UpdateUiState.DownloadFailed(
                    appContext.getString(R.string.update_download_start_failed)
                )
                return@launch
            }
            prefs.pendingDownloadId = downloadId
            prefs.processedDownloadId = null
            _state.value = UpdateUiState.Downloading(0f)
            pollDownload(downloadId, manifest)
        }
    }

    private suspend fun pollDownload(downloadId: Long, manifest: UpdateManifest) {
        while (scope.coroutineContext.isActive) {
            val progress = downloader.query(downloadId)
            when (progress.status) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> {
                    _state.value = UpdateUiState.Downloading(progress.progress)
                    delay(UpdateConfig.PROGRESS_POLL_INTERVAL_MS)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    processDownloadCompleted(downloadId)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.pendingDownloadId = null
                    downloader.cancel(downloadId)
                    _state.value = UpdateUiState.DownloadFailed(
                        appContext.getString(R.string.update_download_failed)
                    )
                    return
                }
                else -> delay(UpdateConfig.PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------- 完成/安装

    /**
     * 处理下载完成事件（轮询线程与系统接收器共用入口，跨进程去重）。
     * @param downloadId 下载任务 id
     */
    fun processDownloadCompleted(downloadId: Long) {
        if (prefs.pendingDownloadId != downloadId) return // 非本应用任务
        if (prefs.processedDownloadId == downloadId) return // 已处理过
        scope.launch {
            val manifest = lastAvailableManifest
            if (manifest == null) {
                prefs.pendingDownloadId = null
                return@launch
            }
            val progress = downloader.query(downloadId)
            if (progress.status != DownloadManager.STATUS_SUCCESSFUL) return@launch
            prefs.processedDownloadId = downloadId
            prefs.pendingDownloadId = null
            val file = downloader.destinationFile(downloadId, manifest)
            lastDownloadedFile = file
            guideToInstall(file)
        }
    }

    private fun guideToInstall(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _state.value = UpdateUiState.DownloadFailed(
                appContext.getString(R.string.update_apk_invalid)
            )
            return
        }
        if (InstallHelper.canRequestPackageInstalls(appContext)) {
            installNow()
        } else {
            _state.value = UpdateUiState.InstallPermissionNeeded
            InstallHelper.openInstallSettings(appContext)
        }
    }

    /** 拉起系统安装器 */
    fun installNow() {
        val file = lastDownloadedFile ?: return
        if (InstallHelper.startInstall(appContext, file)) {
            _state.value = UpdateUiState.Installing
        } else {
            _state.value = UpdateUiState.DownloadFailed(
                appContext.getString(R.string.update_install_failed)
            )
        }
    }

    /**
     * 从「安装未知应用」设置页返回后自动继续安装。
     * 若已有下载好的安装包且权限已授予，则立即拉起安装器，避免反复要求权限。
     */
    fun resumePendingInstall() {
        val file = lastDownloadedFile ?: return
        if (!file.exists()) return
        if (InstallHelper.canRequestPackageInstalls(appContext)) {
            installNow()
        }
    }

    fun openInstallSettings() {
        InstallHelper.openInstallSettings(appContext)
    }

    // ---------------------------------------------------------------- 工具

    private fun localVersionCode(): Int =
        runCatching {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)

    companion object {
        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context).also { instance = it }
            }
    }
}
