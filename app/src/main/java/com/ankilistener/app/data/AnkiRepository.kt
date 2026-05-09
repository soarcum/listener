package com.ankilistener.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.ichi2.anki.api.AddContentApi
import com.ankilistener.app.util.AppLogger

class AnkiRepository(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "AnkiRepo"
        private const val AUTHORITY = "com.ichi2.anki.flashcards"
        val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")
        val DECKS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")
        val SCHEDULE_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
        val NOTES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
        
        // Column Names (matching official FlashCardsContract)
        const val DECK_ID = "deck_id"
        const val DECK_NAME = "deck_name"
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        const val TAGS = "tags"
        
        // ReviewInfo write-only columns (official names)
        const val EASE_COLUMN = "answer_ease"    // NOT "ease"!
        const val TIME_TAKEN_COLUMN = "time_taken"
        const val BURY_COLUMN = "buried"         // NOT "action"!
        const val SUSPEND_COLUMN = "suspended"
        
        // Ease values (matching com.ichi2.anki.api.Ease)
        const val EASE_AGAIN = 1
        const val EASE_HARD = 2
        const val EASE_GOOD = 3
        const val EASE_EASY = 4
    }

    fun isApiAvailable(): Boolean {
        return AddContentApi.getAnkiDroidPackageName(context) != null
    }

    fun getDeckList(): List<Deck> {
        AppLogger.i(TAG, "getDeckList() started")
        val decks = mutableListOf<Deck>()
        try {
            val cursor: Cursor? = contentResolver.query(DECKS_URI, null, null, null, null)
            AppLogger.d(TAG, "getDeckList() cursor: $cursor, count=${cursor?.count}")
            cursor?.use {
                val idIndex = it.getColumnIndex(DECK_ID)
                val nameIndex = it.getColumnIndex(DECK_NAME)
                
                if (idIndex != -1 && nameIndex != -1) {
                    while (it.moveToNext()) {
                        val id = it.getLong(idIndex)
                        val name = it.getString(nameIndex)
                        decks.add(Deck(id, name))
                    }
                } else {
                    AppLogger.w(TAG, "getDeckList() column not found: idIndex=$idIndex, nameIndex=$nameIndex")
                    // Log available columns
                    it.columnNames?.let { cols ->
                        AppLogger.d(TAG, "Available columns: ${cols.joinToString()}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDeckList() failed", e)
        }
        AppLogger.i(TAG, "getDeckList() returning ${decks.size} decks")
        return decks
    }

    fun getCardsToReview(deckId: Long): List<Card> {
        AppLogger.i(TAG, "getCardsToReview(deckId=$deckId) started")
        val cards = mutableListOf<Card>()
        
        try {
            // Get scheduled cards for the deck
            val selection = "limit=?, deckID=?"
            val selectionArgs = arrayOf("50", deckId.toString())
            AppLogger.d(TAG, "Querying SCHEDULE_URI=$SCHEDULE_URI with selection=$selection, args=[50, $deckId]")
            val scheduleCursor: Cursor? = contentResolver.query(SCHEDULE_URI, null, selection, selectionArgs, null)
            
            scheduleCursor?.use {
                val noteIdIndex = it.getColumnIndex(NOTE_ID)
                val ordIndex = it.getColumnIndex(CARD_ORD)
                
                AppLogger.d(TAG, "Schedule cursor: count=${it.count}, noteIdIndex=$noteIdIndex, ordIndex=$ordIndex")
                // Log available columns for debugging
                it.columnNames?.let { cols ->
                    AppLogger.d(TAG, "Schedule columns: ${cols.joinToString()}")
                }
                
                if (noteIdIndex != -1 && ordIndex != -1) {
                    while (it.moveToNext()) {
                        val noteId = it.getLong(noteIdIndex)
                        val ord = it.getInt(ordIndex)
                        AppLogger.d(TAG, "Scheduled card: noteId=$noteId, ord=$ord")
                        
                        // Fetch details for each card
                        fetchCardDetails(noteId, ord)?.let { card ->
                            cards.add(card)
                        }
                    }
                } else {
                    AppLogger.e(TAG, "Schedule cursor missing expected columns!")
                }
            } ?: run {
                AppLogger.w(TAG, "Schedule cursor is null - AnkiDroid may not be running")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCardsToReview() failed", e)
        }
        AppLogger.i(TAG, "getCardsToReview() returning ${cards.size} cards")
        return cards
    }

    private fun fetchCardDetails(noteId: Long, ord: Int): Card? {
        val uri = Uri.withAppendedPath(NOTES_URI, "$noteId/cards/$ord")
        try {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            return cursor?.use {
                if (it.moveToFirst()) {
                    val qIndex = it.getColumnIndex(QUESTION)
                    val aIndex = it.getColumnIndex(ANSWER)
                    val tIndex = it.getColumnIndex(TAGS)
                    if (qIndex != -1 && aIndex != -1) {
                        val front = it.getString(qIndex) ?: ""
                        val back = it.getString(aIndex) ?: ""
                        val tags = if (tIndex != -1) it.getString(tIndex) ?: "" else ""
                        val isMarked = tags.split("\\s+".toRegex()).any { tag -> tag.equals("marked", ignoreCase = true) }
                        Card(noteId, front, back, ord, isMarked)
                    } else {
                        AppLogger.w(TAG, "fetchCardDetails: column not found qIndex=$qIndex, aIndex=$aIndex")
                        null
                    }
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchCardDetails($noteId, $ord) failed", e)
            return null
        }
    }

    /**
     * Answer a card using the official FlashCardsContract.ReviewInfo API.
     * 
     * Per the official docs, answering requires putting note_id, ord, answer_ease, 
     * and time_taken INTO ContentValues (not as selection args), and calling 
     * update on the schedule URI with null selection.
     */
    fun answerCard(card: Card, ease: Int, deckId: Long? = null) {
        AppLogger.i(TAG, "answerCard: noteId=${card.id}, ord=${card.ord}, ease=$ease")
        try {
            val values = ContentValues().apply {
                put(NOTE_ID, card.id)
                put(CARD_ORD, card.ord)
                put(EASE_COLUMN, ease)       // "answer_ease", NOT "ease"
                put(TIME_TAKEN_COLUMN, 5000L) // time_taken in ms
            }
            AppLogger.d(TAG, "answerCard ContentValues: $values")
            val rows = contentResolver.update(SCHEDULE_URI, values, null, null)
            AppLogger.i(TAG, "answerCard result: rowsAffected=$rows (noteId=${card.id}, ease=$ease)")
            if (rows == 0) {
                AppLogger.w(TAG, "answerCard: WARNING - 0 rows updated! Card may not be in review queue.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "answerCard CRASHED", e)
        }
    }

    /**
     * Bury a card using the official FlashCardsContract.ReviewInfo API.
     * 
     * Per the official docs, burying requires putting note_id, ord, and buried=1
     * INTO ContentValues. Do NOT set ease/time_taken when burying.
     */
    fun buryCard(card: Card, deckId: Long? = null) {
        AppLogger.i(TAG, "buryCard: noteId=${card.id}, ord=${card.ord}")
        try {
            val values = ContentValues().apply {
                put(NOTE_ID, card.id)
                put(CARD_ORD, card.ord)
                put(BURY_COLUMN, 1)  // "buried" = 1, NOT "action" = "bury"
            }
            AppLogger.d(TAG, "buryCard ContentValues: $values")
            val rows = contentResolver.update(SCHEDULE_URI, values, null, null)
            AppLogger.i(TAG, "buryCard result: rowsAffected=$rows")
        } catch (e: Exception) {
            AppLogger.e(TAG, "buryCard CRASHED", e)
        }
    }

    /**
     * Mark a note by adding the "marked" tag via AnkiDroid's ContentProvider.
     *
     * AnkiDroid requires updating notes through data URI (notes/{noteId}),
     * not via selection args. Anki's standard "mark" is the "marked" tag.
     *
     * @return The new mark status (true if marked, false if unmarked)
     */
    fun markCard(card: Card): Boolean {
        AppLogger.i(TAG, "markCard: noteId=${card.id}")
        var isMarkedAfter = false
        try {
            val noteUri = Uri.withAppendedPath(NOTES_URI, card.id.toString())

            // 1. Read current tags
            val cursor = contentResolver.query(noteUri, arrayOf(TAGS), null, null, null)
            var currentTags = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val tagsIndex = it.getColumnIndex(TAGS)
                    if (tagsIndex != -1) {
                        currentTags = it.getString(tagsIndex) ?: ""
                    }
                }
            }
            AppLogger.d(TAG, "markCard: currentTags='$currentTags'")

            // 2. Toggle: add "marked" if absent, remove if present
            val tagList = currentTags.split("\\s+".toRegex()).filter { it.isNotBlank() }.toMutableList()
            val alreadyMarked = tagList.any { it.equals("marked", ignoreCase = true) }

            if (alreadyMarked) {
                tagList.removeAll { it.equals("marked", ignoreCase = true) }
                AppLogger.i(TAG, "markCard: removing 'marked' tag")
                isMarkedAfter = false
            } else {
                tagList.add("marked")
                AppLogger.i(TAG, "markCard: adding 'marked' tag")
                isMarkedAfter = true
            }

            val newTags = tagList.joinToString(" ")
            val values = ContentValues().apply {
                put(TAGS, newTags)
            }

            // 3. Update via note-specific data URI
            val rows = contentResolver.update(noteUri, values, null, null)
            AppLogger.i(TAG, "markCard result: rowsAffected=$rows, newTags='$newTags', wasMarked=$alreadyMarked")
        } catch (e: Exception) {
            AppLogger.e(TAG, "markCard CRASHED", e)
        }
        return isMarkedAfter
    }

    /**
     * Note: AnkiDroid's ContentProvider API does NOT support undo.
     * This is a no-op that logs a warning.
     */
    fun undoReview() {
        AppLogger.w(TAG, "undoReview: AnkiDroid ContentProvider API does NOT support undo operations")
    }
}

data class Deck(val id: Long, val name: String)
data class Card(val id: Long, val front: String, val back: String, val ord: Int, val isMarked: Boolean = false)
