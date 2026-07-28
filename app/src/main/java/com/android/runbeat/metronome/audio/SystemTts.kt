package com.android.runbeat.metronome.audio

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 系统文字转语音封装（严格按官方 TextToSpeech API）。
 *
 * - [onInit] 正确跟踪初始化状态（SUCCESS/ERROR）
 * - [isLanguageAvailable] 检测中文语音，缺失则回退默认语言
 * - [setOnUtteranceProgressListener] 跟踪每句实际播报的开始/完成/失败
 * - 每次 [speak] 使用自增唯一 utteranceId（避免同 ID 被上次完成的回调误停）
 * - 提供 [checkTtsData] / [installTtsData] 数据检查与安装引导 Intent
 */
class SystemTts(
    private val context: Context,
    private val onReady: () -> Unit = {},
) {

    @Volatile
    var available: Boolean = false
        private set

    @Volatile
    var chineseAvailable: Boolean = false
        private set

    private val utteranceCounter = AtomicInteger(0)

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langStatus = runCatching {
                    tts?.isLanguageAvailable(Locale.CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                chineseAvailable = langStatus == TextToSpeech.LANG_AVAILABLE ||
                    langStatus == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    langStatus == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                Log.d(TAG, "init: status=SUCCESS 中文可用性 langStatus=$langStatus chineseAvailable=$chineseAvailable")

                if (chineseAvailable) {
                    tts?.language = Locale.CHINESE
                } else {
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(1.1f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "utterance start: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "utterance done: $utteranceId")
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "utterance error: $utteranceId")
                        onError(utteranceId, TextToSpeech.ERROR)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "utterance error: $utteranceId code=$errorCode")
                    }
                })
                available = true
            } else {
                Log.e(TAG, "init: 失败 status=$status")
            }
            onReady()
        }
    }

    /**
     * 播报一段文本。
     * @return true=成功入队；false=TTS 不可用或入队失败
     */
    fun speak(text: String): Boolean {
        val t = tts ?: return false
        if (!available) return false
        val id = "utt-" + utteranceCounter.incrementAndGet()
        val result = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        Log.d(TAG, "speak: text=\"$text\" result=$result id=$id")
        return result == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        available = false
    }

    companion object {
        private const val TAG = "RunBeatSpeech"

        /** 检查系统 TTS 数据是否已安装（返回 Intent，配合 onActivityResult 使用） */
        fun checkTtsData(context: Context): Intent =
            Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)

        /** 引导安装 TTS 语音数据 */
        fun installTtsData(context: Context): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}
