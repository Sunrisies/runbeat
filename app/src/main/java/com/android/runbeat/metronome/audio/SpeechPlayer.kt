package com.android.runbeat.metronome.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * 内置语音播报播放器。
 * 播放预先合成好的倒计时数字/提示词 PCM（res/raw）。
 * 使用独立 AudioTrack，与节拍器 track 完全隔离（无并发访问冲突）。
 * 不依赖系统 TTS，离线可用。
 */
class SpeechPlayer(private val context: Context) {

    private var track: AudioTrack? = null

    private val cache = HashMap<Int, ShortArray>()

    private val lock = Any()

    /** 预热：尽早创建音频管线，消除首次发声延迟 */
    fun ensureStarted() {
        synchronized(lock) {
            if (track != null) return
            track = buildTrack()
        }
    }

    /** 播报一段预合成语音（res/raw 原始 PCM，16bit 单声道 44100Hz） */
    fun speakRaw(resId: Int) {
        synchronized(lock) {
            val t = track ?: return
            val pcm = cache.getOrPut(resId) { loadPcm(resId) }
            try {
                t.play()
                t.write(pcm, 0, pcm.size)
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        synchronized(lock) {
            try {
                track?.pause()
                track?.flush()
                track?.release()
            } catch (_: Exception) {
            }
            track = null
            cache.clear()
        }
    }

    private fun loadPcm(resId: Int): ShortArray {
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        val n = bytes.size / 2
        val out = ShortArray(n)
        for (i in 0 until n) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() shl 8
            out[i] = (lo or hi).toShort()
        }
        return out
    }

    private fun buildTrack(): AudioTrack {
        val minBufferRaw = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val minBuffer = if (minBufferRaw > 0) minBufferRaw else 44100
        val frameSize = 2
        var bufferBytes = maxOf(minBuffer, 44100) // ~1s 容纳一条语音
        if (bufferBytes % frameSize != 0) bufferBytes += frameSize - bufferBytes % frameSize
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(44100)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }
}
