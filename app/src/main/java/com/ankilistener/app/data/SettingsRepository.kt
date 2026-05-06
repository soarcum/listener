package com.ankilistener.app.data

import android.content.Context
import android.content.SharedPreferences

enum class GestureAction {
    NONE,
    SHOW_ANSWER,
    PLAY_TTS,
    ANSWER_AGAIN,
    ANSWER_HARD,
    ANSWER_GOOD,
    ANSWER_EASY,
    SKIP,
    MARK
}

enum class GestureType {
    SINGLE_TAP,
    DOUBLE_TAP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)

    fun getAction(gestureType: GestureType): GestureAction {
        val defaultAction = when (gestureType) {
            GestureType.SINGLE_TAP -> GestureAction.SHOW_ANSWER
            GestureType.DOUBLE_TAP -> GestureAction.PLAY_TTS
            GestureType.SWIPE_LEFT -> GestureAction.ANSWER_AGAIN
            GestureType.SWIPE_RIGHT -> GestureAction.ANSWER_GOOD
            GestureType.SWIPE_UP -> GestureAction.ANSWER_EASY
            GestureType.SWIPE_DOWN -> GestureAction.ANSWER_HARD
        }
        val savedName = prefs.getString(gestureType.name, defaultAction.name) ?: defaultAction.name
        return try {
            GestureAction.valueOf(savedName)
        } catch (e: Exception) {
            defaultAction
        }
    }

    fun setAction(gestureType: GestureType, action: GestureAction) {
        prefs.edit().putString(gestureType.name, action.name).apply()
    }
}
