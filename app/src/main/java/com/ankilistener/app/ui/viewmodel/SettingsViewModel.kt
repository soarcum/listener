package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.data.ThemeMode
import com.ankilistener.app.data.TtsScheme
import com.ankilistener.app.util.TtsAudioCache
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.TtsProvider

data class TtsSettings(
    val provider: TtsProvider = TtsProvider.SYSTEM,
    val scheme: TtsScheme = TtsScheme.SYSTEM,
    val apiAddress: String = "",
    val apiKey: String = "",
    val baseUrl: String = SettingsRepository.DEFAULT_BASE_URL,
    val speed: String = "1.0",
    val delay: String = "5",
    val voice: String = "zh_female_wenroutaozi_uranus_bigtts",
    val prefetchCount: Int = 3
)

data class AiAnswerSettings(
    val enabled: Boolean = false,
    val endpoint: String = SettingsRepository.DEFAULT_AI_ENDPOINT,
    val apiKey: String = "",
    val model: String = "default",
    val followUpEnabled: Boolean = true
)

data class ConceptSettings(
    val enabled: Boolean = true,
    val dueOnly: Boolean = true,
    val againDelayMinutes: Int = 10
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _gestureMappings = mutableStateOf<Map<GestureAction, GestureType>>(emptyMap())
    val gestureMappings: State<Map<GestureAction, GestureType>> = _gestureMappings

    private val _ttsSettings = mutableStateOf(TtsSettings())
    val ttsSettings: State<TtsSettings> = _ttsSettings

    private val _cacheStats = mutableStateOf(TtsAudioCache.CacheStats(0, 0L))
    val cacheStats: State<TtsAudioCache.CacheStats> = _cacheStats

    private val _aiSettings = mutableStateOf(AiAnswerSettings())
    val aiSettings: State<AiAnswerSettings> = _aiSettings

    private val _conceptSettings = mutableStateOf(ConceptSettings())
    val conceptSettings: State<ConceptSettings> = _conceptSettings

    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _gestureMappings.value = settingsRepository.getAllMappings()
        _ttsSettings.value = TtsSettings(
            provider = settingsRepository.getTtsProvider(),
            scheme = settingsRepository.getTtsScheme(),
            apiAddress = settingsRepository.getTtsApiAddress(),
            apiKey = settingsRepository.getTtsApiKey(),
            baseUrl = settingsRepository.getTtsBaseUrl(),
            speed = settingsRepository.getTtsSpeed(),
            delay = settingsRepository.getTtsDelay(),
            voice = settingsRepository.getTtsVoice(),
            prefetchCount = settingsRepository.getPrefetchCount()
        )
        _aiSettings.value = AiAnswerSettings(
            enabled = settingsRepository.getAiEnabled(),
            endpoint = settingsRepository.getAiEndpoint(),
            apiKey = settingsRepository.getAiApiKey(),
            model = settingsRepository.getAiModel(),
            followUpEnabled = settingsRepository.getAiFollowUpEnabled()
        )
        _conceptSettings.value = ConceptSettings(
            enabled = settingsRepository.getConceptReviewEnabled(),
            dueOnly = settingsRepository.getConceptDueOnly(),
            againDelayMinutes = settingsRepository.getConceptAgainDelayMinutes()
        )
        _themeMode.value = settingsRepository.getThemeMode()
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        _cacheStats.value = ttsManager.getCacheStats()
    }

    fun clearCache() {
        ttsManager.clearCache()
        refreshCacheStats()
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
            voice = s.voice,
            apiKey = s.apiKey
        )
    }

    fun updateTtsProvider(provider: TtsProvider) {
        settingsRepository.setTtsProvider(provider)
        _ttsSettings.value = _ttsSettings.value.copy(provider = provider)
        applyTtsConfig()
    }

    fun updateTtsScheme(scheme: TtsScheme) {
        settingsRepository.setTtsScheme(scheme)
        val hasAddress = _ttsSettings.value.apiAddress.isNotBlank()
        val provider = if (scheme == TtsScheme.API && hasAddress) TtsProvider.API else TtsProvider.SYSTEM
        settingsRepository.setTtsProvider(provider)
        _ttsSettings.value = _ttsSettings.value.copy(scheme = scheme, provider = provider)
        applyTtsConfig()
    }

    fun updateTtsApiAddress(address: String, apiKey: String) {
        settingsRepository.setTtsApiAddress(address)
        settingsRepository.setTtsApiKey(apiKey)
        _ttsSettings.value = _ttsSettings.value.copy(apiAddress = address, apiKey = apiKey)
        if (address.isNotBlank()) {
            updateTtsBaseUrl(address)
            settingsRepository.setTtsProvider(TtsProvider.API)
            _ttsSettings.value = _ttsSettings.value.copy(provider = TtsProvider.API)
            applyTtsConfig()
        }
    }

    fun clearTtsApiConfig() {
        settingsRepository.setTtsApiAddress("")
        settingsRepository.setTtsApiKey("")
        settingsRepository.setTtsProvider(TtsProvider.SYSTEM)
        _ttsSettings.value = _ttsSettings.value.copy(apiAddress = "", apiKey = "", provider = TtsProvider.SYSTEM)
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

    fun updatePrefetchCount(count: Int) {
        settingsRepository.setPrefetchCount(count)
        _ttsSettings.value = _ttsSettings.value.copy(prefetchCount = count)
    }

    fun updateAiEnabled(enabled: Boolean) {
        settingsRepository.setAiEnabled(enabled)
        _aiSettings.value = _aiSettings.value.copy(enabled = enabled)
    }

    fun updateAiEndpoint(endpoint: String) {
        settingsRepository.setAiEndpoint(endpoint)
        _aiSettings.value = _aiSettings.value.copy(endpoint = endpoint)
    }

    fun updateAiApiKey(apiKey: String) {
        settingsRepository.setAiApiKey(apiKey)
        _aiSettings.value = _aiSettings.value.copy(apiKey = apiKey)
    }

    fun updateAiModel(model: String) {
        settingsRepository.setAiModel(model)
        _aiSettings.value = _aiSettings.value.copy(model = model)
    }

    fun updateAiFollowUpEnabled(enabled: Boolean) {
        settingsRepository.setAiFollowUpEnabled(enabled)
        _aiSettings.value = _aiSettings.value.copy(followUpEnabled = enabled)
    }

    // ---- Concept Settings ----

    fun updateConceptEnabled(enabled: Boolean) {
        settingsRepository.setConceptReviewEnabled(enabled)
        _conceptSettings.value = _conceptSettings.value.copy(enabled = enabled)
    }

    fun updateConceptDueOnly(dueOnly: Boolean) {
        settingsRepository.setConceptDueOnly(dueOnly)
        _conceptSettings.value = _conceptSettings.value.copy(dueOnly = dueOnly)
    }

    fun updateConceptAgainDelayMinutes(minutes: Int) {
        settingsRepository.setConceptAgainDelayMinutes(minutes)
        _conceptSettings.value = _conceptSettings.value.copy(againDelayMinutes = minutes)
    }

    fun updateThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _themeMode.value = mode
    }
}
