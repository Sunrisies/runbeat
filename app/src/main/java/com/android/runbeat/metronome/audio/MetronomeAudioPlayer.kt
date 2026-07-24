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
 * 采用 MODE_STREAM 模式：调度线程每拍写入一段预渲染 PCM 短音。
 * 短音长度（45-90ms）远小于节拍间隔（≥300ms），不会发生数据堆积；
 * 音量通过 AudioTrack 线性增益控制，输出一致性好。
 */
class MetronomeAudioPlayer {

    private var track: AudioTrack? = null

    @Volatile
    private var volumePercent = 100

    private val lock = Any()

    /** 开始/切换音色时初始化播放器 */
    fun ensureStarted(sound: SoundType, volumePercent: Int) {
        synchronized(lock) {
            this.volumePercent = volumePercent
            if (track != null) return
            val minBufferRaw = AudioTrack.getMinBufferSize(
                TickSoundSynth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val minBuffer = if (minBufferRaw > 0) minBufferRaw else TickSoundSynth.SAMPLE_RATE
            // 缓冲大小必须是帧大小的整数倍（mono/16-bit = 2 字节），否则 build() 抛异常
            val frameSizeBytes = 2
            var bufferBytes = maxOf(minBuffer, TickSoundSynth.SAMPLE_RATE / 2)
            if (bufferBytes % frameSizeBytes != 0) {
                bufferBytes += frameSizeBytes - bufferBytes % frameSizeBytes
            }
            val t = AudioTrack.Builder()
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
            t.setVolume(volumePercent / 100f)
            track = t
        }
    }

    fun setVolume(percent: Int) {
        volumePercent = percent
        track?.setVolume(percent / 100f)
    }

    /** 播放一拍。若音量/音色变化则应用最新值。 */
    fun play(event: TickEvent, sound: SoundType, volumePercent: Int) {
        setVolume(volumePercent)
        val t = track ?: return
        val buffer = TickSoundSynth.render(sound, event.accent)
        try {
            t.play()
            t.write(buffer, 0, buffer.size)
        } catch (_: IllegalStateException) {
            // 播放器处于不可用状态（如音频焦点被抢占），静默跳过本拍
        }
    }

    /** 暂停并释放硬件资源 */
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
