package com.ankilistener.app.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Disk-based audio cache for TTS API responses.
 * Cache key is derived from text + voice + speed so that changing
 * any of those parameters causes a re-download.
 */
class TtsAudioCache(context: Context) {

    companion object {
        private const val TAG = "TtsAudioCache"
        private const val CACHE_DIR_NAME = "tts_audio_cache"
    }

    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }

    // ---- Key generation ----

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun getCacheKey(text: String, voice: String, speed: String, stylePrompt: String = ""): String {
        return md5("$text|$voice|$speed|$stylePrompt")
    }

    private fun getCacheFile(key: String): File {
        return File(cacheDir, "$key.mp3")
    }

    fun deleteCache(text: String, voice: String, speed: String, stylePrompt: String = ""): Boolean {
        val key = getCacheKey(text, voice, speed, stylePrompt)
        val file = getCacheFile(key)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "Deleted TTS cache: key=$key, text=$text, success=$deleted")
            deleted
        } else {
            Log.d(TAG, "TTS cache not found for key=$key, text=$text")
            false
        }
    }

    // ---- Cache queries ----

    fun isCached(text: String, voice: String, speed: String, stylePrompt: String = ""): Boolean {
        val key = getCacheKey(text, voice, speed, stylePrompt)
        return getCacheFile(key).exists()
    }

    fun getCachedFilePath(text: String, voice: String, speed: String, stylePrompt: String = ""): String? {
        val key = getCacheKey(text, voice, speed, stylePrompt)
        val file = getCacheFile(key)
        return if (file.exists()) file.absolutePath else null
    }

    // ---- Download & cache ----

    /**
     * Downloads TTS audio and stores it in cache. Returns the cache file path,
     * or null if download failed. Safe to call from IO dispatcher.
     */
    fun downloadAndCache(
        text: String,
        baseUrl: String,
        voice: String,
        speed: String,
        delay: String,
        apiKey: String = "",
        stylePrompt: String = ""
    ): String? {
        val key = getCacheKey(text, voice, speed, stylePrompt)
        val cacheFile = getCacheFile(key)

        // Already cached
        if (cacheFile.exists()) {
            Log.d(TAG, "Cache hit for key=$key")
            return cacheFile.absolutePath
        }

        val tempFile = File(cacheDir, "$key.tmp")
        try {
            if (baseUrl.contains("api.xiaomimimo.com")) {
                val success = downloadXiaomiTts(tempFile, baseUrl, text, voice, apiKey, stylePrompt)
                if (!success) {
                    tempFile.delete()
                    return null
                }
            } else {
                val requestUrl = buildRequestUrl(baseUrl, text, speed, voice, delay)

                Log.d(TAG, "Downloading TTS audio: key=$key, url=$requestUrl")

                val connection = URL(requestUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error: $responseCode")
                    connection.disconnect()
                    return null
                }

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                connection.disconnect()
            }

            // Atomic rename to prevent partial reads
            if (tempFile.renameTo(cacheFile)) {
                Log.d(TAG, "Cached audio: key=$key, size=${cacheFile.length()} bytes")
                return cacheFile.absolutePath
            } else {
                Log.e(TAG, "Failed to rename temp file")
                tempFile.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for key=$key", e)
            tempFile.delete()
            return null
        }
    }

    private fun downloadXiaomiTts(
        tempFile: File,
        baseUrl: String,
        text: String,
        voice: String,
        apiKey: String,
        stylePrompt: String
    ): Boolean {
        try {
            val rootJson = JSONObject()
            rootJson.put("model", "mimo-v2.5-tts")

            val messagesArray = JSONArray()

            // Message 1: User style prompt
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", stylePrompt.ifBlank { "Natural and clear English speech, standard pace and friendly tone." })
            messagesArray.put(userMsg)

            // Message 2: Assistant content (the text to synthesize)
            val assistantMsg = JSONObject()
            assistantMsg.put("role", "assistant")
            assistantMsg.put("content", text)
            messagesArray.put(assistantMsg)

            rootJson.put("messages", messagesArray)

            // Audio configuration
            val audioObj = JSONObject()
            audioObj.put("format", "wav")
            audioObj.put("voice", voice.ifBlank { "mimo_default" })
            rootJson.put("audio", audioObj)

            val jsonPayload = rootJson.toString()
            Log.d(TAG, "Xiaomi TTS request payload: $jsonPayload")

            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("api-key", apiKey)

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Xiaomi TTS HTTP error: $responseCode, response: $errorResponse")
                connection.disconnect()
                return false
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val respJson = JSONObject(responseText)
            val choices = respJson.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val audio = message.getJSONObject("audio")
                val base64Data = audio.getString("data")

                // Decode base64 to tempFile
                val audioBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                tempFile.outputStream().use { output ->
                    output.write(audioBytes)
                }
                return true
            } else {
                Log.e(TAG, "Xiaomi TTS response choices is empty")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse or download Xiaomi TTS response", e)
            return false
        }
    }

    private fun buildRequestUrl(
        address: String,
        text: String,
        speed: String,
        voice: String,
        delay: String
    ): String {
        return if (address.contains("{{") && address.contains("}}")) {
            var url = address
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val encodedVoice = URLEncoder.encode(voice, "UTF-8")

            // Replace text placeholders with URL-encoded text
            url = url.replace("{{java.encodeURI(speakText)}}", encodedText)
            url = url.replace("{{java.encodeURI(speakText, 'utf-8')}}", encodedText)
            url = url.replace("{{java.encodeURI(speakText, \"utf-8\")}}", encodedText)
            url = url.replace("{{speakText}}", encodedText)

            // Replace speed placeholders
            url = url.replace("{{speakSpeed}}", speed)

            // Replace voice placeholders
            url = url.replace("{{java.encodeURI(voice)}}", encodedVoice)
            url = url.replace("{{voice}}", voice)

            // Replace delay placeholders
            url = url.replace("{{delay}}", delay)

            url
        } else {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val base = address.removeSuffix("/")
            "$base/api/reader/tts/stream" +
                    "?text=$encodedText" +
                    "&speed=$speed" +
                    "&voice=$voice" +
                    "&usePrefetch=false" +
                    "&delay=$delay"
        }
    }

    // ---- Cache management ----

    data class CacheStats(
        val fileCount: Int,
        val totalSizeBytes: Long
    ) {
        val totalSizeMB: String
            get() = "%.1f".format(totalSizeBytes / (1024.0 * 1024.0))
    }

    fun getCacheStats(): CacheStats {
        val files = cacheDir.listFiles { f -> f.extension == "mp3" } ?: emptyArray()
        return CacheStats(
            fileCount = files.size,
            totalSizeBytes = files.sumOf { it.length() }
        )
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache cleared")
    }
}
