package com.ankilistener.app.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ankilistener.app.data.AnkiRepository
import com.ankilistener.app.data.Card
import com.ankilistener.app.data.Deck
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.data.SettingsRepository
import com.ankilistener.app.util.HtmlUtils
import com.ankilistener.app.util.TtsManager
import com.ankilistener.app.util.VibrateManager
import kotlinx.coroutines.launch

enum class ReviewState {
    FRONT, BACK, FINISHED, LOADING
}

class ReviewViewModel(
    private val repository: AnkiRepository,
    private val ttsManager: TtsManager,
    private val vibrateManager: VibrateManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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

    val currentCard: Card? get() = _currentCards.value.getOrNull(_currentIndex.value)

    fun loadDecks() {
        viewModelScope.launch {
            _decks.value = repository.getDeckList()
        }
    }

    fun startReview(deckId: Long) {
        viewModelScope.launch {
            _reviewState.value = ReviewState.LOADING
            val cards = repository.getCardsToReview(deckId)
            _currentCards.value = cards
            _currentIndex.value = 0
            if (cards.isNotEmpty()) {
                showFront()
            } else {
                finishReview()
            }
        }
    }

    private fun showFront() {
        _reviewState.value = ReviewState.FRONT
        currentCard?.let {
            ttsManager.speak(HtmlUtils.extractTtsText(it.front))
        }
    }

    private fun showBack() {
        _reviewState.value = ReviewState.BACK
        vibrateManager.vibrateShort()
        currentCard?.let {
            ttsManager.stop()
            ttsManager.speak(HtmlUtils.extractTtsText(it.back))
        }
    }

    private fun nextCard() {
        _currentIndex.value++
        if (_currentIndex.value < _currentCards.value.size) {
            showFront()
        } else {
            finishReview()
        }
    }

    private fun finishReview() {
        _reviewState.value = ReviewState.FINISHED
        ttsManager.speak("复习完成")
    }

    // Gesture Actions
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

    private fun handleGesture(gestureType: GestureType) {
        val mappings = settingsRepository.getAllMappings()
        val actionsForGesture = mappings.filter { it.value == gestureType }.keys
        
        if (actionsForGesture.isEmpty()) return

        // 根据当前状态选择最合适的动作 (互斥逻辑)
        val action = when (_reviewState.value) {
            ReviewState.FRONT -> {
                // 优先执行“显示答案”，其次是通用的 TTS/跳过/标记/撤销
                actionsForGesture.find { it == GestureAction.SHOW_ANSWER }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.MARK || it == GestureAction.UNDO }
            }
            ReviewState.BACK -> {
                // 优先执行“评分”，其次是通用的 TTS/跳过/标记/撤销
                actionsForGesture.find { it == GestureAction.ANSWER_AGAIN || it == GestureAction.ANSWER_HARD || it == GestureAction.ANSWER_GOOD || it == GestureAction.ANSWER_EASY }
                    ?: actionsForGesture.find { it == GestureAction.PLAY_TTS || it == GestureAction.SKIP || it == GestureAction.MARK || it == GestureAction.UNDO }
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
                    val text = if (_reviewState.value == ReviewState.BACK) it.back else it.front
                    ttsManager.speak(HtmlUtils.extractTtsText(text))
                }
            }
            GestureAction.ANSWER_AGAIN -> answerCardWithEase(AnkiRepository.EASE_AGAIN)
            GestureAction.ANSWER_HARD -> answerCardWithEase(AnkiRepository.EASE_HARD)
            GestureAction.ANSWER_GOOD -> answerCardWithEase(AnkiRepository.EASE_GOOD)
            GestureAction.ANSWER_EASY -> answerCardWithEase(AnkiRepository.EASE_EASY)
            GestureAction.SKIP -> {
                vibrateManager.vibrateShort()
                nextCard()
            }
            GestureAction.MARK -> {
                vibrateManager.vibrateDoubleShort()
            }
            GestureAction.UNDO -> {
                if (_currentIndex.value > 0) {
                    vibrateManager.vibrateMedium()
                    _currentIndex.value--
                    showFront()
                }
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
        currentCard?.let {
            repository.answerCard(it, ease)
            nextCard()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.release()
    }
}
