package com.ankilistener.app.data

import android.content.Context
import android.content.SharedPreferences
import com.ankilistener.app.util.TtsProvider

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
    private val ttsPrefs: SharedPreferences = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
    private val aiPrefs: SharedPreferences = context.getSharedPreferences("ai_prefs_v1", Context.MODE_PRIVATE)

    // ---- Gesture Settings ----

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

    // ---- TTS Settings ----

    fun getTtsProvider(): TtsProvider {
        val name = ttsPrefs.getString("tts_provider", TtsProvider.SYSTEM.name) ?: TtsProvider.SYSTEM.name
        return try {
            TtsProvider.valueOf(name)
        } catch (e: Exception) {
            TtsProvider.SYSTEM
        }
    }

    fun setTtsProvider(provider: TtsProvider) {
        ttsPrefs.edit().putString("tts_provider", provider.name).apply()
    }

    fun getTtsBaseUrl(): String {
        return ttsPrefs.getString("tts_base_url", "http://172.22.64.1:3000") ?: "http://172.22.64.1:3000"
    }

    fun setTtsBaseUrl(url: String) {
        ttsPrefs.edit().putString("tts_base_url", url).apply()
    }

    fun getTtsSpeed(): String {
        return ttsPrefs.getString("tts_speed", "1.0") ?: "1.0"
    }

    fun setTtsSpeed(speed: String) {
        ttsPrefs.edit().putString("tts_speed", speed).apply()
    }

    fun getTtsDelay(): String {
        return ttsPrefs.getString("tts_delay", "5") ?: "5"
    }

    fun setTtsDelay(delay: String) {
        ttsPrefs.edit().putString("tts_delay", delay).apply()
    }

    fun getTtsVoice(): String {
        return ttsPrefs.getString("tts_voice", "zh_female_wenroutaozi_uranus_bigtts") ?: "zh_female_wenroutaozi_uranus_bigtts"
    }

    fun setTtsVoice(voice: String) {
        ttsPrefs.edit().putString("tts_voice", voice).apply()
    }

    fun getPrefetchCount(): Int {
        return ttsPrefs.getInt("prefetch_count", 3)
    }

    fun setPrefetchCount(count: Int) {
        ttsPrefs.edit().putInt("prefetch_count", count.coerceAtLeast(0)).apply()
    }


    fun getSkipQuestionOnBack(): Boolean {
        return ttsPrefs.getBoolean("skip_question_on_back", false)
    }

    fun setSkipQuestionOnBack(skip: Boolean) {
        ttsPrefs.edit().putBoolean("skip_question_on_back", skip).apply()
    }

    fun getLastDeckId(): Long {
        return ttsPrefs.getLong("last_deck_id", -1L)
    }

    fun setLastDeckId(deckId: Long) {
        ttsPrefs.edit().putLong("last_deck_id", deckId).apply()
    }

    // ---- Concept Review Settings ----

    fun getConceptReviewEnabled(): Boolean {
        return ttsPrefs.getBoolean("concept_review_enabled", true)
    }

    fun setConceptReviewEnabled(enabled: Boolean) {
        ttsPrefs.edit().putBoolean("concept_review_enabled", enabled).apply()
    }

    fun getConceptDueOnly(): Boolean {
        return ttsPrefs.getBoolean("concept_due_only", true)
    }

    fun setConceptDueOnly(dueOnly: Boolean) {
        ttsPrefs.edit().putBoolean("concept_due_only", dueOnly).apply()
    }

    fun getConceptAgainDelayMinutes(): Int {
        return ttsPrefs.getInt("concept_again_delay", 10)
    }

    fun setConceptAgainDelayMinutes(minutes: Int) {
        ttsPrefs.edit().putInt("concept_again_delay", minutes.coerceAtLeast(1)).apply()
    }

    // ---- AI Answer Settings ----

    fun getAiEnabled(): Boolean {
        return aiPrefs.getBoolean("enabled", false)
    }

    fun setAiEnabled(enabled: Boolean) {
        aiPrefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun getAiEndpoint(): String {
        return aiPrefs.getString(
            "endpoint",
            "http://172.22.64.1:3000/api/anki-listener/answer"
        ) ?: "http://172.22.64.1:3000/api/anki-listener/answer"
    }

    fun setAiEndpoint(endpoint: String) {
        aiPrefs.edit().putString("endpoint", endpoint).apply()
    }

    fun getAiApiKey(): String {
        return aiPrefs.getString("api_key", "") ?: ""
    }

    fun setAiApiKey(apiKey: String) {
        aiPrefs.edit().putString("api_key", apiKey).apply()
    }

    fun getAiModel(): String {
        return aiPrefs.getString("model", "default") ?: "default"
    }

    fun setAiModel(model: String) {
        aiPrefs.edit().putString("model", model).apply()
    }

    fun getAiFollowUpEnabled(): Boolean {
        return aiPrefs.getBoolean("follow_up_enabled", true)
    }

    fun setAiFollowUpEnabled(enabled: Boolean) {
        aiPrefs.edit().putBoolean("follow_up_enabled", enabled).apply()
    }
}
