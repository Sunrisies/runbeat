package com.android.runbeat.metronome.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.android.runbeat.metronome.core.SoundType
import com.android.runbeat.metronome.core.TickEvent

/**
 * 基于 AudioTrack 的节拍音效播放器。
 *
 * 采用 MODE_STREAM 模式：每拍写入一段预渲染 PCM 短音。
 * 所有 AudioTrack 访问（play/write/setVolume/release）均通过 [lock] 串行化——
 * 节拍调度线程、循环模式提示音线程可能并发访问同一 track，
 * 不加锁会破坏原生层缓冲区状态，导致 SIGSEGV 崩溃。
 */
class MetronomeAudioPlayer {

    private var track: AudioTrack? = null

    @Volatile
    private var volumePercent = 100

    private val lock = Any()

    /**
     * 开始/切换音色时初始化播放器。
     * 首次创建时会预热音频管线（启动 + 写入静音），消除「点击开始后数秒才出声」。
     */
    fun ensureStarted(sound: SoundType, volumePercent: Int) {
        synchronized(lock) {
            this.volumePercent = volumePercent
            if (track != null) {
                setVolumeLocked(volumePercent)
                return
            }
            val t = buildTrack()
            t.setVolume(volumePercent / 100f)
            track = t
            prime(t)
        }
    }

    /** 预热：启动播放并填充一段静音，让音频 HAL/DSP 管线提前就绪 */
    private fun prime(t: AudioTrack) {
        try {
            t.play()
            val silence = ShortArray(TickSoundSynth.SAMPLE_RATE / 8) // 125ms
            t.write(silence, 0, silence.size)
        } catch (_: Exception) {
            // 预热失败不影响后续：首次真正播放时再启动
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBufferRaw = AudioTrack.getMinBufferSize(
            TickSoundSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val minBuffer = if (minBufferRaw > 0) minBufferRaw else TickSoundSynth.SAMPLE_RATE
        // 缓冲大小必须是帧大小的整数倍（mono/16-bit = 2 字节），否则 build() 抛异常
        val frameSizeBytes = 2
        var bufferBytes = maxOf(minBuffer, TickSoundSynth.SAMPLE_RATE / 5)
        if (bufferBytes % frameSizeBytes != 0) {
            bufferBytes += frameSizeBytes - bufferBytes % frameSizeBytes
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TickSoundSynth.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }

    fun setVolume(percent: Int) {
        synchronized(lock) {
            volumePercent = percent
            track?.setVolume(percent / 100f)
        }
    }

    private fun setVolumeLocked(percent: Int) {
        volumePercent = percent
        track?.setVolume(percent / 100f)
    }

    /** 播放一拍（锁内执行，避免与其他线程写 track 并发）。 */
    fun play(event: TickEvent, sound: SoundType, volumePercent: Int) {
        synchronized(lock) {
            setVolumeLocked(volumePercent)
            val t = track ?: return
            val buffer = TickSoundSynth.render(sound, event.accent)
            try {
                t.play()
                t.write(buffer, 0, buffer.size)
            } catch (_: IllegalStateException) {
                // 播放器处于不可用状态（如音频焦点被抢占），静默跳过本拍
            }
        }
    }

    /** 播放过渡/倒计时提示音（锁内执行）。 */
    fun playCue(workStart: Boolean) {
        synchronized(lock) {
            val t = track ?: return
            val buffer = TickSoundSynth.renderCue(workStart)
            try {
                t.play()
                t.write(buffer, 0, buffer.size)
            } catch (_: IllegalStateException) {
            }
        }
    }

    /** 释放硬件资源 */
    fun release() {
        synchronized(lock) {
            try {
                track?.pause()
                track?.flush()
                track?.release()
            } catch (_: Exception) {
            }
            track = null
        }
    }
}
