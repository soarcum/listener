package com.ankilistener.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val body: String
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Installing(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object UpdateManager {

    private const val REPO = "soarcum/listener"
    private const val GITHUB_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val MAX_RETRY = 3
    private const val TAG = "UpdateManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 私有仓库 asset 下载需要手动处理 302,避免把 Authorization 头转发到 S3 预签名 URL 触发 400/403
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private const val GITHUB_TOKEN = ""

    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(GITHUB_API)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "AnkiListener")

            if (GITHUB_TOKEN.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $GITHUB_TOKEN")
            }
            val request = requestBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Update check HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v")
                val releaseBody = json.optString("body", "")

                val currentVersion = getCurrentVersion(context)
                AppLogger.i(TAG, "Current: $currentVersion, Latest: $tagName")

                if (isNewerVersion(tagName, currentVersion)) {
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            val downloadUrl = asset.optString("browser_download_url", "")
                                .ifBlank { asset.getString("url") }
                            return@withContext UpdateInfo(
                                version = tagName,
                                downloadUrl = downloadUrl,
                                body = releaseBody
                            )
                        }
                    }
                    AppLogger.w(TAG, "No APK found in release assets")
                }
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(l.size, c.size)
        for (i in 0 until maxLen) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        version: String,
        onStateChange: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val fileName = "AnkiListener-v$version.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // Already downloaded
        if (file.exists() && file.length() > 1024) {
            AppLogger.i(TAG, "APK already cached: ${file.name}")
            onStateChange(DownloadState.Installing(file))
            installApk(context, file)
            return@withContext
        }

        var lastError: String? = null
        for (attempt in 1..MAX_RETRY) {
            AppLogger.i(TAG, "Download attempt $attempt/$MAX_RETRY")
            onStateChange(DownloadState.Downloading(0, 0, 0))
            try {
                // If it is a public github.com url, we proxy through mirror.ghproxy.com for the first two attempts
                val targetUrl = if (attempt < MAX_RETRY && downloadUrl.contains("github.com/")) {
                    "https://mirror.ghproxy.com/$downloadUrl"
                } else {
                    downloadUrl
                }
                AppLogger.i(TAG, "Downloading from URL: $targetUrl")
                downloadApk(targetUrl, file, onStateChange)
                AppLogger.i(TAG, "Download complete: ${file.length()} bytes")
                onStateChange(DownloadState.Installing(file))
                installApk(context, file)
                return@withContext
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                AppLogger.e(TAG, "Download attempt $attempt failed: $lastError")
                if (file.exists()) file.delete()
                if (attempt < MAX_RETRY) {
                    onStateChange(DownloadState.Downloading(0, 0, 0)) // Reset before retry
                }
            }
        }

        val errorMsg = "Download failed after $MAX_RETRY attempts: $lastError"
        AppLogger.e(TAG, errorMsg)
        onStateChange(DownloadState.Error(errorMsg))
    }

    private fun downloadApk(
        downloadUrl: String,
        targetFile: File,
        onStateChange: (DownloadState) -> Unit
    ) {
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", "AnkiListener")

        // Only add authorization token when requesting the official private API
        if (downloadUrl.contains("api.github.com") && GITHUB_TOKEN.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $GITHUB_TOKEN")
            requestBuilder.addHeader("Accept", "application/octet-stream")
        }

        val request = requestBuilder.build()

        downloadClient.newCall(request).execute().use { firstResp ->
            val streamResp = when (firstResp.code) {
                301, 302, 303, 307, 308 -> {
                    val location = firstResp.header("Location")
                        ?: throw RuntimeException("Redirect ${firstResp.code} without Location header")
                    val cdnRequest = Request.Builder()
                        .url(location)
                        .addHeader("User-Agent", "AnkiListener")
                        .build()
                    downloadClient.newCall(cdnRequest).execute()
                }
                200 -> firstResp
                else -> throw RuntimeException("HTTP ${firstResp.code}")
            }

            val ownsResp = streamResp !== firstResp
            try {
                if (!streamResp.isSuccessful) {
                    throw RuntimeException("HTTP ${streamResp.code} from CDN")
                }
                writeBodyToFile(streamResp, targetFile, onStateChange)
            } finally {
                if (ownsResp) streamResp.close()
            }
        }
    }

    private fun writeBodyToFile(
        response: okhttp3.Response,
        targetFile: File,
        onStateChange: (DownloadState) -> Unit
    ) {
        val body = response.body ?: throw RuntimeException("Empty response body")
        val totalBytes = body.contentLength()
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var lastProgressReport = 0

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes * 100 / totalBytes).toInt()
                        } else {
                            -1
                        }

                        if (progress != lastProgressReport) {
                            lastProgressReport = progress
                            onStateChange(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                        }
                    }

                    output.flush()
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun installApk(context: Context, file: File) {
        AppLogger.i(TAG, "Launching installer for ${file.name}")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }
}
