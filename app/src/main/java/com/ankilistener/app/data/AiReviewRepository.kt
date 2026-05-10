package com.ankilistener.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiReviewRepository(context: Context) {
    private val appContext = context.applicationContext
    private val recordsDir = File(appContext.filesDir, "ai_review_records")
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    init {
        if (!recordsDir.exists()) {
            recordsDir.mkdirs()
        }
    }

    fun saveSession(
        card: Card,
        sessionId: String,
        turns: List<AiReviewTurnRecord>
    ): File {
        val recordFile = File(recordsDir, "$sessionId.json")
        val root = if (recordFile.exists()) {
            runCatching { JSONObject(recordFile.readText()) }.getOrNull() ?: JSONObject()
        } else {
            JSONObject()
        }

        root.put("sessionId", sessionId)
        root.put("savedAt", System.currentTimeMillis())
        root.put("card", JSONObject().apply {
            put("noteId", card.id)
            put("ord", card.ord)
            put("frontHtml", card.front)
            put("backHtml", card.back)
            put("isMarked", card.isMarked)
        })
        root.put("turns", JSONArray().apply {
            turns.forEach { turn ->
                put(JSONObject().apply {
                    put("turnIndex", turn.turnIndex)
                    put("prompt", turn.prompt)
                    put("audioFilePath", turn.audioFilePath)
                    put("transcript", turn.transcript)
                    put("score", turn.score ?: JSONObject.NULL)
                    put("feedback", turn.feedback)
                    put("correction", turn.correction)
                    put("followUpQuestion", turn.followUpQuestion)
                    put("rawResponse", turn.rawResponse)
                    put("createdAt", turn.createdAt)
                })
            }
        })

        recordFile.writeText(root.toString(2))
        return recordFile
    }

    fun createSessionId(card: Card): String {
        val stamp = fileFormat.format(Date())
        return "card_${card.id}_ord_${card.ord}_$stamp"
    }
}
