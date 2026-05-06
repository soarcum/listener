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

    private val _gestureMappings = mutableStateOf<Map<GestureAction, GestureType>>(emptyMap())
    val gestureMappings: State<Map<GestureAction, GestureType>> = _gestureMappings

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _gestureMappings.value = settingsRepository.getAllMappings()
    }

    fun updateActionGesture(action: GestureAction, gesture: GestureType) {
        settingsRepository.setGesture(action, gesture)
        loadSettings() // refresh state
    }
}
