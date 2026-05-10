package com.ankilistener.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioAnswerRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun isRecording(): Boolean = recorder != null

    fun start(): File {
        if (recorder != null) {
            throw IllegalStateException("Recorder is already running")
        }

        val dir = File(appContext.filesDir, "ai_answer_audio")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val file = File(dir, "answer_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: IOException) {
            mediaRecorder.release()
            throw e
        } catch (e: RuntimeException) {
            mediaRecorder.release()
            throw e
        }

        recorder = mediaRecorder
        currentFile = file
        return file
    }

    fun stop(): File? {
        val activeRecorder = recorder ?: return currentFile
        val file = currentFile
        try {
            activeRecorder.stop()
        } catch (_: RuntimeException) {
            file?.delete()
        } finally {
            activeRecorder.reset()
            activeRecorder.release()
            recorder = null
            currentFile = null
        }
        return file
    }

    fun cancel() {
        val file = currentFile
        try {
            recorder?.reset()
        } catch (_: Exception) {
        } finally {
            recorder?.release()
            recorder = null
            currentFile = null
            file?.delete()
        }
    }
}
