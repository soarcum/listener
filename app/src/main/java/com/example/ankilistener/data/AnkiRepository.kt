package com.example.ankilistener.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.ichi2.anki.api.AddContentApi

class AnkiRepository(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val api = AddContentApi(context)

    companion object {
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        val DECKS_URI: Uri = Uri.parse("content://$AUTHORITY/decks")
        val CARDS_URI: Uri = Uri.parse("content://$AUTHORITY/cards")
        val SELECTED_DECK_ID_URI: Uri = Uri.parse("content://$AUTHORITY/selected_deck_id")
        
        // Eases
        const val EASE_AGAIN = 1
        const val EASE_HARD = 2
        const val EASE_GOOD = 3
        const val EASE_EASY = 4
    }

    fun isApiAvailable(): Boolean {
        return AddContentApi.getAnkiDroidPackageName(context) != null
    }

    fun getDeckList(): List<Deck> {
        val decks = mutableListOf<Deck>()
        val cursor: Cursor? = contentResolver.query(DECKS_URI, null, null, null, null)
        cursor?.use {
            val idIndex = it.getColumnIndex("id")
            val nameIndex = it.getColumnIndex("name")
            val dueIndex = it.getColumnIndex("due") // Note: Actual column names might vary by AnkiDroid version, usually 'name' and 'id' are standard.
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val name = it.getString(nameIndex)
                // Filter decks with due cards if possible, or just return all and let UI handle it
                decks.add(Deck(id, name))
            }
        }
        return decks
    }

    fun getCardsToReview(deckId: Long): List<Card> {
        val cards = mutableListOf<Card>()
        // Query cards for a specific deck that are due
        // AnkiDroid API usually provides a way to get 'selected' deck cards or by deck id
        val uri = Uri.withAppendedPath(DECKS_URI, "$deckId/cards")
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idIndex = it.getColumnIndex("id")
            val frontIndex = it.getColumnIndex("question") // Pre-rendered Front HTML
            val backIndex = it.getColumnIndex("answer")   // Pre-rendered Back HTML
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val front = it.getString(frontIndex) ?: ""
                val back = it.getString(backIndex) ?: ""
                cards.add(Card(id, front, back))
            }
        }
        return cards
    }

    fun answerCard(cardId: Long, ease: Int) {
        val values = ContentValues().apply {
            put("ease", ease)
        }
        val uri = Uri.withAppendedPath(CARDS_URI, cardId.toString())
        contentResolver.update(uri, values, null, null)
    }
}

data class Deck(val id: Long, val name: String)
data class Card(val id: Long, val front: String, val back: String)
