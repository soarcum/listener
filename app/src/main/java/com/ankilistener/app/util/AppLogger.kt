package com.ankilistener.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app logger that captures log entries for on-device viewing.
 * Also forwards all entries to Android logcat.
 */
object AppLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String
    ) {
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        fun formatted(): String {
            val time = dateFormat.format(Date(timestamp))
            val lvl = level.name.first()
            return "$time [$lvl] $tag: $message"
        }
    }

    private const val MAX_ENTRIES = 500
    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addEntry(Level.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(Level.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addEntry(Level.WARN, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            addEntry(Level.ERROR, tag, "$message: ${throwable.message}")
        } else {
            Log.e(tag, message)
            addEntry(Level.ERROR, tag, message)
        }
    }

    private fun addEntry(level: Level, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        entries.add(entry)
        // Trim if exceeds max
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        notifyListeners()
    }

    fun getEntries(): List<LogEntry> = entries.toList()

    fun clear() {
        entries.clear()
        notifyListeners()
    }
}
