package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _gestureMappings = mutableStateOf<Map<GestureType, GestureAction>>(emptyMap())
    val gestureMappings: State<Map<GestureType, GestureAction>> = _gestureMappings

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val mappings = GestureType.values().associateWith { gesture ->
            settingsRepository.getAction(gesture)
        }
        _gestureMappings.value = mappings
    }

    fun updateGestureAction(gestureType: GestureType, action: GestureAction) {
        settingsRepository.setAction(gestureType, action)
        loadSettings() // refresh state
    }
}
