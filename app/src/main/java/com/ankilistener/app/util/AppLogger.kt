package com.ankilistener.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app logger that captures log entries for on-device viewing.
 * Also forwards all entries to Android logcat.
 * Persists ERROR and WARN entries to file for crash debugging.
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

    private const val MAX_ENTRIES = 1000
    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var logFile: File? = null
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "app.log")
        // Load persisted entries on startup
        loadPersistedEntries()
    }

    private const val MAX_FILE_SIZE = 1024 * 1024L // 1MB

    private fun loadPersistedEntries() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            // Truncate if file exceeds limit, keep last 80% of max size
            if (file.length() > MAX_FILE_SIZE) {
                val lines = file.readLines()
                val keepFrom = (lines.size * 0.2).toInt()
                val truncated = lines.drop(keepFrom)
                file.writeText(truncated.joinToString("\n") + "\n")
                Log.w("AppLogger", "Log file truncated: dropped ${keepFrom} old entries")
            }
            val lines = file.readLines()
            for (line in lines) {
                val entry = parseLogLine(line) ?: continue
                if (isLegacyCrashNoise(entry)) continue
                entries.add(entry)
            }
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        } catch (e: Throwable) {
            Log.e("AppLogger", "Failed to load persisted logs", e)
        }
    }

    private fun isLegacyCrashNoise(entry: LogEntry): Boolean {
        if (entry.level != Level.ERROR || entry.tag != "CRASH") return false
        return entry.message.endsWith(": null") || entry.message == "Uncaught exception in main"
    }

    private fun parseLogLine(line: String): LogEntry? {
        // Format: "2024-01-01 12:00:00.000 [E] Tag: message"
        val regex = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[([DEWI])\] (.+?): (.+)$""")
        val match = regex.matchEntire(line) ?: return null
        val (timeStr, levelChar, tag, message) = match.destructured
        val timestamp = try {
            fileDateFormat.parse(timeStr)?.time ?: return null
        } catch (e: Exception) {
            return null
        }
        val level = when (levelChar) {
            "D" -> Level.DEBUG
            "I" -> Level.INFO
            "W" -> Level.WARN
            "E" -> Level.ERROR
            else -> return null
        }
        return LogEntry(timestamp, level, tag, message)
    }

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
        // Persist all entries to file for debugging
        persistEntry(entry)
        notifyListeners()
    }

    private fun persistEntry(entry: LogEntry) {
        val file = logFile ?: return
        try {
            // Skip writing if file is already too large
            if (file.length() > MAX_FILE_SIZE * 2) return
            val time = fileDateFormat.format(Date(entry.timestamp))
            val lvl = entry.level.name.first()
            file.appendText("$time [$lvl] ${entry.tag}: ${entry.message}\n")
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to persist log entry", e)
        }
    }

    fun getEntries(): List<LogEntry> = entries.toList()

    fun clear() {
        entries.clear()
        // Clear persisted file
        logFile?.let { file ->
            try {
                if (file.exists()) {
                    file.writeText("")
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to clear log file", e)
            }
        }
        notifyListeners()
    }

    fun clearCrashLog(context: Context) {
        try {
            val crashFile = File(context.filesDir, "crash.log")
            if (crashFile.exists()) {
                crashFile.delete()
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to clear crash log", e)
        }
    }
}
