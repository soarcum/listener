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

    val apiTtsManager = ApiTtsManager(context)

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

    /**
     * Prefetch audio for a text. Only effective when using API provider.
     */
    fun prefetch(text: String) {
        if (provider == TtsProvider.API) {
            apiTtsManager.prefetch(text)
        }
    }

    /**
     * Check if audio for the given text is cached. Only meaningful for API provider.
     */
    fun isCached(text: String): Boolean {
        return if (provider == TtsProvider.API) {
            apiTtsManager.isCached(text)
        } else {
            true // System TTS is always "ready"
        }
    }

    /**
     * Get cache stats.
     */
    fun getCacheStats(): TtsAudioCache.CacheStats {
        return apiTtsManager.cache.getCacheStats()
    }

    /**
     * Clear all cached audio.
     */
    fun clearCache() {
        apiTtsManager.cache.clearCache()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        apiTtsManager.release()
    }
}
