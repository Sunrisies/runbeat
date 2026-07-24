package com.android.runbeat.metronome.audio

import com.android.runbeat.metronome.core.SoundType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 程序化合成节拍音效的 PCM 数据（16-bit 单声道）。
 * 无需内置音频资源文件，避免包体膨胀并保证不同设备输出一致。
 * 3 种音色：咔嗒(Click) / 哔声(Beep) / 木鱼(Wood)。
 * 每 4 拍第 1 拍为重音：提高基频 + 提升增益，便于在嘈杂环境中辨识。
 */
object TickSoundSynth {

    const val SAMPLE_RATE = 44_100

    private const val ACCENT_GAIN = 1.0
    private const val NORMAL_GAIN = 0.75

    /** 预渲染 6 种组合（音色 × 是否重音），避免反复计算 */
    private val cache: Map<SoundType, Map<Boolean, ShortArray>> = SoundType.entries.associateWith { sound ->
        mapOf(false to synthesize(sound, accent = false), true to synthesize(sound, accent = true))
    }

    fun render(sound: SoundType, accent: Boolean): ShortArray =
        cache.getValue(sound).getValue(accent)

    private fun synthesize(sound: SoundType, accent: Boolean): ShortArray {
        val gain = if (accent) ACCENT_GAIN else NORMAL_GAIN
        return when (sound) {
            SoundType.CLICK -> tone(
                baseFreq = if (accent) 1500.0 else 1000.0,
                durationSec = 0.045,
                decay = 28.0,
                harmonics = intArrayOf(1, 3),
                gain = gain,
            )
            SoundType.BEEP -> tone(
                baseFreq = if (accent) 1318.5 else 880.0,
                durationSec = 0.09,
                decay = 14.0,
                harmonics = intArrayOf(1),
                gain = gain,
            )
            SoundType.WOOD -> tone(
                baseFreq = if (accent) 760.0 else 480.0,
                durationSec = 0.05,
                decay = 34.0,
                harmonics = intArrayOf(1, 2, 4),
                gain = gain,
            )
        }
    }

    private fun tone(
        baseFreq: Double,
        durationSec: Double,
        decay: Double,
        harmonics: IntArray,
        gain: Double,
    ): ShortArray {
        val n = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(n)
        val twoPiF = 2.0 * PI * baseFreq
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for (h in harmonics) {
                sample += sin(twoPiF * h * t) / (h * h)
            }
            // 指数衰减包络，尖锐短促，适合节拍提示
            val envelope = exp(-decay * t)
            val s = sample * envelope * gain
            out[i] = (s * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
