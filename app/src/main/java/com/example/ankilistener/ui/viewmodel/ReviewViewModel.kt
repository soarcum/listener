package com.example.ankilistener.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ankilistener.data.AnkiRepository
import com.example.ankilistener.data.Card
import com.example.ankilistener.data.Deck
import com.example.ankilistener.util.HtmlUtils
import com.example.ankilistener.util.TtsManager
import com.example.ankilistener.util.VibrateManager
import kotlinx.coroutines.launch

enum class ReviewState {
    FRONT, BACK, FINISHED, LOADING
}

class ReviewViewModel(
    private val repository: AnkiRepository,
    private val ttsManager: TtsManager,
    private val vibrateManager: VibrateManager
) : ViewModel() {

    private val _decks = mutableStateOf<List<Deck>>(emptyList())
    val decks: State<List<Deck>> = _decks

    private val _currentCards = mutableStateOf<List<Card>>(emptyList())
    private val _currentIndex = mutableStateOf(0)
    
    private val _reviewState = mutableStateOf(ReviewState.LOADING)
    val reviewState: State<ReviewState> = _reviewState

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
        if (_reviewState.value == ReviewState.FRONT) {
            showBack()
        }
    }

    fun onDoubleTap() {
        currentCard?.let {
            val text = if (_reviewState.value == ReviewState.BACK) it.back else it.front
            ttsManager.speak(HtmlUtils.extractTtsText(text))
        }
    }

    fun onSwipeLeft() {
        if (_reviewState.value == ReviewState.BACK) {
            answerCard(AnkiRepository.EASE_AGAIN)
            vibrateManager.vibrateLong()
        }
    }

    fun onSwipeRight() {
        if (_reviewState.value == ReviewState.BACK) {
            answerCard(AnkiRepository.EASE_GOOD)
            vibrateManager.vibrateDoubleShort()
        }
    }

    fun onSwipeDown() {
        if (_reviewState.value == ReviewState.BACK) {
            answerCard(AnkiRepository.EASE_HARD)
            vibrateManager.vibrateMedium()
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
