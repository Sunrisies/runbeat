package com.android.runbeat.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 循环引擎测试：状态切换准确性、循环稳定性、边界与暂停恢复 */
class IntervalEngineTest {

    private class FakeClock(var now: Long = 0L) {
        fun advanceMs(ms: Long) { now += ms }
    }

    private fun engine(work: Int, rest: Int, clock: FakeClock) = IntervalEngine(work, rest) { clock.now }

    @Test
    fun `starts in work phase round one`() {
        val c = FakeClock(1_000_000)
        val e = engine(1, 1, c)
        e.start()
        val s = e.snapshot()
        assertEquals(IntervalStatus.RUNNING, s.status)
        assertEquals(IntervalPhase.WORK, s.phase)
        assertEquals(1, s.round)
        assertEquals(60, s.phaseDurationSec)
        assertEquals(60, s.phaseRemainingSec)
    }

    @Test
    fun `1min run 1min walk cycles work then rest`() {
        val c = FakeClock(0)
        val e = engine(1, 1, c)
        e.start()
        assertEquals(IntervalPhase.WORK, e.snapshot().phase)
        c.advanceMs(60_000) // 恰好工作结束
        val s = e.snapshot()
        assertEquals(IntervalPhase.REST, s.phase)
        assertEquals(1, s.round)
        c.advanceMs(60_000) // 休息结束 → 第2轮工作
        val s2 = e.snapshot()
        assertEquals(IntervalPhase.WORK, s2.phase)
        assertEquals(2, s2.round)
    }

    @Test
    fun `1min run 2min walk combo`() {
        val c = FakeClock(0)
        val e = engine(1, 2, c)
        e.start()
        c.advanceMs(60_000)
        assertEquals(IntervalPhase.REST, e.snapshot().phase)
        assertEquals("休息时长应为120s", 120, e.snapshot().phaseDurationSec)
        c.advanceMs(120_000)
        assertEquals(IntervalPhase.WORK, e.snapshot().phase)
        assertEquals(2, e.snapshot().round)
    }

    @Test
    fun `2min run 1min walk combo`() {
        val c = FakeClock(0)
        val e = engine(2, 1, c)
        e.start()
        c.advanceMs(60_000)
        assertEquals("2分钟工作未结束应仍为WORK", IntervalPhase.WORK, e.snapshot().phase)
        c.advanceMs(60_000)
        assertEquals(IntervalPhase.REST, e.snapshot().phase)
        c.advanceMs(60_000)
        assertEquals(IntervalPhase.WORK, e.snapshot().phase)
    }

    @Test
    fun `boundary 60 minutes work rest`() {
        val c = FakeClock(0)
        val e = engine(60, 60, c)
        e.start()
        c.advanceMs(60 * 60_000L)
        assertEquals(IntervalPhase.REST, e.snapshot().phase)
        c.advanceMs(60 * 60_000L)
        assertEquals(IntervalPhase.WORK, e.snapshot().phase)
        assertEquals(2, e.snapshot().round)
    }

    @Test
    fun `snapshot catches up multiple missed phases`() {
        val c = FakeClock(0)
        val e = engine(1, 1, c)
        e.start()
        // 轮询漏了多个阶段, 一次 snapshot 应跨到当前阶段
        c.advanceMs(300_000) // 5 分钟 → 工作1+休息1+工作2+休息2+工作3(结束)
        val s = e.snapshot()
        assertEquals(IntervalPhase.REST, s.phase)
        assertEquals(3, s.round) // 第3轮休息
        assertTrue(s.phaseRemainingSec <= 60)
    }

    @Test
    fun `pause keeps remaining then resume continues`() {
        val c = FakeClock(0)
        val e = engine(1, 1, c)
        e.start()
        c.advanceMs(30_000) // 工作进行一半
        e.pause()
        assertEquals(IntervalStatus.PAUSED, e.snapshot().status)
        assertEquals(30, e.snapshot().phaseRemainingSec)
        // 暂停期间时间流逝不影响
        c.advanceMs(120_000)
        assertEquals(IntervalStatus.PAUSED, e.snapshot().status)
        assertEquals(30, e.snapshot().phaseRemainingSec)
        e.resume()
        c.advanceMs(30_000) // 恢复后30秒应到休息
        val s = e.snapshot()
        assertEquals(IntervalStatus.RUNNING, s.status)
        assertEquals(IntervalPhase.REST, s.phase)
    }

    @Test
    fun `stop returns to idle`() {
        val c = FakeClock(0)
        val e = engine(1, 1, c)
        e.start()
        c.advanceMs(30_000)
        e.stop()
        val s = e.snapshot()
        assertEquals(IntervalStatus.IDLE, s.status)
        assertEquals(0, s.round)
        // 停止后可重新开始
        e.start()
        assertEquals(IntervalPhase.WORK, e.snapshot().phase)
        assertEquals(1, e.snapshot().round)
    }

    @Test
    fun `invalid durations are rejected`() {
        val c = FakeClock(0)
        var threw = false
        try {
            IntervalEngine(0, 1) { c.now }
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("工作0分钟应被拒绝", threw)
        threw = false
        try {
            IntervalEngine(61, 1) { c.now }
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("工作61分钟应被拒绝", threw)
        threw = false
        try {
            IntervalEngine(1, -1) { c.now }
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("休息负数应被拒绝", threw)
    }
}
