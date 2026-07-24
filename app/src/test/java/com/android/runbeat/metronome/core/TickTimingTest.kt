package com.android.runbeat.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TickTiming 纯数学测试：覆盖 120-200 全区间、边界值、零漂移性质。 */
class TickTimingTest {

    @Test
    fun `interval at known bpm`() {
        assertEquals(500_000_000L, TickTiming.intervalNanos(120))
        assertEquals(375_000_000L, TickTiming.intervalNanos(160))
        assertEquals(333_333_333L, TickTiming.intervalNanos(180))
        assertEquals(300_000_000L, TickTiming.intervalNanos(200))
    }

    @Test
    fun `interval across full range is positive and monotonic`() {
        var prev = Long.MAX_VALUE
        for (bpm in MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM) {
            val interval = TickTiming.intervalNanos(bpm)
            assertTrue("bpm=$bpm", interval > 0)
            assertTrue("bpm=$bpm should decrease as bpm grows", interval <= prev)
            prev = interval
        }
    }

    @Test
    fun `tick times are exact multiples of anchor and interval`() {
        val anchor = 1_000_000_000L
        val interval = TickTiming.intervalNanos(180)
        assertEquals(anchor, TickTiming.tickTime(anchor, 0, interval))
        assertEquals(anchor + interval, TickTiming.tickTime(anchor, 1, interval))
        assertEquals(anchor + 9L * interval, TickTiming.tickTime(anchor, 9, interval))
    }

    @Test
    fun `no drift over one million ticks`() {
        val anchor = 123_456_789L
        val interval = TickTiming.intervalNanos(160)
        val n = 1_000_000L
        // 长时运行后最后一拍时间与理论值完全一致（纯加法，无累积误差）
        assertEquals(anchor + n * interval, TickTiming.tickTime(anchor, n, interval))
        // 相邻两拍间隔恒为 interval
        assertEquals(
            interval,
            TickTiming.tickTime(anchor, n, interval) - TickTiming.tickTime(anchor, n - 1, interval),
        )
    }

    @Test
    fun `accent beat is every fourth beat starting from zero`() {
        assertTrue(TickTiming.isAccentBeat(0))
        assertFalse(TickTiming.isAccentBeat(1))
        assertFalse(TickTiming.isAccentBeat(2))
        assertFalse(TickTiming.isAccentBeat(3))
        assertTrue(TickTiming.isAccentBeat(4))
        assertTrue(TickTiming.isAccentBeat(8))
        assertTrue(TickTiming.isAccentBeat(400))
        assertFalse(TickTiming.isAccentBeat(401))
        assertFalse(TickTiming.isAccentBeat(-1))
    }

    @Test
    fun `boundary bpm values behave correctly`() {
        assertEquals(MetronomeConstants.MIN_BPM, 120)
        assertEquals(MetronomeConstants.MAX_BPM, 200)
        // 最高步频间隔更短（更快）
        val minInterval = TickTiming.intervalNanos(MetronomeConstants.MAX_BPM)
        val maxInterval = TickTiming.intervalNanos(MetronomeConstants.MIN_BPM)
        assertTrue(minInterval < maxInterval)
        // 200bpm 单拍 300ms，远大于 50ms 精度预算
        assertEquals(300_000_000L, minInterval)
    }
}
