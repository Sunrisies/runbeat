package com.android.runbeat.metronome.audio

import com.android.runbeat.metronome.core.SoundType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 程序化合成节拍音效的 PCM 数据（16-bit 单声道）。
 *
 * 「咔嗒」音色为参考音频(runbeat_synth_1min.wav)拟合的阻尼正弦模型：
 *  - 重音(每4拍第1拍) ≈ 2.38kHz 高音族
 *  - 轻音            ≈ 727Hz  低音族
 * 频率(音调)通过 pitchRatio 整体缩放，音量在播放层控制。
 */
object TickSoundSynth {

    const val SAMPLE_RATE = 44_100

    private const val ACCENT_GAIN = 1.0
    private const val SOFT_GAIN = 0.7

    /** 参考高音(重音) click 阻尼正弦模型：2441/2379/2115/2457/2350Hz 族 */
    private val ACCENT_SINES = arrayOf(
        Sine(2441.5, 0.009202, 0.02108, -1.929),
        Sine(2379.0, 0.005078, 0.05018, 1.1112),
        Sine(2115.0, 0.057678, 0.00015, -1.4348),
        Sine(2457.1, 0.002623, 0.01263, 1.7236),
        Sine(2350.9, 0.001814, 0.15126, 2.6408),
    )

    /** 参考低音(轻音) click 阻尼正弦模型：718/770/736/645/690Hz 族 */
    private val SOFT_SINES = arrayOf(
        Sine(717.9, 0.011215, 0.00945, 1.387),
        Sine(770.0, 0.002993, 0.00042, -3.0991),
        Sine(736.5, 0.000276, 0.3, -0.0589),
        Sine(644.6, 0.000745, 0.0178, -0.0174),
        Sine(690.1, 0.000516, 0.3, 2.6677),
    )

    data class Sine(val freqHz: Double, val amp: Double, val tauSec: Double, val phaseRad: Double)

    /**
     * 渲染一拍 PCM。
     */
    fun render(sound: SoundType, accent: Boolean): ShortArray =
        when (sound) {
            SoundType.CLICK -> tone(
                baseFreq = if (accent) 1500.0 else 1000.0,
                durationSec = 0.045,
                decay = 28.0,
                harmonics = intArrayOf(1, 3),
                gain = if (accent) ACCENT_GAIN else SOFT_GAIN,
            )
            SoundType.BEEP -> tone(
                baseFreq = if (accent) 1318.5 else 880.0,
                durationSec = 0.09,
                decay = 14.0,
                harmonics = intArrayOf(1),
                gain = if (accent) ACCENT_GAIN else SOFT_GAIN,
            )
            SoundType.WOOD -> tone(
                baseFreq = if (accent) 760.0 else 480.0,
                durationSec = 0.05,
                decay = 34.0,
                harmonics = intArrayOf(1, 2, 4),
                gain = if (accent) ACCENT_GAIN else SOFT_GAIN,
            )
            // 参考跑步节拍音效：重音≈2.38kHz高音族 / 轻音≈727Hz低音族
            SoundType.RUN -> referenceClick(accent)
        }

    private fun referenceClick(accent: Boolean): ShortArray {
        val sines = if (accent) ACCENT_SINES else SOFT_SINES
        val n = (SAMPLE_RATE * 0.06).toInt()
        val raw = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for (s in sines) {
                sample += s.amp * exp(-t / s.tauSec) * sin(2.0 * PI * s.freqHz * t + s.phaseRad)
            }
            raw[i] = sample
        }
        return normalize(raw, if (accent) ACCENT_GAIN else SOFT_GAIN)
    }

    private fun tone(
        baseFreq: Double,
        durationSec: Double,
        decay: Double,
        harmonics: IntArray,
        gain: Double,
    ): ShortArray {
        val n = (SAMPLE_RATE * durationSec).toInt()
        val raw = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for (h in harmonics) {
                sample += sin(2.0 * PI * baseFreq * h * t) / (h * h)
            }
            raw[i] = sample * exp(-decay * t)
        }
        return normalize(raw, gain)
    }

    private fun normalize(raw: DoubleArray, targetPeak: Double): ShortArray {
        val peak = raw.maxOrNull()?.let { abs(it) } ?: 0.0
        val scale = if (peak > 1e-9) targetPeak * Short.MAX_VALUE / peak else 0.0
        return ShortArray(raw.size) { (raw[it] * scale).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
