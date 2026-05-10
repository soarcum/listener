package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ankilistener.app.data.AiAnswerApiClient
import com.ankilistener.app.data.AiReviewRepository
import com.ankilistener.app.data.AiReviewTurnRecord
import com.ankilistener.app.data.AiSettings
import com.ankilistener.app.data.AnkiRepository
import com.ankilistener.app.data.Card
import com.ankilistener.app.data.Deck
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.util.AppLogger
import com.ankilistener.app.util.AudioAnswerRecorder
import com.ankilistener.app.util.HtmlUtils
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.VibrateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReviewState {
    FRONT, BACK, FINISHED, LOADING
}

/**
 * Prefetch/cache status for the current review session.
 */
data class PrefetchStatus(
    val totalCards: Int = 0,
    val cachedFrontCount: Int = 0,
    val cachedBackCount: Int = 0,
    val prefetchCount: Int = 0
)

enum class AiAnswerPhase {
    IDLE, RECORDING, SUBMITTING
}

data class AiAnswerUiState(
    val enabled: Boolean = false,
    val phase: AiAnswerPhase = AiAnswerPhase.IDLE,
    val activePrompt: String = "",
    val transcript: String = "",
    val score: Int? = null,
    val feedback: String = "",
    val correction: String = "",
    val followUpQuestion: String = "",
    val savedRecordPath: String? = null,
    val turnCount: Int = 0,
    val error: String? = null
) {
    val hasResult: Boolean
        get() = transcript.isNotBlank() || feedback.isNotBlank() || correction.isNotBlank() || score != null
}

class ReviewViewModel(
    private val repository: AnkiRepository,
    private val ttsManager: TtsManager,
    private val vibrateManager: VibrateManager,
    private val settingsRepository: SettingsRepository,
    private val audioAnswerRecorder: AudioAnswerRecorder,
    private val aiAnswerApiClient: AiAnswerApiClient,
    private val aiReviewRepository: AiReviewRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ReviewVM"
    }

    private val _decks = mutableStateOf<List<Deck>>(emptyList())
    val decks: State<List<Deck>> = _decks

    private val _currentCards = mutableStateOf<List<Card>>(emptyList())
    private val _currentIndex = mutableStateOf(0)
    
    private val _reviewState = mutableStateOf(ReviewState.LOADING)
    val reviewState: State<ReviewState> = _reviewState

