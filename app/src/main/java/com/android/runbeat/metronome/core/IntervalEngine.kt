package com.android.runbeat.metronome.core

/** 循环阶段 */
enum class IntervalPhase { WORK, REST }

/** 循环运行状态 */
enum class IntervalStatus { IDLE, RUNNING, PAUSED }

/** 循环快照（供 UI/服务展示） */
data class IntervalSnapshot(
    val status: IntervalStatus = IntervalStatus.IDLE,
    val phase: IntervalPhase = IntervalPhase.WORK,
    val round: Int = 0,
    val phaseElapsedSec: Long = 0L,
    val phaseRemainingSec: Long = 0L,
    val phaseDurationSec: Long = 0L,
) {
    /** 当前阶段总时长 mm:ss */
    fun durationText(): String = fmt(phaseDurationSec)

    /** 当前阶段剩余 mm:ss */
    fun remainingText(): String = fmt(phaseRemainingSec)

    private fun fmt(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)
}

/**
 * 自定义工作/休息间隔循环引擎（纯 Kotlin，可 JVM 单测）。
 * 基于单调时钟推进阶段：工作→休息→工作→…，每完成一轮 work 则轮次 +1。
 * [snapshot] 会一次性跨过所有已到期的阶段（防止轮询间隔内多阶段切换导致漏判）。
 */
class IntervalEngine(
    val workMinutes: Int,
    val restMinutes: Int,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    init {
        require(workMinutes in 1..60) { "workMinutes out of range: $workMinutes" }
        require(restMinutes in 1..60) { "restMinutes out of range: $restMinutes" }
    }

    private val workMs = workMinutes * 60_000L
    private val restMs = restMinutes * 60_000L

    private var status = IntervalStatus.IDLE
    private var phase = IntervalPhase.WORK
    private var round = 1
    private var phaseStartMs = 0L
    private var remainingMs = 0L

    fun start() {
        status = IntervalStatus.RUNNING
        phase = IntervalPhase.WORK
        round = 1
        phaseStartMs = clock()
    }

    fun pause() {
        if (status != IntervalStatus.RUNNING) return
        remainingMs = (phaseDurationMs() - (clock() - phaseStartMs)).coerceAtLeast(0)
        status = IntervalStatus.PAUSED
    }

    fun resume() {
        if (status != IntervalStatus.PAUSED) return
        phaseStartMs = clock() - (phaseDurationMs() - remainingMs)
        status = IntervalStatus.RUNNING
    }

    fun stop() {
        status = IntervalStatus.IDLE
        phase = IntervalPhase.WORK
        round = 1
        remainingMs = 0
    }

    /** 推进到当前时刻并返回最新快照 */
    fun snapshot(): IntervalSnapshot {
        when (status) {
            IntervalStatus.IDLE -> return IntervalSnapshot()
            IntervalStatus.PAUSED -> return IntervalSnapshot(
                status = IntervalStatus.PAUSED,
                phase = phase,
                round = round,
                phaseRemainingSec = remainingMs / 1000,
                phaseDurationSec = phaseDurationMs() / 1000,
            )
            IntervalStatus.RUNNING -> {
                val now = clock()
                while (true) {
                    val dur = phaseDurationMs()
                    val elapsed = now - phaseStartMs
                    if (elapsed < dur) {
                        return IntervalSnapshot(
                            status = IntervalStatus.RUNNING,
                            phase = phase,
                            round = round,
                            phaseElapsedSec = elapsed / 1000,
                            phaseRemainingSec = (dur - elapsed) / 1000,
                            phaseDurationSec = dur / 1000,
                        )
                    }
                    phase = if (phase == IntervalPhase.WORK) IntervalPhase.REST else IntervalPhase.WORK
                    if (phase == IntervalPhase.WORK) round++
                    phaseStartMs += dur
                }
            }
        }
    }

    private fun phaseDurationMs(): Long = if (phase == IntervalPhase.WORK) workMs else restMs
}
