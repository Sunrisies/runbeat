package com.android.runbeat.metronome.core

import java.util.concurrent.atomic.AtomicBoolean

/** 节拍器运行状态 */
enum class MetronomeStatus { STOPPED, RUNNING, PAUSED }

/** 节拍回调（在调度线程上调用） */
fun interface MetronomeTickListener {
    fun onTick(event: TickEvent)
}

/**
 * 节拍器核心引擎（纯 Kotlin，可 JVM 单测）。
 *
 * 精度策略：
 *  - 使用单调时钟 [clock]（默认 System.nanoTime）维护绝对节拍时间 [nextTickNanos]。
 *  - 调度循环每次醒来后以「当前时间 >= 目标时间」判定触发，触发后仅做
 *    `nextTickNanos += intervalNanos`，间隔恒定且与调度滞后无关，无漂移。
 *  - 剩余时间大于 [SPIN_THRESHOLD_NANOS] 时 Thread.sleep，否则自旋等待，
 *    保证触发误差在毫秒级（远小于 ±50ms 要求）。
 *
 * 线程模型：start/resume 各启动一个调度线程；pause/stop 中断并等待其退出。
 */
class MetronomeEngine(
    private val clock: () -> Long = { System.nanoTime() },
) {
    @Volatile
    var status: MetronomeStatus = MetronomeStatus.STOPPED
        private set

    @Volatile
    var bpm: Int = MetronomeConstants.DEFAULT_BPM
        set(value) {
            val clamped = value.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
            if (field == clamped) return
            field = clamped
            // 运行中修改步频：从当前时刻起按新间隔安排下一拍，保持小节重音不失步
            if (status == MetronomeStatus.RUNNING) {
                val now = clock()
                val anchor = maxOf(now, nextTickNanos)
                nextTickNanos = anchor + TickTiming.intervalNanos(clamped)
            }
        }

    @Volatile
    private var nextTickNanos = 0L

    @Volatile
    private var beatIndex = 0

    private val running = AtomicBoolean(false)

    @Volatile
    private var thread: Thread? = null

    private val stateLock = Object()

    private fun snapshotListener(): MetronomeTickListener? = listener

    @Volatile
    private var listener: MetronomeTickListener? = null

    /** 当前小节内拍序（第 1 拍为重音） */
    fun currentBeatInBar(): Int = beatIndex % MetronomeConstants.BEATS_PER_BAR + 1

    /** 当前累计拍数 */
    fun currentBeatIndex(): Int = beatIndex

    /** 启动（从第 1 拍立即开始） */
    fun start(bpm: Int, listener: MetronomeTickListener) {
        synchronized(stateLock) {
            stopInternal()
            this.bpm = bpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
            this.listener = listener
            beatIndex = 0
            nextTickNanos = clock() // 第 1 拍立即触发，提供即时反馈
            startThread()
        }
    }

    /** 暂停（保留当前拍序与步频） */
    fun pause() {
        synchronized(stateLock) {
            if (status != MetronomeStatus.RUNNING) return
            running.set(false)
            interruptAndJoin()
            status = MetronomeStatus.PAUSED
        }
    }

    /** 恢复（保持小节重音同步，下一拍按一个完整间隔后触发） */
    fun resume() {
        synchronized(stateLock) {
            if (status != MetronomeStatus.PAUSED) return
            running.set(true)
            nextTickNanos = clock() + TickTiming.intervalNanos(bpm)
            startThread()
        }
    }

    /**
     * 运行中立即重启节拍：从第 1 拍、按当前步频重新开始。
     * 唤醒调度线程使其马上触发新节拍，让步频修改即刻生效。
     */
    fun restart() {
        synchronized(stateLock) {
            if (status != MetronomeStatus.RUNNING) return
            beatIndex = 0
            nextTickNanos = clock()
            thread?.interrupt()
        }
    }

    /** 停止并清零拍序 */
    fun stop() {
        synchronized(stateLock) {
            stopInternal()
        }
    }

    private fun startThread() {
        running.set(true)
        status = MetronomeStatus.RUNNING
        val t = Thread(::runLoop, "metronome-scheduler")
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
        thread = t
        t.start()
    }

    private fun stopInternal() {
        running.set(false)
        interruptAndJoin()
        beatIndex = 0
        nextTickNanos = 0L
        status = MetronomeStatus.STOPPED
    }

    /**
     * 中断并等待调度线程退出。
     * 必须在 old 调度线程完全退出后再启动新线程，否则快速暂停/重启会出现
     * 双线程同时调度（旧线程误读新的 running 标志），导致重复节拍与拍序错乱。
     */
    private fun interruptAndJoin() {
        val t = thread
        thread = null
        if (t == null) return
        t.interrupt()
        try {
            t.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun runLoop() {
        val l = snapshotListener() ?: return
        while (running.get()) {
            val now = clock()
            val target = nextTickNanos
            if (now >= target) {
                val index = beatIndex
                l.onTick(TickEvent(index, TickTiming.isAccentBeat(index), target))
                beatIndex = index + 1
                nextTickNanos = target + TickTiming.intervalNanos(bpm)
            } else {
                val remaining = target - now
                if (remaining > SPIN_THRESHOLD_NANOS) {
                    val sleepMillis = (remaining - SPIN_THRESHOLD_NANOS) / 1_000_000L
                    try {
                        Thread.sleep(sleepMillis.coerceAtLeast(1))
                    } catch (_: InterruptedException) {
                        // 退出条件由 running 标志控制
                    }
                } else {
                    Thread.yield()
                }
            }
        }
    }

    companion object {
        /** 剩余时间小于该值时改为自旋等待（保证毫秒级触发精度） */
        const val SPIN_THRESHOLD_NANOS = 8_000_000L // 8ms
    }
}
