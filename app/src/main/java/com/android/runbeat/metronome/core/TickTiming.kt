package com.android.runbeat.metronome.core

/**
 * 节拍时间的纯数学计算。所有时间基于单调时钟（System.nanoTime），
 * 节拍绝对时间 = 锚点 + n * 间隔，绝不基于上一拍完成时刻累加，
 * 从而保证长时间运行零累积误差。
 */
object TickTiming {

    const val NANOS_PER_MINUTE = 60_000_000_000L

    /** 由 BPM 计算单拍间隔（纳秒） */
    fun intervalNanos(bpm: Int): Long {
        require(bpm > 0) { "bpm must be positive, got $bpm" }
        return NANOS_PER_MINUTE / bpm
    }

    /** 由 BPM 计算单拍间隔（毫秒），供测试与 UI 展示使用 */
    fun intervalMillis(bpm: Int): Long = intervalNanos(bpm) / 1_000_000L

    /** 计算第 n 拍（从 0 开始）的绝对时间 */
    fun tickTime(anchorNanos: Long, tickNumber: Long, intervalNanos: Long): Long =
        anchorNanos + tickNumber * intervalNanos

    /** 是否重音拍：每个小节的第 1 拍 */
    fun isAccentBeat(beatIndex: Int): Boolean =
        beatIndex >= 0 && beatIndex % MetronomeConstants.BEATS_PER_BAR == 0
}
