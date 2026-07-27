package com.android.runbeat.metronome.settings

import android.content.Context
import android.content.SharedPreferences
import com.android.runbeat.metronome.core.MetronomeConstants
import com.android.runbeat.metronome.core.MetronomeSettings
import com.android.runbeat.metronome.core.SoundType

/** 节拍器设置的 SharedPreferences 持久化。 */
class MetronomePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): MetronomeSettings = MetronomeSettings(
        bpm = prefs.getInt(KEY_BPM, MetronomeConstants.DEFAULT_BPM)
            .coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM),
        soundType = runCatching {
            SoundType.valueOf(prefs.getString(KEY_SOUND, null) ?: SoundType.CLICK.name)
        }.getOrDefault(SoundType.CLICK),
        volumePercent = prefs.getInt(KEY_VOLUME, 100).coerceIn(0, 100),
    )

    fun saveSettings(settings: MetronomeSettings) {
        prefs.edit()
            .putInt(KEY_BPM, settings.bpm)
            .putString(KEY_SOUND, settings.soundType.name)
            .putInt(KEY_VOLUME, settings.volumePercent)
            .apply()
    }

    /** 自定义预设 BPM，未保存时为 null */
    fun loadCustomPresetBpm(): Int? =
        prefs.getInt(KEY_CUSTOM_BPM, -1).takeIf { it in MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM }

    fun saveCustomPresetBpm(bpm: Int) {
        prefs.edit().putInt(KEY_CUSTOM_BPM, bpm).apply()
    }

    /** 循环模式：上次工作/休息时长（分钟） */
    fun loadIntervalWorkMinutes(): Int = prefs.getInt(KEY_INTERVAL_WORK, 1).coerceIn(1, 60)
    fun loadIntervalRestMinutes(): Int = prefs.getInt(KEY_INTERVAL_REST, 1).coerceIn(1, 60)

    fun saveIntervalMinutes(work: Int, rest: Int) {
        prefs.edit()
            .putInt(KEY_INTERVAL_WORK, work.coerceIn(1, 60))
            .putInt(KEY_INTERVAL_REST, rest.coerceIn(1, 60))
            .apply()
    }

    /** 服务恢复标记：用于进程被杀后前台服务重建时恢复运行态 */
    var wasRunning: Boolean
        get() = prefs.getBoolean(KEY_WAS_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_RUNNING, value).apply()

    companion object {
        const val PREFS_NAME = "metronome_prefs"
        private const val KEY_BPM = "bpm"
        private const val KEY_SOUND = "sound"
        private const val KEY_VOLUME = "volume"
        private const val KEY_CUSTOM_BPM = "custom_bpm"
        private const val KEY_INTERVAL_WORK = "interval_work"
        private const val KEY_INTERVAL_REST = "interval_rest"
        private const val KEY_WAS_RUNNING = "was_running"
    }
}
