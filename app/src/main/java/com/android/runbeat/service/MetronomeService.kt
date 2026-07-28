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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.android.runbeat.MainActivity
import com.android.runbeat.R
import com.android.runbeat.metronome.audio.MetronomeAudioPlayer
import com.android.runbeat.metronome.audio.SpeechPlayer
import com.android.runbeat.metronome.audio.SystemTts
import com.android.runbeat.metronome.core.MetronomeConstants
import com.android.runbeat.metronome.core.MetronomeEngine
import com.android.runbeat.metronome.core.MetronomeSettings
import com.android.runbeat.metronome.core.MetronomeStatus
import com.android.runbeat.metronome.core.SoundType
import com.android.runbeat.metronome.core.TickEvent
import com.android.runbeat.metronome.settings.MetronomePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import com.android.runbeat.metronome.core.CountdownTrigger
import com.android.runbeat.metronome.core.IntervalEngine
import com.android.runbeat.metronome.core.IntervalPhase
import com.android.runbeat.metronome.core.IntervalSnapshot
import com.android.runbeat.metronome.core.IntervalStatus

/**
 * 节拍器前台服务。
 * 负责：节拍引擎持有、音频播放、通知控制、后台/锁屏运行（前台服务 + WakeLock）、
 * 来电等音频焦点打断后的自动暂停与恢复。
 */
class MetronomeService : Service() {

    // 注意：prefs 依赖 applicationContext，只能在 onCreate 及之后访问（构造期间 context 尚未 attach）
    private val prefs by lazy { MetronomePrefs(this) }
    private val engine = MetronomeEngine()
    private val audioPlayer = MetronomeAudioPlayer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(MetronomeStatus.STOPPED)
    val status: StateFlow<MetronomeStatus> = _status.asStateFlow()

    private val _settings = MutableStateFlow(MetronomeSettings())
    val settings: StateFlow<MetronomeSettings> = _settings.asStateFlow()

    private val _ticks = MutableSharedFlow<TickEvent>(extraBufferCapacity = 8)
    val ticks: SharedFlow<TickEvent> = _ticks.asSharedFlow()

    private val _interval = MutableStateFlow(IntervalSnapshot())
    val interval: StateFlow<IntervalSnapshot> = _interval.asStateFlow()

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var autoResumeAfterFocusLoss = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // 内置预合成语音（离线兜底）
    private val speechPlayer = SpeechPlayer(this)

    // 系统文字转语音（按官方 API 封装）
    private var systemTts: SystemTts? = null

    @Volatile
    private var ttsAvailable = false

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private var lastCountdownSec = -1

    /** BPM 修改去抖：拖动结束后 150ms 自动重启节拍，让新步频立即生效 */
    private val debouncedRestart = Runnable { engine.restart() }

    private val binder = LocalBinder()

    private val tickListener = com.android.runbeat.metronome.core.MetronomeTickListener { event ->
        val s = _settings.value
        audioPlayer.play(event, s.soundType, s.volumePercent)
        _ticks.tryEmit(event)
    }

    override fun onCreate() {
        super.onCreate()
        // context 已 attach，此时才能安全访问 prefs
        _settings.value = prefs.loadSettings()
        // 提前创建并预热音频管线，消除按下「开始」后数秒才出声的问题
        audioPlayer.ensureStarted(_settings.value.soundType, _settings.value.volumePercent)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        // 内置语音（离线兜底）
        speechPlayer.ensureStarted()
        initTts()
    }

    /** 初始化系统文字转语音（中文优先，回退默认语音） */
    private fun initTts() {
        systemTts = SystemTts(this) {
            ttsAvailable = systemTts?.available ?: false
            _ttsReady.value = ttsAvailable
        }
    }

