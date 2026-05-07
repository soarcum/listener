package com.ankilistener.app.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import java.net.URLEncoder

/**
 * TTS manager that streams audio from a remote HTTP TTS API.
 * Compatible with Legado-style TTS endpoints.
 */
class ApiTtsManager {

    companion object {
        private const val TAG = "ApiTtsManager"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var baseUrl: String = "http://172.22.64.1:3000"
    var speakSpeed: String = "1.0"
    var delay: String = "5"
    var voice: String = "zh_female_wenroutaozi_uranus_bigtts"

    /**
     * Speaks the given text by streaming audio from the remote TTS API.
     */
    fun speak(text: String) {
        stop()

        if (text.isBlank()) return

        playJob = scope.launch {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "${baseUrl}/api/reader/tts/stream" +
                        "?text=$encodedText" +
                        "&speed=$speakSpeed" +
                        "&voice=$voice" +
                        "&usePrefetch=false" +
                        "&delay=$delay"

                Log.d(TAG, "TTS API request: $url")

                withContext(Dispatchers.Main) {
                    try {
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(url)
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                true
                            }
                            setOnCompletionListener {
                                it.release()
                                if (mediaPlayer == it) {
                                    mediaPlayer = null
                                }
                            }
                            prepareAsync()
                            setOnPreparedListener { mp ->
                                mp.start()
                            }
                        }
                        mediaPlayer = player
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start MediaPlayer", e)
                    }
                }
            } catch (e: CancellationException) {
                // Coroutine cancelled, expected behavior
            } catch (e: Exception) {
                Log.e(TAG, "TTS API error", e)
            }
        }
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
