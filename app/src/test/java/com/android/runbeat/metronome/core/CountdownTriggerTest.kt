package com.android.runbeat.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 阶段末倒计时触发器测试：窗口判定、每秒播报、去重 */
class CountdownTriggerTest {

    @Test
    fun `no announcement outside the last five seconds`() {
        assertNull(CountdownTrigger.nextAnnouncement(60, -1))
        assertNull(CountdownTrigger.nextAnnouncement(6, -1))
        assertNull(CountdownTrigger.nextAnnouncement(0, -1))
        assertNull(CountdownTrigger.nextAnnouncement(-1, -1))
    }

    @Test
    fun `announces five to one once per second`() {
        var last = -1
        // 进入窗口(剩余5)开始播报
        assertEquals(5, CountdownTrigger.nextAnnouncement(5, last))
        last = 5
        // 同一秒不重复
        assertNull(CountdownTrigger.nextAnnouncement(5, last))
        assertEquals(4, CountdownTrigger.nextAnnouncement(4, last))
        last = 4
        assertEquals(3, CountdownTrigger.nextAnnouncement(3, last))
        last = 3
        assertEquals(2, CountdownTrigger.nextAnnouncement(2, last))
        last = 2
        assertEquals(1, CountdownTrigger.nextAnnouncement(1, last))
        last = 1
        // 阶段结束(剩余0)不再播报
        assertNull(CountdownTrigger.nextAnnouncement(0, last))
    }

    @Test
    fun `phase boundary resets the sequence`() {
        // 第1轮休息末播报 3,2,1
        assertEquals(3, CountdownTrigger.nextAnnouncement(3, -1))
        assertEquals(2, CountdownTrigger.nextAnnouncement(2, 3))
        assertEquals(1, CountdownTrigger.nextAnnouncement(1, 2))
        // 进入下一轮工作(剩余60) → 重置, 窗口外不播报
        assertNull(CountdownTrigger.nextAnnouncement(60, 1))
        // 下一轮工作末重新从5开始
        assertEquals(5, CountdownTrigger.nextAnnouncement(5, -1))
    }

    @Test
    fun `works identically for work and rest phases`() {
        // 触发器与阶段无关, 仅依赖剩余秒数 → 工作/休息逻辑一致
        assertEquals(5, CountdownTrigger.nextAnnouncement(5, -1))
        assertEquals(5, CountdownTrigger.nextAnnouncement(5, -1))
    }
}
