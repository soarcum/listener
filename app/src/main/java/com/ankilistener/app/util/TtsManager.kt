package com.ankilistener.app.util

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class TtsProvider {
    SYSTEM,  // Android built-in TextToSpeech
    API      // Remote HTTP TTS API (Legado-compatible)
}

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = try {
        TextToSpeech(context, this)
    } catch (e: Exception) {
        AppLogger.e("TtsManager", "Failed to initialize TextToSpeech", e)
        null
    }
    private var isReady = false
    private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val apiTtsManager = ApiTtsManager(context)

    var provider: TtsProvider = TtsProvider.SYSTEM

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.let { callback ->
                            mainHandler.post(callback)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)
                    }
                }
            })
            isReady = true
        }
    }

    /**
     * Update API TTS configuration.
     */
    fun updateApiConfig(baseUrl: String, speed: String, delay: String, voice: String, apiKey: String = "") {
        apiTtsManager.baseUrl = baseUrl
        apiTtsManager.speakSpeed = speed
        apiTtsManager.delay = delay
        apiTtsManager.voice = voice
        apiTtsManager.apiKey = apiKey
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        when (provider) {
            TtsProvider.SYSTEM -> {
                if (isReady) {
                    utteranceCallbacks.clear()
                    tts?.stop()
                    val utteranceId = "utt_${System.currentTimeMillis()}"
                    if (onComplete != null) {
                        utteranceCallbacks[utteranceId] = onComplete
                    }
                    tts?.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        Bundle(),
                        utteranceId
                    )
                } else {
                    onComplete?.invoke()
                }
            }
            TtsProvider.API -> {
                apiTtsManager.speak(text, onComplete)
            }
        }
    }

    fun stop() {
        when (provider) {
            TtsProvider.SYSTEM -> {
                utteranceCallbacks.clear()
                tts?.stop()
            }
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
     * Delete cached audio for the given text. Only meaningful for API provider.
     */
    fun deleteCache(text: String): Boolean {
        return if (provider == TtsProvider.API) {
            apiTtsManager.deleteCache(text)
        } else {
            false
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
        utteranceCallbacks.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        apiTtsManager.release()
    }
}