    /** 播报任意文本（走系统 TTS；不可用时返回 false 由调用方兜底） */
    private fun speakText(text: String): Boolean {
        val ok = systemTts?.speak(text) ?: false
        if (!ok) Log.w(SPEECH_TAG, "speakText 未入队（TTS不可用） text=\"$text\"")
        return ok
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(fromSettings(intent))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAndSelf()
            else -> {
                // START_STICKY 重建：若重启前正在运行则自动恢复
                if (intent == null && prefs.wasRunning) {
                    start(_settings.value)
                }
            }
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- 控制

    fun start(settings: MetronomeSettings) {
        _settings.value = settings
        prefs.saveSettings(settings)
        prefs.wasRunning = true
        audioPlayer.ensureStarted(settings.soundType, settings.volumePercent)
        try {
            promoteToForeground()
        } catch (_: SecurityException) {
            // 通知权限被拒绝：降级为普通前台运行（不显示通知）
        }
        acquireWakeLock()
        requestAudioFocus()
        engine.start(settings.bpm, tickListener)
        _status.value = MetronomeStatus.RUNNING
        updateNotification()
    }

    fun pause() {
        if (isIntervalActive()) {
            pauseInterval()
            return
        }
        if (_status.value != MetronomeStatus.RUNNING) return
        engine.pause()
        _status.value = MetronomeStatus.PAUSED
        releaseWakeLock()
        updateNotification()
    }

    fun resume() {
        if (isIntervalActive()) {
            resumeInterval()
            return
        }
        if (_status.value != MetronomeStatus.PAUSED) return
        acquireWakeLock()
        requestAudioFocus()
        engine.resume()
        _status.value = MetronomeStatus.RUNNING
        updateNotification()
    }

    fun stopAndSelf() {
        if (isIntervalActive()) {
            stopInterval()
            return
        }
        engine.stop()
        _status.value = MetronomeStatus.STOPPED
        releaseWakeLock()
        abandonAudioFocus()
        // 注意：不在停止时释放 audioPlayer —— 保持音频管线常热，
        // 否则每次「开始」都会重新冷启动音频设备导致首拍延迟
        prefs.wasRunning = false
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    // 由 Binder 调用的设置方法（UI 绑定后使用）

    fun changeBpm(bpm: Int) {
        val updated = _settings.value.withBpm(bpm)
        _settings.value = updated
        prefs.saveSettings(updated)
        if (_status.value == MetronomeStatus.RUNNING) {
            engine.bpm = bpm
            mainHandler.removeCallbacks(debouncedRestart)
            mainHandler.postDelayed(debouncedRestart, BPM_CHANGE_RESTART_DEBOUNCE_MS)
            updateNotification()
        }
    }

    /** 运行中手动重新开始（从第 1 拍、当前步频） */
    fun restart() {
        if (_status.value != MetronomeStatus.RUNNING) return
        mainHandler.removeCallbacks(debouncedRestart)
        engine.restart()
    }

    fun changeSound(sound: SoundType) {
        val updated = _settings.value.withSound(sound)
        _settings.value = updated
        prefs.saveSettings(updated)
        audioPlayer.ensureStarted(sound, updated.volumePercent)
    }

    fun changeVolume(percent: Int) {
        val updated = _settings.value.withVolume(percent)
        _settings.value = updated
        prefs.saveSettings(updated)
        audioPlayer.setVolume(percent)
    }

    // ---------------------------------------------------------------- 循环模式

    @Volatile
    private var intervalEngine: IntervalEngine? = null

    private var intervalJob: Job? = null

    /** 循环模式是否激活 */
    fun isIntervalActive(): Boolean = _interval.value.status != IntervalStatus.IDLE

    fun startInterval(workMinutes: Int, restMinutes: Int) {
        if (_interval.value.status == IntervalStatus.RUNNING) return
        // 基础模式若在运行则先停
        if (_status.value != MetronomeStatus.STOPPED) {
            engine.stop()
            _status.value = MetronomeStatus.STOPPED
        }
        val eng = IntervalEngine(workMinutes, restMinutes)
        intervalEngine = eng
        engine.start(_settings.value.bpm, tickListener) // 工作阶段开始响
        eng.start()
        _interval.value = eng.snapshot()
        try {
            promoteToForeground()
        } catch (_: SecurityException) {
        }
        acquireWakeLock()
        updateNotification()
        observeInterval(eng)
    }

    fun pauseInterval() {
        val eng = intervalEngine ?: return
        if (eng.snapshot().status != IntervalStatus.RUNNING) return
        eng.pause()
        engine.pause()
        _interval.value = eng.snapshot()
        updateNotification()
    }

    fun resumeInterval() {
        val eng = intervalEngine ?: return
        if (eng.snapshot().status != IntervalStatus.PAUSED) return
        eng.resume()
        if (eng.snapshot().phase == IntervalPhase.WORK) {
            engine.resume()
        } else {
            engine.start(_settings.value.bpm, tickListener)
        }
        _interval.value = eng.snapshot()
        updateNotification()
    }

    fun stopInterval() {
        intervalJob?.cancel()
        intervalJob = null
        intervalEngine?.stop()
        intervalEngine = null
        engine.stop()
        _interval.value = IntervalSnapshot()
        _status.value = MetronomeStatus.STOPPED
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun observeInterval(eng: IntervalEngine) {
        intervalJob?.cancel()
        intervalJob = scope.launch(Dispatchers.IO) {
            var lastPhase: IntervalPhase? = null
            while (isActive) {
                val snap = eng.snapshot()
                if (lastPhase != null && snap.status == IntervalStatus.RUNNING && snap.phase != lastPhase) {
                    onIntervalPhaseChange(snap.phase)
                }
                lastPhase = snap.phase
                _interval.value = snap
                checkCountdown(snap)
                delay(INTERVAL_POLL_MS)
            }
        }
    }

    /** 最后 5 秒倒计时：精准在窗口内触发，同一秒只播报一次 */
    private fun checkCountdown(snap: IntervalSnapshot) {
        if (snap.status != IntervalStatus.RUNNING) {
            lastCountdownSec = -1
            return
        }
        val next = CountdownTrigger.nextAnnouncement(snap.phaseRemainingSec, lastCountdownSec)
        if (next != null) {
            lastCountdownSec = next
            announceCountdown(next)
        } else if (snap.phaseRemainingSec > CountdownTrigger.COUNTDOWN_WINDOW_SEC) {
            lastCountdownSec = -1
        }
    }

    private fun announceCountdown(sec: Int) {
        Log.d(SPEECH_TAG, "announceCountdown: sec=$sec")
        // 优先系统 TTS 播报数字，不可用时回退内置语音
        if (!speakText(sec.toString())) {
            speechPlayer.speakRaw(countdownRaw(sec))
        }
    }

    private fun countdownRaw(sec: Int): Int = when (sec) {
        5 -> R.raw.countdown_5
        4 -> R.raw.countdown_4
        3 -> R.raw.countdown_3
        2 -> R.raw.countdown_2
        else -> R.raw.countdown_1
    }

    private fun onIntervalPhaseChange(phase: IntervalPhase) {
        when (phase) {
            IntervalPhase.WORK -> {
                engine.start(_settings.value.bpm, tickListener)
                speechPlayer.speakRaw(R.raw.cue_work_start)
            }
            IntervalPhase.REST -> {
                engine.stop()
                speechPlayer.speakRaw(R.raw.cue_rest)
            }
        }
        updateNotification()
    }

    // ---------------------------------------------------------------- 前台 / 通知

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // 通知权限被拒绝时仅跳过刷新
        }
    }

    private fun buildNotification(): Notification {
        val ctx = applicationContext
        val contentIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val s = _settings.value
        val iv = _interval.value
        val intervalActive = iv.status != IntervalStatus.IDLE
        val running = if (intervalActive) iv.status == IntervalStatus.RUNNING else _status.value == MetronomeStatus.RUNNING
        val text = when {
            intervalActive -> {
                val phase = if (iv.phase == IntervalPhase.WORK) "工作中" else "休息中"
                "循环 $phase · 第${iv.round}轮 · 剩余 ${iv.remainingText()}"
            }
            running -> getString(R.string.notification_running, s.bpm)
            else -> getString(R.string.notification_paused)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
        builder.setSmallIcon(R.drawable.ic_metronome)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        val pauseActionText = if (running) {
            getString(R.string.notification_action_pause)
        } else {
            getString(R.string.notification_action_resume)
        }
        val pauseActionIntent = Intent(ctx, MetronomeService::class.java).apply {
            action = if (running) ACTION_PAUSE else ACTION_RESUME
        }
        builder.addAction(buildAction(pauseActionText, pauseActionIntent))

        val stopIntent = Intent(ctx, MetronomeService::class.java).apply { action = ACTION_STOP }
        builder.addAction(buildAction(getString(R.string.notification_action_stop), stopIntent))
        return builder.build()
    }

    private fun buildAction(title: String, intent: Intent): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_metronome),
            title,
            servicePendingIntent(applicationContext, intent),
        ).build()

