package com.android.runbeat.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 引擎状态机与实时节拍精度测试。 */
class MetronomeEngineTest {

    private fun startAndCollect(
        engine: MetronomeEngine,
        bpm: Int,
        count: Int,
        timeoutMs: Long = 5000,
    ): List<TickEvent> {
        val queue = LinkedBlockingQueue<TickEvent>()
        val done = CountDownLatch(count)
        engine.start(bpm) { e ->
            queue.offer(e)
            done.countDown()
        }
        assertTrue("收集 $count 个节拍超时", done.await(timeoutMs, TimeUnit.MILLISECONDS))
        return (0 until count).map { queue.poll(500, TimeUnit.MILLISECONDS)!! }
    }

    @Test
    fun `first tick fires immediately for instant feedback`() {
        val engine = MetronomeEngine()
        val start = System.nanoTime()
        val events = startAndCollect(engine, 160, 1, timeoutMs = 300)
        assertEquals(0, events[0].beatIndex)
        assertTrue("第一拍应立即触发", System.nanoTime() - start < 100_000_000L)
    }

    @Test
    fun `accent flag forwarded on every fourth beat`() {
        val engine = MetronomeEngine()
        val events = startAndCollect(engine, 200, 9) // 200bpm → 2.4s
        events.forEach { e ->
            assertEquals(TickTiming.isAccentBeat(e.beatIndex), e.accent)
        }
        assertTrue(events[0].accent)
        assertTrue(events[4].accent)
        assertTrue(events[8].accent)
        assertFalse(events[1].accent)
        assertFalse(events[3].accent)
        engine.stop()
    }

    @Test
    fun `real time jitter stays within 50ms at max bpm`() {
        val engine = MetronomeEngine()
        val arrivals = ArrayList<Long>()
        val done = CountDownLatch(7)
        engine.start(200) {
            arrivals.add(System.nanoTime())
            done.countDown()
        }
        assertTrue(done.await(5000, TimeUnit.MILLISECONDS))
        val interval = TickTiming.intervalNanos(200)
        val tolerance = MetronomeConstants.TARGET_TICK_JITTER_MS * 1_000_000L
        for (i in 1 until arrivals.size) {
            val delta = arrivals[i] - arrivals[i - 1]
            assertTrue(
                "第 ${i} 拍间隔偏差过大: ${delta / 1_000_000}ms",
                kotlin.math.abs(delta - interval) <= tolerance,
            )
        }
        engine.stop()
    }

    @Test
    fun `pause stops ticks and resume continues measure`() {
        val engine = MetronomeEngine()
        val queue = LinkedBlockingQueue<TickEvent>()
        val first = CountDownLatch(1)
        engine.start(160) { e ->
            queue.offer(e)
            first.countDown()
        }
        assertTrue(first.await(1000, TimeUnit.MILLISECONDS))
        assertEquals(MetronomeStatus.RUNNING, engine.status)
        // 排空第一拍（首拍立即触发）
        assertEquals(0, queue.poll(100, TimeUnit.MILLISECONDS)!!.beatIndex)

        engine.pause()
        assertEquals(MetronomeStatus.PAUSED, engine.status)
        Thread.sleep(400)
        assertNull("暂停期间不应再有节拍", queue.poll(50, TimeUnit.MILLISECONDS))
        val pausedIndex = engine.currentBeatIndex()

        engine.resume()
        assertEquals(MetronomeStatus.RUNNING, engine.status)
        val resumedEvent = queue.poll(1500, TimeUnit.MILLISECONDS)
        assertNotNull("恢复后应有节拍触发", resumedEvent)
        // 恢复后拍序连续，保持小节重音同步
        assertEquals(pausedIndex, resumedEvent.beatIndex)
        engine.stop()
    }

    @Test
    fun `reset returns to stopped and clears beat counter`() {
        val engine = MetronomeEngine()
        startAndCollect(engine, 200, 5)
        engine.stop()
        assertEquals(MetronomeStatus.STOPPED, engine.status)
        assertEquals(0, engine.currentBeatIndex())
        // 重新开始后从第 1 拍重新计
        val events = startAndCollect(engine, 160, 1)
        assertEquals(0, events[0].beatIndex)
        engine.stop()
    }

    @Test
    fun `restart while running resets to beat one immediately`() {
        val engine = MetronomeEngine()
        val queue = LinkedBlockingQueue<TickEvent>()
        val first = CountDownLatch(1)
        engine.start(160) { e ->
            queue.offer(e)
            first.countDown()
        }
        assertTrue(first.await(1000, TimeUnit.MILLISECONDS))
        assertEquals(0, queue.poll(100, TimeUnit.MILLISECONDS)!!.beatIndex)
        engine.bpm = 200

        engine.restart()
        assertEquals(MetronomeStatus.RUNNING, engine.status)
        val restarted = queue.poll(1000, TimeUnit.MILLISECONDS)
        assertNotNull("重启后应立即触发新节拍", restarted)
        assertEquals("重启后应从第 1 拍重新计", 0, restarted.beatIndex)
        assertTrue(restarted.accent)
        engine.stop()
    }

    @Test
    fun `bpm setter clamps to supported range`() {
        val engine = MetronomeEngine()
        engine.bpm = 50
        assertEquals(MetronomeConstants.MIN_BPM, engine.bpm)
        engine.bpm = 999
        assertEquals(MetronomeConstants.MAX_BPM, engine.bpm)
        engine.bpm = 173
        assertEquals(173, engine.bpm)
    }

    @Test
    fun `bpm change while running applies new cadence`() {
        val engine = MetronomeEngine()
        val arrivals = ArrayList<Long>()
        val first = CountDownLatch(1)
        val enough = CountDownLatch(3)
        engine.start(160) {
            arrivals.add(System.nanoTime())
            first.countDown()
            enough.countDown()
        }
        assertTrue(first.await(1000, TimeUnit.MILLISECONDS))
        engine.bpm = 200 // 375ms → 300ms
        assertTrue(enough.await(3000, TimeUnit.MILLISECONDS))
        // 改变步频后相邻两拍应按新间隔（约300ms）触发
        val a = arrivals[1]
        val b = arrivals[2]
        val delta = b - a
        assertTrue(
            "改变步频后间隔应接近300ms，实际 ${delta / 1_000_000}ms",
            kotlin.math.abs(delta - 300_000_000L) <= 80_000_000L,
        )
        engine.stop()
    }

    @Test
    fun `settings bounds are enforced`() {
        val s = MetronomeSettings().withBpm(50).withVolume(-5)
        assertEquals(MetronomeConstants.MIN_BPM, s.bpm)
        assertEquals(0, s.volumePercent)
        val s2 = s.withBpm(250).withVolume(150)
        assertEquals(MetronomeConstants.MAX_BPM, s2.bpm)
        assertEquals(100, s2.volumePercent)
    }

    @Test
    fun `built-in presets match spec`() {
        val byBpm = Presets.BUILT_IN.associateBy { it.bpm }
        assertEquals(160, byBpm[160]?.bpm)
        assertEquals(180, byBpm[180]?.bpm)
        assertEquals(190, byBpm[190]?.bpm)
        assertEquals("入门慢跑", byBpm[160]?.name)
        assertEquals("标准配速", byBpm[180]?.name)
        assertEquals("间歇冲刺", byBpm[190]?.name)
    }
}
