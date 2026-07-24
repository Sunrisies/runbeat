package com.android.runbeat.metronome.audio

import com.android.runbeat.metronome.core.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** PCM 音效合成测试：3 种音色 × 重音/轻音均有效，重音增益更高。 */
class TickSoundSynthTest {

    @Test
    fun `all six combinations render non-empty pcm`() {
        SoundType.entries.forEach { sound ->
            listOf(false, true).forEach { accent ->
                val pcm = TickSoundSynth.render(sound, accent)
                assertNotNull(pcm)
                assertTrue("$sound/$accent 应生成采样数据", pcm.isNotEmpty())
            }
        }
    }

    @Test
    fun `accent beat is louder than soft beat`() {
        SoundType.entries.forEach { sound ->
            val softPeak = TickSoundSynth.render(sound, accent = false).maxOf { abs(it.toInt()) }
            val accentPeak = TickSoundSynth.render(sound, accent = true).maxOf { abs(it.toInt()) }
            assertTrue(
                "$sound 重音增益应高于轻音 (soft=$softPeak, accent=$accentPeak)",
                accentPeak > softPeak,
            )
        }
    }

    @Test
    fun `sample values stay within pcm 16-bit range`() {
        SoundType.entries.forEach { sound ->
            listOf(false, true).forEach { accent ->
                val pcm = TickSoundSynth.render(sound, accent)
                pcm.forEach { s ->
                    assertTrue("采样值越界: $s", s.toInt() in -Short.MAX_VALUE..Short.MAX_VALUE)
                }
            }
        }
    }

    @Test
    fun `rendered length scales with duration`() {
        // 哔声 90ms 最长，咔嗒/木鱼更短
        val beep = TickSoundSynth.render(SoundType.BEEP, false)
        val click = TickSoundSynth.render(SoundType.CLICK, false)
        assertTrue(beep.size > click.size)
        // 90ms @ 44100Hz ≈ 3969 个采样点
        val expected = TickSoundSynth.SAMPLE_RATE * 90 / 1000
        assertEquals(expected.toDouble(), beep.size.toDouble(), (expected / 100).toDouble())
    }
}