    private fun servicePendingIntent(ctx: Context, intent: Intent): PendingIntent =
        PendingIntent.getService(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    // ---------------------------------------------------------------- 唤醒锁

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunBeat::metronome")
            .apply { acquire(WAKELOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    // ---------------------------------------------------------------- 音频焦点（来电打断恢复）

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 来电/其他应用结束后自动恢复
                if (autoResumeAfterFocusLoss && _status.value == MetronomeStatus.PAUSED) {
                    autoResumeAfterFocusLoss = false
                    resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS,
            -> {
                // 打断期间暂停，保持节拍状态，便于恢复
                if (_status.value == MetronomeStatus.RUNNING) {
                    autoResumeAfterFocusLoss = change != AudioManager.AUDIOFOCUS_LOSS
                    pause()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
        audioFocusRequest = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        abandonAudioFocus()
        audioPlayer.release()
        speechPlayer.release()
        systemTts?.shutdown()
        systemTts = null
        super.onDestroy()
    }

    /** 供 UI 绑定的本地 Binder */
    inner class LocalBinder : Binder() {
        fun getService(): MetronomeService = this@MetronomeService
    }

    companion object {
        private const val TAG = "MetronomeService"
        private const val SPEECH_TAG = "RunBeatSpeech"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "metronome_running"
        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L // 6h
        private const val BPM_CHANGE_RESTART_DEBOUNCE_MS = 150L
        private const val INTERVAL_POLL_MS = 200L

        const val ACTION_START = "com.android.runbeat.action.START"
        const val ACTION_PAUSE = "com.android.runbeat.action.PAUSE"
        const val ACTION_RESUME = "com.android.runbeat.action.RESUME"
        const val ACTION_STOP = "com.android.runbeat.action.STOP"

        private const val EXTRA_BPM = "bpm"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_VOLUME = "volume"

        fun startIntent(context: Context, settings: MetronomeSettings): Intent =
            Intent(context, MetronomeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BPM, settings.bpm)
                putExtra(EXTRA_SOUND, settings.soundType.name)
                putExtra(EXTRA_VOLUME, settings.volumePercent)
            }

        fun pauseIntent(context: Context) = Intent(context, MetronomeService::class.java).apply { action = ACTION_PAUSE }
        fun resumeIntent(context: Context) = Intent(context, MetronomeService::class.java).apply { action = ACTION_RESUME }
        fun stopIntent(context: Context) = Intent(context, MetronomeService::class.java).apply { action = ACTION_STOP }

        private fun fromSettings(intent: Intent): MetronomeSettings = MetronomeSettings(
            bpm = intent.getIntExtra(EXTRA_BPM, MetronomeConstants.DEFAULT_BPM)
                .coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM),
            soundType = runCatching {
                SoundType.valueOf(intent.getStringExtra(EXTRA_SOUND) ?: SoundType.CLICK.name)
            }.getOrDefault(SoundType.CLICK),
            volumePercent = intent.getIntExtra(EXTRA_VOLUME, 100).coerceIn(0, 100),
        )
    }
}
