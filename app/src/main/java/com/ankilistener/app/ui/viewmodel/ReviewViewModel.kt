package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ankilistener.app.data.AiAnswerApiClient
import com.ankilistener.app.data.AiReviewRepository
import com.ankilistener.app.data.AiReviewTurnRecord
import com.ankilistener.app.data.AiSettings
import com.ankilistener.app.data.AnkiRepository
import com.ankilistener.app.data.Card
import com.ankilistener.app.data.ConceptCard
import com.ankilistener.app.data.ConceptReviewState
import com.ankilistener.app.data.ConceptScheduleRepository
import com.ankilistener.app.data.FollowUpCard
import com.ankilistener.app.data.Deck
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.util.AppLogger
import com.ankilistener.app.util.AudioAnswerRecorder
import com.ankilistener.app.util.ConceptCardParser
import com.ankilistener.app.util.HtmlUtils
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.VibrateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReviewState {
    FRONT, BACK, CONCEPT_FRONT, CONCEPT_BACK, FOLLOWUP_FRONT, FOLLOWUP_BACK, FINISHED, LOADING, NO_CARDS
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
    private val aiReviewRepository: AiReviewRepository,
    private val conceptScheduleRepository: ConceptScheduleRepository
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

    // ---- Concept review state ----
    private val _allConceptsForCurrentCard = mutableStateOf<List<ConceptCard>>(emptyList())
    val allConceptsForCurrentCard: State<List<ConceptCard>> = _allConceptsForCurrentCard

    private val _dueConceptQueue = mutableStateOf<List<ConceptCard>>(emptyList())
    val dueConceptQueue: State<List<ConceptCard>> = _dueConceptQueue

    private val _currentConceptIndex = mutableStateOf(0)
    val currentConceptIndex: State<Int> = _currentConceptIndex

    val currentConcept: ConceptCard?
        get() = _dueConceptQueue.value.getOrNull(_currentConceptIndex.value)

    fun getCurrentConceptReviewState(): ConceptReviewState? {
        val concept = currentConcept ?: return null
        val card = currentCard ?: return null
        val key = conceptScheduleRepository.buildKey(card.id, card.ord, concept.id)
        return conceptScheduleRepository.getState(key)
    }

    /**
     * Returns a map of concept title/id to color based on their review state.
     * Used for highlighting [[concept]] links in the card back.
     */
    fun getConceptColorMap(): Map<String, Color> {
        val card = currentCard ?: return emptyMap()
        val concepts = _allConceptsForCurrentCard.value
        if (concepts.isEmpty()) return emptyMap()

        val colorMap = mutableMapOf<String, Color>()
        for (concept in concepts) {
            val key = conceptScheduleRepository.buildKey(card.id, card.ord, concept.id)
            val state = conceptScheduleRepository.getState(key)
            val color = when (state?.lastEase) {
                ConceptReviewState.EASE_AGAIN -> Color(0xFFE53935) // Red
                ConceptReviewState.EASE_HARD -> Color(0xFFFF9800) // Orange
                ConceptReviewState.EASE_GOOD -> Color(0xFF1E88E5) // Blue
                ConceptReviewState.EASE_EASY -> Color(0xFF43A047) // Green
                else -> Color(0xFF1E88E5) // Default blue for new concepts
            }
            // Map both title and id for matching
            if (concept.title.isNotBlank()) {
                colorMap[concept.title] = color
            }
            colorMap[concept.id] = color
        }
        return colorMap
    }

    private val _conceptReviewResults = mutableStateOf<Map<String, Int>>(emptyMap())
    val conceptReviewResults: State<Map<String, Int>> = _conceptReviewResults

    // ---- Follow-up review state ----
    private val _allFollowUpsForCurrentCard = mutableStateOf<List<FollowUpCard>>(emptyList())
    val allFollowUpsForCurrentCard: State<List<FollowUpCard>> = _allFollowUpsForCurrentCard

    private val _dueFollowUpQueue = mutableStateOf<List<FollowUpCard>>(emptyList())
    val dueFollowUpQueue: State<List<FollowUpCard>> = _dueFollowUpQueue

    private val _currentFollowUpIndex = mutableStateOf(0)
    val currentFollowUpIndex: State<Int> = _currentFollowUpIndex

    val currentFollowUp: FollowUpCard?
        get() = _dueFollowUpQueue.value.getOrNull(_currentFollowUpIndex.value)

    private val _followUpReviewResults = mutableStateOf<Map<String, Int>>(emptyMap())
    val followUpReviewResults: State<Map<String, Int>> = _followUpReviewResults

    // ---- Segmented Response state ----
    private val _currentSegmentStep = mutableStateOf(0)
    val currentSegmentStep: State<Int> = _currentSegmentStep

    private val _revealSteps = mutableStateOf<List<String>>(emptyList())
    val revealSteps: State<List<String>> = _revealSteps

    private val _ttsSteps = mutableStateOf<List<String>>(emptyList())

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
            try {
                AppLogger.i(TAG, "Step 1: Setting LOADING state")
                _reviewState.value = ReviewState.LOADING
                AppLogger.i(TAG, "Step 2: Fetching cards for deckId=$deckId")
                val cards = withContext(Dispatchers.IO) {
                    repository.getCardsToReview(deckId)
                }
                AppLogger.i(TAG, "Step 3: Fetched ${cards.size} cards")
                settingsRepository.setLastDeckId(deckId)
                _currentCards.value = cards
                _currentIndex.value = 0
                if (cards.isNotEmpty()) {
                    AppLogger.i(TAG, "Step 4: Calling showFront()")
                    showFront()
                    AppLogger.i(TAG, "Step 5: Calling prefetchUpcoming()")
                    prefetchUpcoming()
                    AppLogger.i(TAG, "Step 6: All done")
                } else {
                    AppLogger.i(TAG, "Step 4: No cards")
                    _reviewState.value = ReviewState.NO_CARDS
                }
            } catch (e: Throwable) {
                AppLogger.e(TAG, "startReview CRASHED at some step", e)
                _reviewState.value = ReviewState.FINISHED
            }
        }
    }

    private fun showFront() {
        _reviewState.value = ReviewState.FRONT
        currentCard?.let {
            try {
                resetAiSessionForCard(it)
                _questionPlaybackFinished.value = false
                AppLogger.d(TAG, "Showing Front: noteId=${it.id}, ord=${it.ord}, index=${_currentIndex.value}")
                speakFrontQuestion(it)
            } catch (e: Throwable) {
                AppLogger.e(TAG, "showFront failed for noteId=${it.id}", e)
            }
        }
    }

    private fun showBack() {
        cancelAiRecordingIfNeeded()
        _reviewState.value = ReviewState.BACK
        _questionPlaybackFinished.value = false
        vibrateManager.vibrateShort()
        // Prepare concept and follow-up queues for this card
        prepareConceptQueue()
        prepareFollowUpQueue()
        currentCard?.let {
            val hasConcepts = _dueConceptQueue.value.isNotEmpty()
            val hasFollowUps = _dueFollowUpQueue.value.isNotEmpty()
            AppLogger.i(TAG, "showBack: noteId=${it.id}, ord=${it.ord}, hasConcepts=$hasConcepts, hasFollowUps=$hasFollowUps")
            ttsManager.stop()
            
            val isSegmentedEnabled = settingsRepository.getSegmentedResponseEnabled()
            val answerOnly = HtmlUtils.extractAnswerOnlyHtml(HtmlUtils.removeAnkiListenerConceptBlocks(it.back))
            
            var useSegments = false
            if (isSegmentedEnabled) {
                // Find tags like (背景), （任务） at the beginning of a line or text, up to 10 chars inside
                val regex = Regex("(?m)^([(（][^)）]{1,10}[)）])")
                val matches = regex.findAll(answerOnly).toList()
                if (matches.size > 1 || (matches.size == 1 && matches[0].range.first == 0)) {
                    useSegments = true
                    val rSteps = mutableListOf<String>()
                    val tSteps = mutableListOf<String>()
                    var accumulated = ""
                    
                    if (matches.isNotEmpty() && matches[0].range.first > 0) {
                        val intro = answerOnly.substring(0, matches[0].range.first)
                        accumulated += intro
                        rSteps.add(accumulated.trim())
                        tSteps.add(intro.trim())
                    }
                    
                    for (i in matches.indices) {
                        val match = matches[i]
                        val label = match.groupValues[1]
                        val start = match.range.last + 1
                        val end = if (i + 1 < matches.size) matches[i + 1].range.first else answerOnly.length
                        val content = answerOnly.substring(start, end)
                        
                        rSteps.add((accumulated + label).trim())
                        tSteps.add(label)
                        
                        accumulated += label + content
                        rSteps.add(accumulated.trim())
                        tSteps.add(content.trim())
                    }
                    
                    _revealSteps.value = rSteps
                    _ttsSteps.value = tSteps
                    _currentSegmentStep.value = 0
                }
            }
            
            if (!useSegments) {
                _revealSteps.value = emptyList()
                _ttsSteps.value = emptyList()
                _currentSegmentStep.value = 0
                
                val backTtsText = getBackTtsText(it)
                ttsManager.speak(backTtsText) {
                    AppLogger.d(TAG, "showBack: TTS finished callback, hasConcepts=$hasConcepts, hasFollowUps=$hasFollowUps, state=${_reviewState.value}")
                    if (_reviewState.value == ReviewState.BACK) {
                        when {
                            hasConcepts -> {
                                AppLogger.i(TAG, "showBack: auto-starting concept flow")
                                startConceptFlow()
                            }
                            hasFollowUps -> {
                                AppLogger.i(TAG, "showBack: auto-starting follow-up flow")
                                startFollowUpFlow()
                            }
                        }
                    }
                }
            } else {
                val ttsText = _ttsSteps.value[0]
                ttsManager.speak(ttsText) {
                    // Do not auto-advance for segments, let user interact
                }
            }
        }
    }

    private fun prepareConceptQueue() {
        val card = currentCard
        if (card == null) {
            AppLogger.w(TAG, "prepareConceptQueue: currentCard is null!")
            return
        }
        val conceptEnabled = settingsRepository.getConceptReviewEnabled()
        AppLogger.i(TAG, "prepareConceptQueue: noteId=${card.id}, ord=${card.ord}, conceptEnabled=$conceptEnabled")
        if (!conceptEnabled) {
            AppLogger.i(TAG, "prepareConceptQueue: concept review disabled, skipping")
            _allConceptsForCurrentCard.value = emptyList()
            _dueConceptQueue.value = emptyList()
            _conceptReviewResults.value = emptyMap()
            return
        }
        AppLogger.d(TAG, "prepareConceptQueue: calling ConceptCardParser.parse()")
        val concepts = ConceptCardParser.parse(card.back, card.id, card.ord)
        AppLogger.i(TAG, "prepareConceptQueue: parsed ${concepts.size} concepts")
        for ((i, c) in concepts.withIndex()) {
            AppLogger.d(TAG, "prepareConceptQueue: concept[$i] id=${c.id}, title=${c.title}, q=${c.question.take(50)}")
        }
        _allConceptsForCurrentCard.value = concepts
        _conceptReviewResults.value = emptyMap()

        val now = System.currentTimeMillis()
        val dueOnly = settingsRepository.getConceptDueOnly()
        AppLogger.d(TAG, "prepareConceptQueue: dueOnly=$dueOnly, now=$now")
        val due = if (dueOnly) {
            concepts.filter { c ->
                val key = conceptScheduleRepository.buildKey(card.id, card.ord, c.id)
                val isDue = conceptScheduleRepository.isDue(key, now)
                AppLogger.d(TAG, "prepareConceptQueue: concept ${c.id} key=$key isDue=$isDue")
                isDue
            }
        } else {
            concepts
        }

        _dueConceptQueue.value = due
        _currentConceptIndex.value = 0
        AppLogger.i(TAG, "prepareConceptQueue: ${due.size} due concepts out of ${concepts.size} total")
    }

    private fun startConceptFlow() {
        val concepts = _dueConceptQueue.value
        if (concepts.isEmpty()) return
        _currentConceptIndex.value = 0
        showConceptFront()
        val msg = "开始复习 ${concepts.size} 个相关概念"
        _gestureFeedback.value = FeedbackEvent(msg)
        AppLogger.i(TAG, msg)
    }

    private fun showConceptFront() {
        val concept = currentConcept ?: return
        _reviewState.value = ReviewState.CONCEPT_FRONT
        _questionPlaybackFinished.value = false
        AppLogger.d(TAG, "Concept front: ${concept.id} - ${concept.title}")
        ttsManager.stop()
        ttsManager.speak(concept.question) {
            if (_reviewState.value == ReviewState.CONCEPT_FRONT && currentConcept?.id == concept.id) {
                _questionPlaybackFinished.value = true
            }
        }
    }

    private fun showConceptBack() {
        val concept = currentConcept ?: return
        _reviewState.value = ReviewState.CONCEPT_BACK
        _questionPlaybackFinished.value = false
        AppLogger.d(TAG, "Concept back: ${concept.id}")
        ttsManager.stop()
        ttsManager.speak(concept.answer)
    }

    private fun answerConcept(ease: Int) {
        val concept = currentConcept ?: return
        val card = currentCard ?: return
        val key = conceptScheduleRepository.buildKey(card.id, card.ord, concept.id)
        val now = System.currentTimeMillis()
        val againDelay = settingsRepository.getConceptAgainDelayMinutes()

        val oldState = conceptScheduleRepository.getState(key)
        AppLogger.i(TAG, "Concept answer: ${concept.id}, ease=$ease, oldState=$oldState")

        conceptScheduleRepository.updateState(key, ease, now, againDelay)

        val newState = conceptScheduleRepository.getState(key)
        AppLogger.i(TAG, "Concept answer: ${concept.id}, newState=$newState")

        _conceptReviewResults.value = _conceptReviewResults.value + (concept.id to ease)

        when (ease) {
            ConceptReviewState.EASE_AGAIN -> vibrateManager.vibrateLong()
            ConceptReviewState.EASE_HARD -> vibrateManager.vibrateMedium()
            ConceptReviewState.EASE_GOOD -> vibrateManager.vibrateDoubleShort()
            ConceptReviewState.EASE_EASY -> vibrateManager.vibrateShort()
        }

        val nextIdx = _currentConceptIndex.value + 1
        if (nextIdx < _dueConceptQueue.value.size) {
            _currentConceptIndex.value = nextIdx
            showConceptFront()
        } else {
            finishConceptFlow()
        }
    }

    private fun finishConceptFlow() {
        AppLogger.i(TAG, "Concept flow finished")
        _gestureFeedback.value = FeedbackEvent("相关概念复习完成")

        val hasFollowUps = _dueFollowUpQueue.value.isNotEmpty()
        if (hasFollowUps) {
            startFollowUpFlow()
        } else {
            _reviewState.value = ReviewState.BACK
            currentCard?.let {
                ttsManager.stop()
                ttsManager.speak(getBackTtsText(it))
            }
        }
    }

    private fun prepareFollowUpQueue() {
        val card = currentCard ?: return
        val followUps = ConceptCardParser.parseFollowUps(card.back, card.id, card.ord)
        AppLogger.i(TAG, "prepareFollowUpQueue: parsed ${followUps.size} follow-ups")
        _allFollowUpsForCurrentCard.value = followUps
        _followUpReviewResults.value = emptyMap()

        val now = System.currentTimeMillis()
        val dueOnly = settingsRepository.getConceptDueOnly()
        val due = if (dueOnly) {
            followUps.filter { fu ->
                val key = conceptScheduleRepository.buildFollowUpKey(card.id, card.ord, fu.id)
                conceptScheduleRepository.isDue(key, now)
            }
        } else {
            followUps
        }

        _dueFollowUpQueue.value = due
        _currentFollowUpIndex.value = 0
        AppLogger.i(TAG, "prepareFollowUpQueue: ${due.size} due follow-ups out of ${followUps.size} total")
    }

    private fun startFollowUpFlow() {
        val followUps = _dueFollowUpQueue.value
        if (followUps.isEmpty()) return
        _currentFollowUpIndex.value = 0
        showFollowUpFront()
        val msg = "开始复习 ${followUps.size} 个追问"
        _gestureFeedback.value = FeedbackEvent(msg)
        AppLogger.i(TAG, msg)
    }

    private fun showFollowUpFront() {
        val followUp = currentFollowUp ?: return
        _reviewState.value = ReviewState.FOLLOWUP_FRONT
        _questionPlaybackFinished.value = false
        AppLogger.d(TAG, "Follow-up front: ${followUp.id}")
        ttsManager.stop()
        ttsManager.speak(followUp.question) {
            if (_reviewState.value == ReviewState.FOLLOWUP_FRONT && currentFollowUp?.id == followUp.id) {
                _questionPlaybackFinished.value = true
            }
        }
    }

    private fun showFollowUpBack() {
        val followUp = currentFollowUp ?: return
        _reviewState.value = ReviewState.FOLLOWUP_BACK
        _questionPlaybackFinished.value = false
        AppLogger.d(TAG, "Follow-up back: ${followUp.id}")
        ttsManager.stop()
        ttsManager.speak(followUp.answer)
    }

    private fun answerFollowUp(ease: Int) {
        val followUp = currentFollowUp ?: return
        val card = currentCard ?: return
        val key = conceptScheduleRepository.buildFollowUpKey(card.id, card.ord, followUp.id)
        val now = System.currentTimeMillis()
        val againDelay = settingsRepository.getConceptAgainDelayMinutes()

        conceptScheduleRepository.updateState(key, ease, now, againDelay)
        _followUpReviewResults.value = _followUpReviewResults.value + (followUp.id to ease)

        when (ease) {
            ConceptReviewState.EASE_AGAIN -> vibrateManager.vibrateLong()
            ConceptReviewState.EASE_HARD -> vibrateManager.vibrateMedium()
            ConceptReviewState.EASE_GOOD -> vibrateManager.vibrateDoubleShort()
            ConceptReviewState.EASE_EASY -> vibrateManager.vibrateShort()
        }

        val nextIdx = _currentFollowUpIndex.value + 1
        if (nextIdx < _dueFollowUpQueue.value.size) {
            _currentFollowUpIndex.value = nextIdx
            showFollowUpFront()
        } else {
            finishFollowUpFlow()
        }
    }

    private fun finishFollowUpFlow() {
        AppLogger.i(TAG, "Follow-up flow finished, returning to main card BACK")
        _gestureFeedback.value = FeedbackEvent("追问复习完成")
        _reviewState.value = ReviewState.BACK
        currentCard?.let {
            ttsManager.stop()
            ttsManager.speak(getBackTtsText(it))
        }
    }

    private fun getBackTtsText(card: Card): String {
        val cleaned = HtmlUtils.removeAnkiListenerConceptBlocks(card.back)
        return HtmlUtils.extractTtsText(cleaned)
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
        clearConceptState()
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

    private fun clearConceptState() {
        _allConceptsForCurrentCard.value = emptyList()
        _dueConceptQueue.value = emptyList()
        _currentConceptIndex.value = 0
        _conceptReviewResults.value = emptyMap()
        _allFollowUpsForCurrentCard.value = emptyList()
        _dueFollowUpQueue.value = emptyList()
        _currentFollowUpIndex.value = 0
        _followUpReviewResults.value = emptyMap()
    }

    private fun buryCurrentCard() {
        currentCard?.let { card ->
            AppLogger.i(TAG, "Action: Bury card noteId=${card.id}, ord=${card.ord}")
            clearConceptState()
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
            AppLogger.w(TAG, "Action: Undo - only local navigation back, AnkiDroid API does not support undo. Concept schedule NOT rolled back.")
            vibrateManager.vibrateMedium()
            clearConceptState()
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

                // Prefetch concept TTS for upcoming cards
                val now = System.currentTimeMillis()
                if (settingsRepository.getConceptReviewEnabled()) {
                    val concepts = ConceptCardParser.parse(card.back, card.id, card.ord)
                    for (concept in concepts) {
                        val key = conceptScheduleRepository.buildKey(card.id, card.ord, concept.id)
                        if (conceptScheduleRepository.isDue(key, now)) {
                            ttsManager.prefetch(concept.question)
                            ttsManager.prefetch(concept.answer)
                        }
                    }
                }
                // Prefetch follow-up TTS (independent of concept setting)
                val followUps = ConceptCardParser.parseFollowUps(card.back, card.id, card.ord)
                for (fu in followUps) {
                    val key = conceptScheduleRepository.buildFollowUpKey(card.id, card.ord, fu.id)
                    if (conceptScheduleRepository.isDue(key, now)) {
                        ttsManager.prefetch(fu.question)
                        ttsManager.prefetch(fu.answer)
                    }
                }
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

        // In BACK state, check if segments are fully revealed
        val isFullyRevealed = _revealSteps.value.isEmpty() || _currentSegmentStep.value >= _revealSteps.value.size - 1

        // In BACK state with pending concepts or follow-ups, enter review flow (if fully revealed)
        if (_reviewState.value == ReviewState.BACK) {
            val pendingConceptCount = _dueConceptQueue.value.size - _conceptReviewResults.value.size
            val pendingFollowUpCount = _dueFollowUpQueue.value.size - _followUpReviewResults.value.size
            val conceptEntryActions = setOf(
                GestureAction.SHOW_ANSWER,
                GestureAction.ANSWER_AGAIN, GestureAction.ANSWER_HARD,
                GestureAction.ANSWER_GOOD, GestureAction.ANSWER_EASY
            )
            
            // If segments are not fully revealed, and the action is SHOW_ANSWER, we don't intercept it here.
            // Let executeAction handle it to advance the segment.
            val shouldInterceptForConcepts = isFullyRevealed || !actionsForGesture.contains(GestureAction.SHOW_ANSWER)
            
            if (shouldInterceptForConcepts) {
                if (pendingConceptCount > 0 && actionsForGesture.any { it in conceptEntryActions }) {
                    AppLogger.i(TAG, "Gesture in BACK with $pendingConceptCount pending concepts -> entering concept flow")
                    ttsManager.stop()
                    startConceptFlow()
                    return
                }
                if (pendingFollowUpCount > 0 && actionsForGesture.any { it in conceptEntryActions }) {
                    AppLogger.i(TAG, "Gesture in BACK with $pendingFollowUpCount pending follow-ups -> entering follow-up flow")
                    ttsManager.stop()
                    startFollowUpFlow()
                    return
                }
            }
        }

        val action = when (_reviewState.value) {
            ReviewState.FRONT -> {
                actionsForGesture.find { it == GestureAction.SHOW_ANSWER }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.UNDO }
            }
            ReviewState.BACK -> {
                actionsForGesture.find { it == GestureAction.ANSWER_AGAIN || it == GestureAction.ANSWER_HARD || it == GestureAction.ANSWER_GOOD || it == GestureAction.ANSWER_EASY }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.UNDO }
            }
            ReviewState.CONCEPT_FRONT -> {
                actionsForGesture.find { it == GestureAction.SHOW_ANSWER }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS }
            }
            ReviewState.CONCEPT_BACK -> {
                actionsForGesture.find { it == GestureAction.ANSWER_AGAIN || it == GestureAction.ANSWER_HARD || it == GestureAction.ANSWER_GOOD || it == GestureAction.ANSWER_EASY }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS }
            }
            ReviewState.FOLLOWUP_FRONT -> {
                actionsForGesture.find { it == GestureAction.SHOW_ANSWER }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS }
            }
            ReviewState.FOLLOWUP_BACK -> {
                actionsForGesture.find { it == GestureAction.ANSWER_AGAIN || it == GestureAction.ANSWER_HARD || it == GestureAction.ANSWER_GOOD || it == GestureAction.ANSWER_EASY }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS }
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
                when (_reviewState.value) {
                    ReviewState.FRONT -> showBack()
                    ReviewState.BACK -> {
                        if (_revealSteps.value.isNotEmpty() && _currentSegmentStep.value < _revealSteps.value.size - 1) {
                            _currentSegmentStep.value++
                            ttsManager.stop()
                            ttsManager.speak(_ttsSteps.value[_currentSegmentStep.value]) {
                                // Do not auto-advance segments
                            }
                        } else if (_revealSteps.value.isNotEmpty() && _currentSegmentStep.value == _revealSteps.value.size - 1) {
                            val hasConcepts = _dueConceptQueue.value.isNotEmpty()
                            val hasFollowUps = _dueFollowUpQueue.value.isNotEmpty()
                            when {
                                hasConcepts -> startConceptFlow()
                                hasFollowUps -> startFollowUpFlow()
                            }
                        }
                    }
                    ReviewState.CONCEPT_FRONT -> showConceptBack()
                    ReviewState.FOLLOWUP_FRONT -> showFollowUpBack()
                    else -> {}
                }
            }
            GestureAction.PLAY_TTS -> {
                when (_reviewState.value) {
                    ReviewState.FRONT -> {
                        currentCard?.let {
                            val followUpQuestion = _aiAnswerState.value.followUpQuestion
                            if (followUpQuestion.isNotBlank()) {
                                speakFollowUpQuestion(it, followUpQuestion)
                            } else {
                                speakFrontQuestion(it)
                            }
                        }
                    }
                    ReviewState.BACK -> {
                        currentCard?.let { ttsManager.speak(getBackTtsText(it)) }
                    }
                    ReviewState.CONCEPT_FRONT -> {
                        currentConcept?.let { ttsManager.speak(it.question) }
                    }
                    ReviewState.CONCEPT_BACK -> {
                        currentConcept?.let { ttsManager.speak(it.answer) }
                    }
                    ReviewState.FOLLOWUP_FRONT -> {
                        currentFollowUp?.let { ttsManager.speak(it.question) }
                    }
                    ReviewState.FOLLOWUP_BACK -> {
                        currentFollowUp?.let { ttsManager.speak(it.answer) }
                    }
                    else -> {}
                }
            }
            GestureAction.ANSWER_AGAIN -> {
                when (_reviewState.value) {
                    ReviewState.CONCEPT_BACK -> answerConcept(ConceptReviewState.EASE_AGAIN)
                    ReviewState.FOLLOWUP_BACK -> answerFollowUp(ConceptReviewState.EASE_AGAIN)
                    else -> answerCardWithEase(AnkiRepository.EASE_AGAIN)
                }
            }
            GestureAction.ANSWER_HARD -> {
                when (_reviewState.value) {
                    ReviewState.CONCEPT_BACK -> answerConcept(ConceptReviewState.EASE_HARD)
                    ReviewState.FOLLOWUP_BACK -> answerFollowUp(ConceptReviewState.EASE_HARD)
                    else -> answerCardWithEase(AnkiRepository.EASE_HARD)
                }
            }
            GestureAction.ANSWER_GOOD -> {
                when (_reviewState.value) {
                    ReviewState.CONCEPT_BACK -> answerConcept(ConceptReviewState.EASE_GOOD)
                    ReviewState.FOLLOWUP_BACK -> answerFollowUp(ConceptReviewState.EASE_GOOD)
                    else -> answerCardWithEase(AnkiRepository.EASE_GOOD)
                }
            }
            GestureAction.ANSWER_EASY -> {
                when (_reviewState.value) {
                    ReviewState.CONCEPT_BACK -> answerConcept(ConceptReviewState.EASE_EASY)
                    ReviewState.FOLLOWUP_BACK -> answerFollowUp(ConceptReviewState.EASE_EASY)
                    else -> answerCardWithEase(AnkiRepository.EASE_EASY)
                }
            }
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
            // Check if there are pending concepts or follow-ups
            val pendingConceptCount = _dueConceptQueue.value.size - _conceptReviewResults.value.size
            val pendingFollowUpCount = _dueFollowUpQueue.value.size - _followUpReviewResults.value.size
            val pendingCount = pendingConceptCount + pendingFollowUpCount
            if (pendingCount > 0) {
                _gestureFeedback.value = FeedbackEvent("还有 $pendingCount 个项目待复习")
                return
            }

            // Apply concept score constraints
            val effectiveEase = calculateEffectiveEase(ease)

            when (effectiveEase) {
                AnkiRepository.EASE_AGAIN -> vibrateManager.vibrateLong()
                AnkiRepository.EASE_HARD -> vibrateManager.vibrateMedium()
                AnkiRepository.EASE_GOOD -> vibrateManager.vibrateDoubleShort()
                AnkiRepository.EASE_EASY -> vibrateManager.vibrateShort()
            }
            if (effectiveEase != ease) {
                AppLogger.i(TAG, "Concept constraint: user chose $ease, submitting $effectiveEase")
            }
            answerCard(effectiveEase)
        }
    }

    private fun calculateEffectiveEase(userEase: Int): Int {
        val results = _conceptReviewResults.value + _followUpReviewResults.value
        if (results.isEmpty()) return userEase

        val worstConceptEase = results.values.minOrNull() ?: return userEase

        return when {
            // Any concept AGAIN -> main card forced AGAIN
            worstConceptEase == ConceptReviewState.EASE_AGAIN -> {
                if (userEase != AnkiRepository.EASE_AGAIN) {
                    _gestureFeedback.value = FeedbackEvent("概念未掌握，强制重来")
                }
                AnkiRepository.EASE_AGAIN
            }
            // Any concept HARD (no AGAIN) -> max HARD
            worstConceptEase == ConceptReviewState.EASE_HARD && userEase > AnkiRepository.EASE_HARD -> {
                _gestureFeedback.value = FeedbackEvent("概念有困难，限制为困难")
                AnkiRepository.EASE_HARD
            }
            else -> userEase
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