data class FeedbackEvent(val message: String, val id: Long = System.currentTimeMillis())

    private val _gestureFeedback = mutableStateOf<FeedbackEvent?>(null)
    val gestureFeedback: State<FeedbackEvent?> = _gestureFeedback

    fun clearGestureFeedback() {
        _gestureFeedback.value = null
    }

    private val _fontScale = mutableStateOf(1f)
    val fontScale: State<Float> = _fontScale

    private val _prefetchStatus = mutableStateOf(PrefetchStatus())
    val prefetchStatus: State<PrefetchStatus> = _prefetchStatus

    private val _questionPlaybackFinished = mutableStateOf(false)
    val questionPlaybackFinished: State<Boolean> = _questionPlaybackFinished

    private val _aiAnswerState = mutableStateOf(AiAnswerUiState())
    val aiAnswerState: State<AiAnswerUiState> = _aiAnswerState

    val currentCard: Card? get() = _currentCards.value.getOrNull(_currentIndex.value)
    private var currentDeckId: Long? = null
    private var aiSessionId: String? = null
    private val aiTurnHistory = mutableListOf<AiReviewTurnRecord>()

    fun loadDecks() {
        viewModelScope.launch {
            val decks = withContext(Dispatchers.IO) {
                repository.getDeckList()
            }
            _decks.value = decks
        }
    }

    fun startReview(deckId: Long) {
        currentDeckId = deckId
        AppLogger.i(TAG, "startReview(deckId=$deckId)")
        viewModelScope.launch {
            _reviewState.value = ReviewState.LOADING
            val cards = withContext(Dispatchers.IO) {
                repository.getCardsToReview(deckId)
            }
            AppLogger.i(TAG, "Fetched ${cards.size} cards for review")
            settingsRepository.setLastDeckId(deckId)
            _currentCards.value = cards
            _currentIndex.value = 0
            if (cards.isNotEmpty()) {
                showFront()
                prefetchUpcoming()
            } else {
                finishReview()
            }
        }
    }

    private fun showFront() {
        _reviewState.value = ReviewState.FRONT
        currentCard?.let {
            resetAiSessionForCard(it)
            _questionPlaybackFinished.value = false
            AppLogger.d(TAG, "Showing Front: noteId=${it.id}, ord=${it.ord}, index=${_currentIndex.value}")
            speakFrontQuestion(it)
        }
    }

    private fun showBack() {
        cancelAiRecordingIfNeeded()
        _reviewState.value = ReviewState.BACK
        _questionPlaybackFinished.value = false
        vibrateManager.vibrateShort()
        currentCard?.let {
            AppLogger.d(TAG, "Showing Back: noteId=${it.id}, ord=${it.ord}")
            ttsManager.stop()
            ttsManager.speak(getBackTtsText(it))
        }
    }

    private fun getBackTtsText(card: Card): String {
        return if (settingsRepository.getSkipQuestionOnBack()) {
            HtmlUtils.extractAnswerOnly(card.back)
        } else {
            HtmlUtils.extractTtsText(card.back)
        }
    }

    private fun resetAiSessionForCard(card: Card) {
        audioAnswerRecorder.cancel()
        aiTurnHistory.clear()
        aiSessionId = aiReviewRepository.createSessionId(card)
        _aiAnswerState.value = AiAnswerUiState(
            enabled = settingsRepository.getAiEnabled()
        )
    }

    private fun currentAiSettings(): AiSettings {
        return AiSettings(
            enabled = settingsRepository.getAiEnabled(),
            endpoint = settingsRepository.getAiEndpoint(),
            apiKey = settingsRepository.getAiApiKey(),
            model = settingsRepository.getAiModel(),
            followUpEnabled = settingsRepository.getAiFollowUpEnabled()
        )
    }

    private fun speakFrontQuestion(card: Card) {
        _questionPlaybackFinished.value = false
        val noteId = card.id
        val ord = card.ord
        ttsManager.speak(HtmlUtils.extractTtsText(card.front)) {
            val stillCurrent = currentCard?.let { current -> current.id == noteId && current.ord == ord } == true
            if (stillCurrent && _reviewState.value == ReviewState.FRONT) {
                _questionPlaybackFinished.value = true
            }
        }
    }

    private fun speakFollowUpQuestion(card: Card, question: String) {
        if (question.isBlank()) return
        _questionPlaybackFinished.value = false
        val noteId = card.id
        val ord = card.ord
        ttsManager.speak(question) {
            val stillCurrent = currentCard?.let { current -> current.id == noteId && current.ord == ord } == true
            val sameQuestion = _aiAnswerState.value.followUpQuestion == question
            if (stillCurrent && sameQuestion && _reviewState.value == ReviewState.FRONT) {
                _questionPlaybackFinished.value = true
            }
        }
    }

    private fun nextCard() {
        cancelAiRecordingIfNeeded()
        val oldIndex = _currentIndex.value
        _currentIndex.value++
        AppLogger.d(TAG, "nextCard: $oldIndex -> ${_currentIndex.value} / ${_currentCards.value.size}")
        if (_currentIndex.value < _currentCards.value.size) {
            showFront()
            prefetchUpcoming()
        } else {
            finishReview()
        }
    }

    private fun buryCurrentCard() {
        currentCard?.let { card ->
            AppLogger.i(TAG, "Action: Bury card noteId=${card.id}, ord=${card.ord}")
            viewModelScope.launch(Dispatchers.IO) {
                repository.buryCard(card, currentDeckId)
                withContext(Dispatchers.Main) {
                    nextCard()
                }
            }
        }
    }

    private fun undoLastAction() {
        if (_currentIndex.value > 0) {
            AppLogger.w(TAG, "Action: Undo - only local navigation back, AnkiDroid API does not support undo")
            vibrateManager.vibrateMedium()
            _currentIndex.value--
            showFront()
            // Note: repository.undoReview() is a no-op since AnkiDroid API doesn't support undo
        }
    }

    private fun finishReview() {
        cancelAiRecordingIfNeeded()
        AppLogger.i(TAG, "Review session finished. Reviewed ${_currentIndex.value} cards.")
        _reviewState.value = ReviewState.FINISHED
        _questionPlaybackFinished.value = false
        ttsManager.speak("复习完成")
    }

    fun toggleMark() {
        currentCard?.let { card ->
            AppLogger.i(TAG, "Action: Toggle mark card noteId=${card.id}")
            viewModelScope.launch(Dispatchers.IO) {
                val newMarkedStatus = repository.markCard(card)
                withContext(Dispatchers.Main) {
                    // Update the card in our list to reflect the new status
                    val updatedCards = _currentCards.value.toMutableList()
                    updatedCards[_currentIndex.value] = card.copy(isMarked = newMarkedStatus)
                    _currentCards.value = updatedCards
                    
                    vibrateManager.vibrateDoubleShort()
                }
            }
        }
    }

    // ---- Prefetch logic ----

    private fun prefetchUpcoming() {
        viewModelScope.launch {
            val prefetchCount = settingsRepository.getPrefetchCount()
            val cards = _currentCards.value
            val currentIdx = _currentIndex.value

            val endIdx = (currentIdx + prefetchCount).coerceAtMost(cards.size)
            for (i in currentIdx until endIdx) {
                val card = cards[i]
                val frontText = HtmlUtils.extractTtsText(card.front)
                val backText = getBackTtsText(card)
                ttsManager.prefetch(frontText)
                ttsManager.prefetch(backText)
            }

            delay(500)
            updatePrefetchStatus()
        }
    }

    fun updatePrefetchStatus() {
        val cards = _currentCards.value
        val prefetchCount = settingsRepository.getPrefetchCount()
        val currentIdx = _currentIndex.value

        val endIdx = (currentIdx + prefetchCount).coerceAtMost(cards.size)
        var cachedFront = 0
        var cachedBack = 0

        for (i in currentIdx until endIdx) {
            val card = cards[i]
            if (ttsManager.isCached(HtmlUtils.extractTtsText(card.front))) cachedFront++
            if (ttsManager.isCached(getBackTtsText(card))) cachedBack++
        }

        _prefetchStatus.value = PrefetchStatus(
            totalCards = endIdx - currentIdx,
            cachedFrontCount = cachedFront,
            cachedBackCount = cachedBack,
            prefetchCount = prefetchCount
        )
    }

    // ---- AI voice answer ----

    fun startAiRecording() {
        val card = currentCard ?: return
        val settings = currentAiSettings()

        if (!settings.enabled) {
            _aiAnswerState.value = _aiAnswerState.value.copy(
                enabled = false,
                error = "请先在设置中开启 AI 回答"
            )
            return
        }

        if (_reviewState.value != ReviewState.FRONT) {
            _aiAnswerState.value = _aiAnswerState.value.copy(error = "请在问题正面回答")
            return
        }

        val latestState = _aiAnswerState.value
        val prompt = latestState.followUpQuestion.ifBlank { HtmlUtils.extractTtsText(card.front) }
        if (!_questionPlaybackFinished.value) {
            _aiAnswerState.value = latestState.copy(error = "请等待问题朗读完成")
            return
        }

        try {
            ttsManager.stop()
            audioAnswerRecorder.start()
            _aiAnswerState.value = latestState.copy(
                enabled = true,
                phase = AiAnswerPhase.RECORDING,
                activePrompt = prompt,
                transcript = "",
                score = null,
                feedback = "",
                correction = "",
                error = null
            )
            AppLogger.i(TAG, "AI answer recording started for noteId=${card.id}, ord=${card.ord}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start AI answer recording", e)
            _aiAnswerState.value = latestState.copy(error = "录音启动失败：${e.message}")
        }
    }

    fun stopAndSubmitAiRecording() {
        val card = currentCard ?: return
        val activePrompt = _aiAnswerState.value.activePrompt.ifBlank {
            _aiAnswerState.value.followUpQuestion.ifBlank { HtmlUtils.extractTtsText(card.front) }
        }

        val audioFile = try {
            audioAnswerRecorder.stop()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop AI answer recording", e)
            _aiAnswerState.value = _aiAnswerState.value.copy(
                phase = AiAnswerPhase.IDLE,
                error = "录音保存失败：${e.message}"
            )
            return
        }

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _aiAnswerState.value = _aiAnswerState.value.copy(
                phase = AiAnswerPhase.IDLE,
                error = "没有录到有效音频"
            )
            return
        }

        val settings = currentAiSettings()
        _aiAnswerState.value = _aiAnswerState.value.copy(
            phase = AiAnswerPhase.SUBMITTING,
            error = null
        )

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    aiAnswerApiClient.evaluateAnswer(
                        settings = settings,
                        card = card,
                        prompt = activePrompt,
                        audioFile = audioFile,
                        turnHistory = aiTurnHistory.toList()
                    )
                }

                val followUpQuestion = if (settings.followUpEnabled) {
                    result.followUpQuestion
                } else {
                    ""
                }

                val turn = AiReviewTurnRecord(
                    turnIndex = aiTurnHistory.size + 1,
                    prompt = activePrompt,
                    audioFilePath = audioFile.absolutePath,
                    transcript = result.transcript,
                    score = result.score,
                    feedback = result.feedback,
                    correction = result.correction,
                    followUpQuestion = followUpQuestion,
                    rawResponse = result.rawResponse
                )
                aiTurnHistory.add(turn)

                val recordFile = withContext(Dispatchers.IO) {
                    aiReviewRepository.saveSession(
                        card = card,
                        sessionId = aiSessionId ?: aiReviewRepository.createSessionId(card),
                        turns = aiTurnHistory.toList()
                    )
                }

                val stillCurrent = currentCard?.let { current ->
                    current.id == card.id && current.ord == card.ord
                } == true
                if (!stillCurrent || _reviewState.value != ReviewState.FRONT) {
                    AppLogger.i(TAG, "AI answer saved for previous card: ${recordFile.absolutePath}")
                    return@launch
                }

                _aiAnswerState.value = _aiAnswerState.value.copy(
                    phase = AiAnswerPhase.IDLE,
                    transcript = result.transcript,
                    score = result.score,
                    feedback = result.feedback,
                    correction = result.correction,
                    followUpQuestion = followUpQuestion,
                    savedRecordPath = recordFile.absolutePath,
                    turnCount = aiTurnHistory.size,
                    error = null
                )
                if (followUpQuestion.isNotBlank()) {
                    speakFollowUpQuestion(card, followUpQuestion)
                }
                AppLogger.i(TAG, "AI answer saved: ${recordFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "AI answer submission failed", e)
                _aiAnswerState.value = _aiAnswerState.value.copy(
                    phase = AiAnswerPhase.IDLE,
                    error = "AI 处理失败：${e.message}"
                )
            }
        }
    }

    fun cancelAiRecording() {
        audioAnswerRecorder.cancel()
        _aiAnswerState.value = _aiAnswerState.value.copy(
            phase = AiAnswerPhase.IDLE,
            error = null
        )
    }

    private fun cancelAiRecordingIfNeeded() {
        if (_aiAnswerState.value.phase == AiAnswerPhase.RECORDING) {
            audioAnswerRecorder.cancel()
            _aiAnswerState.value = _aiAnswerState.value.copy(
                phase = AiAnswerPhase.IDLE,
                error = null
            )
        }
    }

    fun onAudioPermissionDenied() {
        _aiAnswerState.value = _aiAnswerState.value.copy(error = "需要麦克风权限才能录音回答")
    }

    // ---- Gesture Actions ----

    fun onSingleTap() {
        handleGesture(GestureType.SINGLE_TAP)
    }

    fun onDoubleTap() {
        handleGesture(GestureType.DOUBLE_TAP)
    }

    fun onSwipeLeft() {
        handleGesture(GestureType.SWIPE_LEFT)
    }

    fun onSwipeRight() {
        handleGesture(GestureType.SWIPE_RIGHT)
    }

    fun onSwipeDown() {
        handleGesture(GestureType.SWIPE_DOWN)
    }
    
    fun onSwipeUp() {
        handleGesture(GestureType.SWIPE_UP)
    }

    fun onLongPress() {
        handleGesture(GestureType.LONG_PRESS)
    }

    fun onTwoFingerTap() {
        handleGesture(GestureType.TWO_FINGER_TAP)
    }

    fun onScaleChange(scale: Float) {
        _fontScale.value = (_fontScale.value * scale).coerceIn(0.5f, 3.0f)
    }

    private fun handleGesture(gestureType: GestureType) {
        val mappings = settingsRepository.getAllMappings()
        val actionsForGesture = mappings.filter { it.value == gestureType }.keys
        
        if (actionsForGesture.isEmpty()) return

        val action = when (_reviewState.value) {
            ReviewState.FRONT -> {
                actionsForGesture.find { it == GestureAction.SHOW_ANSWER }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.UNDO }
            }
            ReviewState.BACK -> {
                actionsForGesture.find { it == GestureAction.ANSWER_AGAIN || it == GestureAction.ANSWER_HARD || it == GestureAction.ANSWER_GOOD || it == GestureAction.ANSWER_EASY }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.UNDO }
            }
            else -> null
        } ?: return

        val gestureName = when (gestureType) {
            GestureType.NONE -> "无"
            GestureType.SINGLE_TAP -> "单击"
            GestureType.DOUBLE_TAP -> "双击"
            GestureType.SWIPE_LEFT -> "左滑"
            GestureType.SWIPE_RIGHT -> "右滑"
            GestureType.SWIPE_UP -> "上滑"
            GestureType.SWIPE_DOWN -> "下滑"
            GestureType.LONG_PRESS -> "长按"
            GestureType.TWO_FINGER_TAP -> "双指点击"
        }
        
        val actionName = when (action) {
            GestureAction.NONE -> "无操作"
            GestureAction.SHOW_ANSWER -> "显示答案"
            GestureAction.PLAY_TTS -> "发音"
            GestureAction.ANSWER_AGAIN -> "重来"
            GestureAction.ANSWER_HARD -> "困难"
            GestureAction.ANSWER_GOOD -> "良好"
            GestureAction.ANSWER_EASY -> "简单"
            GestureAction.SKIP -> "跳过"
            GestureAction.MARK -> "标记"
            GestureAction.UNDO -> "撤销"
        }
        
        _gestureFeedback.value = FeedbackEvent("$gestureName ($actionName)")
        executeAction(action)
    }

    private fun executeAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> { /* Do nothing */ }
            GestureAction.SHOW_ANSWER -> {
                if (_reviewState.value == ReviewState.FRONT) {
                    showBack()
                }
            }
            GestureAction.PLAY_TTS -> {
                currentCard?.let {
                    val text = if (_reviewState.value == ReviewState.BACK) {
                        getBackTtsText(it)
                    } else {
                        HtmlUtils.extractTtsText(it.front)
                    }
                    if (_reviewState.value == ReviewState.FRONT) {
                        val followUpQuestion = _aiAnswerState.value.followUpQuestion
                        if (followUpQuestion.isNotBlank()) {
                            speakFollowUpQuestion(it, followUpQuestion)
                        } else {
                            speakFrontQuestion(it)
                        }
                    } else {
                        ttsManager.speak(text)
                    }
                }
            }
            GestureAction.ANSWER_AGAIN -> answerCardWithEase(AnkiRepository.EASE_AGAIN)
            GestureAction.ANSWER_HARD -> answerCardWithEase(AnkiRepository.EASE_HARD)
            GestureAction.ANSWER_GOOD -> answerCardWithEase(AnkiRepository.EASE_GOOD)
            GestureAction.ANSWER_EASY -> answerCardWithEase(AnkiRepository.EASE_EASY)
            GestureAction.SKIP -> {
                vibrateManager.vibrateShort()
                buryCurrentCard()
            }
            GestureAction.MARK -> {
                toggleMark()
            }
            GestureAction.UNDO -> {
                undoLastAction()
            }
        }
    }

    private fun answerCardWithEase(ease: Int) {
        if (_reviewState.value == ReviewState.BACK) {
            when (ease) {
                AnkiRepository.EASE_AGAIN -> vibrateManager.vibrateLong()
                AnkiRepository.EASE_HARD -> vibrateManager.vibrateMedium()
                AnkiRepository.EASE_GOOD -> vibrateManager.vibrateDoubleShort()
                AnkiRepository.EASE_EASY -> vibrateManager.vibrateShort()
            }
            answerCard(ease)
        }
    }

    private fun answerCard(ease: Int) {
        currentCard?.let { card ->
            AppLogger.i(TAG, "Action: Answer card noteId=${card.id}, ord=${card.ord}, ease=$ease")
            viewModelScope.launch(Dispatchers.IO) {
                repository.answerCard(card, ease, currentDeckId)
                withContext(Dispatchers.Main) {
                    nextCard()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioAnswerRecorder.cancel()
        ttsManager.release()
    }
}
