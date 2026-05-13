package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.data.ThemeMode
import com.ankilistener.app.data.TtsScheme
import com.ankilistener.app.data.TtsSchemeItem
import com.ankilistener.app.util.TtsAudioCache
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.TtsProvider

data class TtsSettings(
    val provider: TtsProvider = TtsProvider.SYSTEM,
    val scheme: TtsScheme = TtsScheme.SYSTEM,
    val schemes: List<TtsSchemeItem> = emptyList(),
    val activeSchemeId: String = "",
    val prefetchCount: Int = 3
) {
    val activeScheme: TtsSchemeItem?
        get() = schemes.find { it.id == activeSchemeId } ?: schemes.firstOrNull()
}

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
        val schemes = settingsRepository.getTtsSchemes()
        val activeId = settingsRepository.getActiveSchemeId()
        _ttsSettings.value = TtsSettings(
            provider = settingsRepository.getTtsProvider(),
            scheme = settingsRepository.getTtsScheme(),
            schemes = schemes,
            activeSchemeId = activeId.ifBlank { schemes.firstOrNull()?.id ?: "" },
            prefetchCount = settingsRepository.getPrefetchCount()
        )
        // Sync active scheme config to TtsManager on load
        if (schemes.isNotEmpty()) applyTtsConfig()
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
        val active = s.activeScheme
        if (active != null) {
            ttsManager.updateApiConfig(
                baseUrl = active.address,
                speed = active.speed,
                delay = active.delay,
                voice = active.voice,
                apiKey = active.apiKey
            )
        }
    }

    fun updateTtsScheme(scheme: TtsScheme) {
        settingsRepository.setTtsScheme(scheme)
        val hasSchemes = _ttsSettings.value.schemes.isNotEmpty()
        val provider = if (scheme == TtsScheme.API && hasSchemes) TtsProvider.API else TtsProvider.SYSTEM
        settingsRepository.setTtsProvider(provider)
        _ttsSettings.value = _ttsSettings.value.copy(scheme = scheme, provider = provider)
        applyTtsConfig()
    }

    fun addScheme(item: TtsSchemeItem) {
        val updated = _ttsSettings.value.schemes + item
        settingsRepository.setTtsSchemes(updated)
        settingsRepository.setActiveSchemeId(item.id)
        settingsRepository.setTtsProvider(TtsProvider.API)
        _ttsSettings.value = _ttsSettings.value.copy(
            schemes = updated,
            activeSchemeId = item.id,
            provider = TtsProvider.API
        )
        applyTtsConfig()
    }

    fun updateScheme(item: TtsSchemeItem) {
        val updated = _ttsSettings.value.schemes.map { if (it.id == item.id) item else it }
        settingsRepository.setTtsSchemes(updated)
        _ttsSettings.value = _ttsSettings.value.copy(schemes = updated)
        applyTtsConfig()
    }

    fun removeScheme(id: String) {
        val updated = _ttsSettings.value.schemes.filter { it.id != id }
        settingsRepository.setTtsSchemes(updated)
        val newActiveId = if (_ttsSettings.value.activeSchemeId == id) {
            updated.firstOrNull()?.id ?: ""
        } else {
            _ttsSettings.value.activeSchemeId
        }
        settingsRepository.setActiveSchemeId(newActiveId)
        if (updated.isEmpty()) {
            settingsRepository.setTtsProvider(TtsProvider.SYSTEM)
        }
        _ttsSettings.value = _ttsSettings.value.copy(
            schemes = updated,
            activeSchemeId = newActiveId,
            provider = if (updated.isEmpty()) TtsProvider.SYSTEM else _ttsSettings.value.provider
        )
        applyTtsConfig()
    }

    fun selectScheme(id: String) {
        settingsRepository.setActiveSchemeId(id)
        _ttsSettings.value = _ttsSettings.value.copy(activeSchemeId = id)
        applyTtsConfig()
    }

    fun updateActiveSchemeSpeed(speed: String) {
        val active = _ttsSettings.value.activeScheme ?: return
        updateScheme(active.copy(speed = speed))
    }

    fun updateActiveSchemeDelay(delay: String) {
        val active = _ttsSettings.value.activeScheme ?: return
        updateScheme(active.copy(delay = delay))
    }

    fun updateActiveSchemeVoice(voice: String) {
        val active = _ttsSettings.value.activeScheme ?: return
        updateScheme(active.copy(voice = voice))
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
