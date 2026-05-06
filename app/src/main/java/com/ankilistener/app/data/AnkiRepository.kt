package com.ankilistener.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.ichi2.anki.api.AddContentApi

class AnkiRepository(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")
        val DECKS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")
        val SCHEDULE_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
        val NOTES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
        
        // Column Names
        const val DECK_ID = "deck_id"
        const val DECK_NAME = "deck_name"
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        
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
            val idIndex = it.getColumnIndex(DECK_ID)
            val nameIndex = it.getColumnIndex(DECK_NAME)
            
            if (idIndex != -1 && nameIndex != -1) {
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex)
                    decks.add(Deck(id, name))
                }
            }
        }
        return decks
    }

    fun getCardsToReview(deckId: Long): List<Card> {
        val cards = mutableListOf<Card>()
        
        // 1. Get scheduled cards for the deck
        val selection = "limit=?, deckID=?"
        val selectionArgs = arrayOf("50", deckId.toString())
        val scheduleCursor: Cursor? = contentResolver.query(SCHEDULE_URI, null, selection, selectionArgs, null)
        
        scheduleCursor?.use {
            val noteIdIndex = it.getColumnIndex(NOTE_ID)
            val ordIndex = it.getColumnIndex(CARD_ORD)
            
            if (noteIdIndex != -1 && ordIndex != -1) {
                while (it.moveToNext()) {
                    val noteId = it.getLong(noteIdIndex)
                    val ord = it.getInt(ordIndex)
                    
                    // 2. Fetch details for each card
                    fetchCardDetails(noteId, ord)?.let { card ->
                        cards.add(card)
                    }
                }
            }
        }
        return cards
    }

    private fun fetchCardDetails(noteId: Long, ord: Int): Card? {
        val uri = Uri.withAppendedPath(NOTES_URI, "$noteId/cards/$ord")
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val qIndex = it.getColumnIndex(QUESTION)
                val aIndex = it.getColumnIndex(ANSWER)
                if (qIndex != -1 && aIndex != -1) {
                    val front = it.getString(qIndex) ?: ""
                    val back = it.getString(aIndex) ?: ""
                    Card(noteId, front, back, ord)
                } else null
            } else null
        }
    }

    fun answerCard(card: Card, ease: Int) {
        val values = ContentValues().apply {
            put(NOTE_ID, card.id)
            put(CARD_ORD, card.ord)
            put("answer_ease", ease)
            put("time_taken", 0) // Optional: add real time tracking if needed
        }
        contentResolver.update(SCHEDULE_URI, values, null, null)
    }
}

data class Deck(val id: Long, val name: String)
data class Card(val id: Long, val front: String, val back: String, val ord: Int)
