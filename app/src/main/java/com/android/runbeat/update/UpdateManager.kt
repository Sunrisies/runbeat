package com.android.runbeat.update

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.runbeat.R
import com.android.runbeat.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

/** 更新流程 UI 状态 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val manifest: UpdateManifest, val forced: Boolean) : UpdateUiState
    data object NoUpdate : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UpdateUiState
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
                withTimeout(UpdateConfig.CHECK_TOTAL_TIMEOUT_MS) {
                    UpdateChecker(source).fetchWithRetry()
                }
            } catch (e: Exception) {
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

    /** 「立即下载」：启动前台下载服务，下载完成后自动引导安装 */
    fun downloadNow() {
        val manifest = lastAvailableManifest ?: return
        if (_state.value is UpdateUiState.Downloading) return
        val appDownloader = AppDownloader.getInstance(appContext)
        val dest = AppDownloader.resolveDestFile(appContext, manifest)
        lastDownloadedFile = dest
        Log.d(TAG, "downloadNow: url=${manifest.updateUrl}, dest=$dest")
        _state.value = UpdateUiState.Downloading(0f)
        ContextCompat.startForegroundService(
            appContext,
            DownloadService.startIntent(appContext, manifest.updateUrl, dest, manifest.versionName),
        )
        observeDownload(appDownloader)
    }

    /** 取消当前下载 */
    fun cancelDownload() {
        AppDownloader.getInstance(appContext).cancel()
        _state.value = UpdateUiState.Idle
    }

    private fun observeDownload(appDownloader: AppDownloader) {
        scope.launch {
            appDownloader.state.collect { s ->
                when (s) {
                    is DownloadState.Downloading -> _state.value = UpdateUiState.Downloading(
                        progress = s.progress,
                        bytesDownloaded = s.bytesDownloaded,
                        totalBytes = s.totalBytes,
                    )
                    is DownloadState.Paused -> Unit // 保留当前进度显示
                    is DownloadState.Success -> {
                        lastDownloadedFile = s.file
                        guideToInstall(s.file)
                    }
                    is DownloadState.Failed -> _state.value = UpdateUiState.DownloadFailed(s.message)
                    DownloadState.Idle -> Unit
                }
            }
        }
    }

    // ---------------------------------------------------------------- 完成/安装

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
        private const val TAG = "RunBeatUpdate"

        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context).also { instance = it }
            }
    }
}
