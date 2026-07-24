package com.android.runbeat.metronome.core

/** 节拍器运行设置（纯数据，可序列化） */
data class MetronomeSettings(
    val bpm: Int = MetronomeConstants.DEFAULT_BPM,
    val soundType: SoundType = SoundType.CLICK,
    /** 音量百分比 0..100 */
    val volumePercent: Int = 100,
) {
    init {
        require(bpm in MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM) {
            "bpm out of range: $bpm"
        }
        require(volumePercent in 0..100) { "volume out of range: $volumePercent" }
    }

    fun withBpm(value: Int) = copy(bpm = value.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM))

    fun withSound(type: SoundType) = copy(soundType = type)

    fun withVolume(percent: Int) = copy(volumePercent = percent.coerceIn(0, 100))
}

/** 快捷预设 */
data class BpmPreset(
    val name: String,
    val bpm: Int,
)

object Presets {
    val BUILT_IN: List<BpmPreset> = listOf(
        BpmPreset("入门慢跑", MetronomeConstants.Presets.JOG_BPM),
        BpmPreset("标准配速", MetronomeConstants.Presets.CADENCE_BPM),
        BpmPreset("间歇冲刺", MetronomeConstants.Presets.SPRINT_BPM),
    )
}
