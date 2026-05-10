package com.ankilistener.app.data

import android.content.Context
import android.content.SharedPreferences
import com.ankilistener.app.util.AppLogger
import org.json.JSONObject

data class ConceptReviewState(
    val key: String,
    val dueAt: Long,
    val intervalDays: Double,
    val easeFactor: Double,
    val lastEase: Int,
    val reviewCount: Int,
    val lapseCount: Int,
    val updatedAt: Long
) {
    companion object {
        const val EASE_AGAIN = 1
        const val EASE_HARD = 2
        const val EASE_GOOD = 3
        const val EASE_EASY = 4
        const val MIN_EASE_FACTOR = 1.3
    }
}

class ConceptScheduleRepository(context: Context) {

    companion object {
        private const val TAG = "ConceptSchedule"
        private const val PREFS_NAME = "concept_schedule_v1"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun buildKey(noteId: Long, ord: Int, conceptId: String): String {
        return "$noteId:$ord:$conceptId"
    }

    fun getState(key: String): ConceptReviewState? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            val obj = JSONObject(json)
            ConceptReviewState(
                key = key,
                dueAt = obj.getLong("dueAt"),
                intervalDays = obj.getDouble("intervalDays"),
                easeFactor = obj.getDouble("easeFactor"),
                lastEase = obj.getInt("lastEase"),
                reviewCount = obj.getInt("reviewCount"),
                lapseCount = obj.getInt("lapseCount"),
                updatedAt = obj.getLong("updatedAt")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse state for key=$key", e)
            null
        }
    }

    fun isDue(key: String, now: Long): Boolean {
        val state = getState(key) ?: return true
        return state.dueAt <= now
    }

    fun updateState(key: String, ease: Int, now: Long, againDelayMinutes: Int = 10) {
        val current = getState(key)
        val newState = if (current == null) {
            // First review
            val interval = when (ease) {
                ConceptReviewState.EASE_AGAIN -> 0.0
                ConceptReviewState.EASE_HARD -> 1.0
                ConceptReviewState.EASE_GOOD -> 1.0
                ConceptReviewState.EASE_EASY -> 3.0
                else -> 1.0
            }
            val dueAt = calculateDueAt(ease, interval, now, againDelayMinutes)
            ConceptReviewState(
                key = key,
                dueAt = dueAt,
                intervalDays = interval,
                easeFactor = 2.5,
                lastEase = ease,
                reviewCount = 1,
                lapseCount = if (ease == ConceptReviewState.EASE_AGAIN) 1 else 0,
                updatedAt = now
            )
        } else {
            val newEaseFactor = when (ease) {
                ConceptReviewState.EASE_HARD -> (current.easeFactor - 0.15).coerceAtLeast(ConceptReviewState.MIN_EASE_FACTOR)
                ConceptReviewState.EASE_EASY -> current.easeFactor + 0.15
                else -> current.easeFactor
            }
            val newInterval = when (ease) {
                ConceptReviewState.EASE_AGAIN -> 0.0
                ConceptReviewState.EASE_HARD -> (current.intervalDays * 1.2).coerceAtLeast(1.0)
                ConceptReviewState.EASE_GOOD -> (current.intervalDays * newEaseFactor).coerceAtLeast(1.0)
                ConceptReviewState.EASE_EASY -> (current.intervalDays * newEaseFactor * 1.3).coerceAtLeast(3.0)
                else -> current.intervalDays
            }
            val dueAt = calculateDueAt(ease, newInterval, now, againDelayMinutes)
            current.copy(
                dueAt = dueAt,
                intervalDays = newInterval,
                easeFactor = newEaseFactor,
                lastEase = ease,
                reviewCount = current.reviewCount + 1,
                lapseCount = current.lapseCount + if (ease == ConceptReviewState.EASE_AGAIN) 1 else 0,
                updatedAt = now
            )
        }
        saveState(newState)
        AppLogger.i(TAG, "Updated concept $key: ease=$ease, dueAt=${newState.dueAt}, interval=${newState.intervalDays}")
    }

    private fun calculateDueAt(ease: Int, intervalDays: Double, now: Long, againDelayMinutes: Int): Long {
        return if (ease == ConceptReviewState.EASE_AGAIN) {
            now + againDelayMinutes * 60 * 1000L
        } else {
            now + (intervalDays * 24 * 60 * 60 * 1000).toLong()
        }
    }

    private fun saveState(state: ConceptReviewState) {
        val obj = JSONObject().apply {
            put("dueAt", state.dueAt)
            put("intervalDays", state.intervalDays)
            put("easeFactor", state.easeFactor)
            put("lastEase", state.lastEase)
            put("reviewCount", state.reviewCount)
            put("lapseCount", state.lapseCount)
            put("updatedAt", state.updatedAt)
        }
        prefs.edit().putString(state.key, obj.toString()).apply()
    }
}
