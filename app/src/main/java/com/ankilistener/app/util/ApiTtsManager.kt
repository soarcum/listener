package com.ankilistener.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * TTS manager that streams audio from a remote HTTP TTS API.
 * Compatible with Legado-style TTS endpoints.
 *
 * Features:
 * - Disk-based audio caching (same text won't re-download)
 * - Prefetch support for upcoming cards
 * - Cache management (stats, clear)
 */
class ApiTtsManager(private val context: Context) {

    companion object {
        private const val TAG = "ApiTtsManager"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val cache = TtsAudioCache(context)

    var baseUrl: String = "http://172.22.64.1:3000"
    var speakSpeed: String = "1.0"
    var delay: String = "5"
    var voice: String = "zh_female_wenroutaozi_uranus_bigtts"
    var apiKey: String = ""
    var stylePrompt: String = ""

    /**
     * Speaks the given text. Uses cached audio if available, otherwise
     * downloads, caches, then plays.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        stop()
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        playJob = scope.launch {
            try {
                // Try cache first, download if miss
                val filePath = cache.getCachedFilePath(text, voice, speakSpeed, stylePrompt)
                    ?: cache.downloadAndCache(text, baseUrl, voice, speakSpeed, delay, apiKey, stylePrompt)

                if (filePath == null) {
                    throw Exception("获取音频文件失败")
                }

                // Play from local file on Main thread
                withContext(Dispatchers.Main) {
                    try {
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(filePath)
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                true
                            }
                            setOnCompletionListener {
                                it.release()
                                if (mediaPlayer == it) {
                                    mediaPlayer = null
                                }
                                onComplete?.invoke()
                            }
                            prepare() // Synchronous since it's a local file
                            start()
                        }
                        mediaPlayer = player
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to play cached audio", e)
                        Toast.makeText(context, "播放音频失败: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete?.invoke()
                    }
                }
            } catch (e: CancellationException) {
                // Coroutine cancelled, expected behavior
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "TTS播放失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                onComplete?.invoke()
            }
        }
    }

    /**
     * Prefetch audio for the given text without playing it.
     * Downloads and caches the audio in background.
     */
    fun prefetch(text: String) {
        if (text.isBlank()) return
        if (cache.isCached(text, voice, speakSpeed, stylePrompt)) return

        scope.launch {
            try {
                cache.downloadAndCache(text, baseUrl, voice, speakSpeed, delay, apiKey, stylePrompt)
            } catch (e: CancellationException) {
                // expected
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch error", e)
            }
        }
    }

    /**
     * Check if audio for the given text is already cached.
     */
    fun isCached(text: String): Boolean {
        return cache.isCached(text, voice, speakSpeed, stylePrompt)
    }

    /**
     * Delete cached audio for the given text.
     */
    fun deleteCache(text: String): Boolean {
        return cache.deleteCache(text, voice, speakSpeed, stylePrompt)
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
