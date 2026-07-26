package com.android.runbeat.metronome.core

/**
 * 节拍器全局常量与公共类型。
 * 本文件及 core 包内代码不依赖任何 Android API，可在 JVM 上单测，
 * 便于未来通过 KMP 移植到 iOS。
 */
object MetronomeConstants {
    /** 最小步频（BPM） */
    const val MIN_BPM = 120

    /** 最大步频（BPM） */
    const val MAX_BPM = 200

    /** 精细调节步长（BPM） */
    const val BPM_STEP = 1

    /** 每小节节拍数（第 1 拍为重音） */
    const val BEATS_PER_BAR = 4

    /** 节拍触发精度要求（毫秒），用于测试断言 */
    const val TARGET_TICK_JITTER_MS = 50L

    /** 默认步频（入门慢跑） */
    const val DEFAULT_BPM = 160

    /** 内置预设 */
    object Presets {
        const val JOG_BPM = 160
        const val CADENCE_BPM = 180
        const val SPRINT_BPM = 190
    }
}

/** 节拍音色选项 */
enum class SoundType {
    CLICK,
    BEEP,
    WOOD,

    /** 参考跑步节拍音效（重音≈2.38kHz / 轻音≈727Hz） */
    RUN,
}

/** 节拍事件（由调度线程发出） */
data class TickEvent(
    val beatIndex: Int,
    val accent: Boolean,
    val scheduledAtNanos: Long,
)
