package com.ankilistener.app.data

import android.content.Context
import android.content.SharedPreferences

enum class GestureAction {
    NONE,
    SKIP,
    MARK,
    SHOW_ANSWER,
    PLAY_TTS,
    ANSWER_AGAIN,
    ANSWER_HARD,
    ANSWER_GOOD,
    ANSWER_EASY,
    UNDO
}

enum class GestureType {
    NONE,
    SINGLE_TAP,
    DOUBLE_TAP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    LONG_PRESS,
    TWO_FINGER_TAP
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gestures_prefs_v2", Context.MODE_PRIVATE)

    fun getGesture(action: GestureAction): GestureType {
        val defaultGesture = when (action) {
            GestureAction.SHOW_ANSWER -> GestureType.SINGLE_TAP
            GestureAction.PLAY_TTS -> GestureType.DOUBLE_TAP
            GestureAction.ANSWER_AGAIN -> GestureType.SWIPE_LEFT
            GestureAction.ANSWER_HARD -> GestureType.SWIPE_DOWN
            GestureAction.ANSWER_GOOD -> GestureType.SWIPE_RIGHT
            GestureAction.ANSWER_EASY -> GestureType.SWIPE_UP
            else -> GestureType.NONE
        }
        val savedName = prefs.getString(action.name, defaultGesture.name) ?: defaultGesture.name
        return try {
            GestureType.valueOf(savedName)
        } catch (e: Exception) {
            defaultGesture
        }
    }

    fun setGesture(action: GestureAction, gesture: GestureType) {
        prefs.edit().putString(action.name, gesture.name).apply()
    }
    
    // For backward compatibility or internal lookup
    fun getAllMappings(): Map<GestureAction, GestureType> {
        return GestureAction.values().filter { it != GestureAction.NONE }.associateWith { 
            getGesture(it)
        }
    }
}
