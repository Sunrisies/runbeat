package com.android.runbeat.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.runbeat.service.MetronomeService
import com.android.runbeat.metronome.core.IntervalSnapshot
import com.android.runbeat.metronome.core.IntervalStatus
import com.android.runbeat.metronome.core.MetronomeSettings
import com.android.runbeat.metronome.core.MetronomeStatus
import com.android.runbeat.metronome.core.SoundType
import com.android.runbeat.metronome.core.TickEvent
import com.android.runbeat.metronome.settings.MetronomePrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 界面模式 */
enum class MetronomeMode { BASIC, INTERVAL }

/** UI 状态聚合 */
data class MetronomeUiState(
    val settings: MetronomeSettings = MetronomeSettings(),
    val status: MetronomeStatus = MetronomeStatus.STOPPED,
    val lastTick: TickEvent? = null,
    val customPresetBpm: Int? = null,
    val serviceBound: Boolean = false,
    val mode: MetronomeMode = MetronomeMode.BASIC,
    val intervalWorkMinutes: Int = 1,
    val intervalRestMinutes: Int = 1,
    val interval: IntervalSnapshot = IntervalSnapshot(),
)

/**
 * 节拍器 ViewModel：持有 UI 状态，通过绑定前台服务进行控制与状态同步。
 * 服务为节拍引擎的唯一持有者，保证后台/锁屏期间节拍不中断。
 */
class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = MetronomePrefs(application)

    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            settings = prefs.loadSettings(),
            customPresetBpm = prefs.loadCustomPresetBpm(),
            intervalWorkMinutes = prefs.loadIntervalWorkMinutes(),
            intervalRestMinutes = prefs.loadIntervalRestMinutes(),
        )
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private val _ticks = MutableSharedFlow<TickEvent>(extraBufferCapacity = 16)
    val ticks: SharedFlow<TickEvent> = _ticks.asSharedFlow()

    private var service: MetronomeService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? MetronomeService.LocalBinder)?.getService()
            bound = service != null
            update { it.copy(serviceBound = bound) }
            service?.let { s ->
                viewModelScope.launch {
                    s.status.collect { status -> update { it.copy(status = status) } }
                }
                viewModelScope.launch {
                    s.settings.collect { settings ->
                        update { it.copy(settings = settings) }
                    }
                }
                viewModelScope.launch {
                    s.ticks.collect { tick ->
                        _ticks.emit(tick)
                        update { it.copy(lastTick = tick) }
                    }
                }
                viewModelScope.launch {
                    s.interval.collect { snap ->
                        update { it.copy(interval = snap) }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            update { it.copy(serviceBound = false) }
        }
    }

    init {
        val app = getApplication<Application>()
        app.bindService(Intent(app, MetronomeService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(connection)
        super.onCleared()
    }

    // ---------------------------------------------------------------- 动作

    fun start() {
        val s = _uiState.value.settings
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, MetronomeService.startIntent(context, s))
    }

    fun pause() {
        service?.pause()
    }

    fun resume() {
        service?.resume()
    }

    fun stop() {
        service?.stopAndSelf()
    }

    /** 运行中从第 1 拍、当前步频重新开始 */
    fun restart() {
        service?.restart()
    }

    fun setBpm(bpm: Int) {
        val updated = _uiState.value.settings.withBpm(bpm)
        prefs.saveSettings(updated)
        update { it.copy(settings = updated) }
        service?.changeBpm(updated.bpm)
    }

    // ---------------------------------------------------------------- 模式与循环

    fun setMode(mode: MetronomeMode) {
        val cur = _uiState.value
        if (cur.mode == mode) return
        // 切换到基础模式时, 若循环在运行则先停止
        if (mode == MetronomeMode.BASIC && cur.interval.status != IntervalStatus.IDLE) {
            service?.stopInterval()
        }
        update { it.copy(mode = mode) }
    }

    fun setIntervalWorkMinutes(value: Int) {
        val work = value.coerceIn(1, 60)
        val rest = _uiState.value.intervalRestMinutes
        prefs.saveIntervalMinutes(work, rest)
        update { it.copy(intervalWorkMinutes = work) }
    }

    fun setIntervalRestMinutes(value: Int) {
        val work = _uiState.value.intervalWorkMinutes
        val rest = value.coerceIn(1, 60)
        prefs.saveIntervalMinutes(work, rest)
        update { it.copy(intervalRestMinutes = rest) }
    }

    fun startInterval() {
        val s = _uiState.value
        service?.startInterval(s.intervalWorkMinutes, s.intervalRestMinutes)
    }

    fun pauseInterval() = service?.pauseInterval()

    fun resumeInterval() = service?.resumeInterval()

    fun stopInterval() = service?.stopInterval()

    fun changeSound(sound: SoundType) {
        val updated = _uiState.value.settings.withSound(sound)
        prefs.saveSettings(updated)
        update { it.copy(settings = updated) }
        service?.changeSound(sound)
    }

    fun changeVolume(percent: Int) {
        val updated = _uiState.value.settings.withVolume(percent)
        prefs.saveSettings(updated)
        update { it.copy(settings = updated) }
        service?.changeVolume(percent)
    }

    /** 保存当前 BPM 为「我的预设」 */
    fun saveCustomPreset() {
        val bpm = _uiState.value.settings.bpm
        prefs.saveCustomPresetBpm(bpm)
        update { it.copy(customPresetBpm = bpm) }
    }

    /** 切换音色时用于 UI 展示音色名称 */
    fun soundLabel(sound: SoundType): String = when (sound) {
        SoundType.CLICK -> "咔嗒"
        SoundType.BEEP -> "哔声"
        SoundType.WOOD -> "木鱼"
        SoundType.RUN -> "跑步节拍"
    }

    fun hasNotificationPermission(): Boolean {
        val context = getApplication<Application>()
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun update(transform: (MetronomeUiState) -> MetronomeUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
