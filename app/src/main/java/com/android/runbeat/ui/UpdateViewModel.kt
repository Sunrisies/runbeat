package com.android.runbeat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.android.runbeat.update.UpdateManager
import com.android.runbeat.update.UpdateNotice
import com.android.runbeat.update.UpdateUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** 版本更新的 ViewModel：绑定进程级 [UpdateManager]，负责启动自动检测与 UI 动作转发。 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = UpdateManager.getInstance(application)

    val state: StateFlow<UpdateUiState> = manager.state

    private val _notices = MutableSharedFlow<UpdateNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<UpdateNotice> = _notices.asSharedFlow()

    /** 应用启动自动检测（延迟片刻，避免与首帧渲染竞争） */
    fun checkOnLaunch() {
        viewModelScope.launch {
            delay(AUTO_CHECK_DELAY_MS)
            manager.checkForUpdates(manual = false)
        }
    }

    /** 手动检查更新：失败/无更新时给出即时提示 */
    fun checkNow() {
        viewModelScope.launch {
            manager.checkForUpdates(manual = true)
            manager.notices.collect { notice -> _notices.emit(notice) }
        }
    }

    fun downloadNow() = manager.downloadNow()

    fun dismiss() = manager.dismiss()

    fun suppress() = manager.suppress()

    fun installNow() = manager.installNow()

    fun openInstallSettings() = manager.openInstallSettings()

    /** 从系统设置页返回后调用，权限已授予则自动继续安装 */
    fun resumePendingInstall() = manager.resumePendingInstall()

    fun retryDownload() = manager.downloadNow()

    private companion object {
        const val AUTO_CHECK_DELAY_MS = 1200L
    }
}
