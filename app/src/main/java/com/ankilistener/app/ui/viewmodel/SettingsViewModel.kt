package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.TtsProvider

data class TtsSettings(
    val provider: TtsProvider = TtsProvider.SYSTEM,
    val baseUrl: String = "http://172.22.64.1:3000",
    val speed: String = "1.0",
    val delay: String = "5",
    val voice: String = "zh_female_wenroutaozi_uranus_bigtts"
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _gestureMappings = mutableStateOf<Map<GestureAction, GestureType>>(emptyMap())
    val gestureMappings: State<Map<GestureAction, GestureType>> = _gestureMappings

    private val _ttsSettings = mutableStateOf(TtsSettings())
    val ttsSettings: State<TtsSettings> = _ttsSettings

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _gestureMappings.value = settingsRepository.getAllMappings()
        _ttsSettings.value = TtsSettings(
            provider = settingsRepository.getTtsProvider(),
            baseUrl = settingsRepository.getTtsBaseUrl(),
            speed = settingsRepository.getTtsSpeed(),
            delay = settingsRepository.getTtsDelay(),
            voice = settingsRepository.getTtsVoice()
        )
    }

    fun updateActionGesture(action: GestureAction, gesture: GestureType) {
        settingsRepository.setGesture(action, gesture)
        loadSettings() // refresh state
    }

    // ---- TTS Settings ----

    private fun applyTtsConfig() {
        val s = _ttsSettings.value
        ttsManager.provider = s.provider
        ttsManager.updateApiConfig(
            baseUrl = s.baseUrl,
            speed = s.speed,
            delay = s.delay,
            voice = s.voice
        )
    }

    fun updateTtsProvider(provider: TtsProvider) {
        settingsRepository.setTtsProvider(provider)
        _ttsSettings.value = _ttsSettings.value.copy(provider = provider)
        applyTtsConfig()
    }

    fun updateTtsBaseUrl(url: String) {
        settingsRepository.setTtsBaseUrl(url)
        _ttsSettings.value = _ttsSettings.value.copy(baseUrl = url)
        applyTtsConfig()
    }

    fun updateTtsSpeed(speed: String) {
        settingsRepository.setTtsSpeed(speed)
        _ttsSettings.value = _ttsSettings.value.copy(speed = speed)
        applyTtsConfig()
    }

    fun updateTtsDelay(delay: String) {
        settingsRepository.setTtsDelay(delay)
        _ttsSettings.value = _ttsSettings.value.copy(delay = delay)
        applyTtsConfig()
    }

    fun updateTtsVoice(voice: String) {
        settingsRepository.setTtsVoice(voice)
        _ttsSettings.value = _ttsSettings.value.copy(voice = voice)
        applyTtsConfig()
    }
}
