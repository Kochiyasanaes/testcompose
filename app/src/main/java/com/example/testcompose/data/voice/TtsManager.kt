package com.example.testcompose.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var onCompleteCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            println("DEBUG TTS: 初始化回调 status=$status")
            if (status == TextToSpeech.SUCCESS) {
                // 先尝试中文
                var result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {

                    println("TTS: 中文不支持，尝试默认语言")
                    // 降级为默认语言（通常是英语）
                    result = tts?.setLanguage(Locale.getDefault())
                }

                // 只要有一种语言支持，就标记为初始化成功
                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    isInitialized = true
                    println("TTS: 初始化成功，语言=$result")
                    pendingText?.let { speak(it) }
                } else {
                    println("TTS: 设备完全不支持 TTS")
                }
            }
        }
        // 监听朗读完成
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onCompleteCallback?.invoke()
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        println("DEBUG TTS: speak被调用，文本长度=${text.length}，内容=${text.take(20)}...")
        println("DEBUG TTS: isInitialized=$isInitialized")

        this.onCompleteCallback = onComplete

        if (!isInitialized) {
            println("DEBUG TTS: 还未初始化，暂存文本")
            pendingText = text
            return
        }

        if (text.isBlank()) {
            println("DEBUG TTS: 文本为空，不播放")
            return
        }

        stop()
        println("DEBUG TTS: 开始播放")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kimi_reply")


    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}