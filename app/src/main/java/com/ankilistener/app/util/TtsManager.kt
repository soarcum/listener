package com.ankilistener.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

enum class TtsProvider {
    SYSTEM,  // Android built-in TextToSpeech
    API      // Remote HTTP TTS API (Legado-compatible)
}

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false

    private val apiTtsManager = ApiTtsManager()

    var provider: TtsProvider = TtsProvider.SYSTEM

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isReady = true
        }
    }

    /**
     * Update API TTS configuration.
     */
    fun updateApiConfig(baseUrl: String, speed: String, delay: String, voice: String) {
        apiTtsManager.baseUrl = baseUrl
        apiTtsManager.speakSpeed = speed
        apiTtsManager.delay = delay
        apiTtsManager.voice = voice
    }

    fun speak(text: String) {
        when (provider) {
            TtsProvider.SYSTEM -> {
                if (isReady) {
                    tts?.stop()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            TtsProvider.API -> {
                apiTtsManager.speak(text)
            }
        }
    }

    fun stop() {
        when (provider) {
            TtsProvider.SYSTEM -> tts?.stop()
            TtsProvider.API -> apiTtsManager.stop()
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        apiTtsManager.release()
    }
}
