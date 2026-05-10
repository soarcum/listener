package com.ankilistener.app.util

import com.ankilistener.app.data.ConceptCard
import org.json.JSONArray
import org.json.JSONObject

object ConceptCardParser {

    private val BLOCK_REGEX = Regex(
        "<!--\\s*ankilistener:concepts:v1\\s*(\\{.*?})\\s*-->",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(html: String, noteId: Long, ord: Int): List<ConceptCard> {
        val match = BLOCK_REGEX.find(html) ?: return emptyList()
        val jsonStr = match.groupValues[1]
        return try {
            val root = JSONObject(jsonStr)
            val items = root.getJSONArray("items")
            parseItems(items, noteId, ord)
        } catch (e: Exception) {
            AppLogger.e("ConceptParser", "Failed to parse concepts JSON", e)
            emptyList()
        }
    }

    private fun parseItems(items: JSONArray, noteId: Long, ord: Int): List<ConceptCard> {
        val result = mutableListOf<ConceptCard>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until items.length()) {
            try {
                val obj = items.getJSONObject(i)
                val id = obj.getString("id")
                if (id in seenIds) {
                    AppLogger.w("ConceptParser", "Duplicate concept id '$id', skipping")
                    continue
                }
                seenIds.add(id)
                val title = obj.optString("title", "")
                val question = obj.optString("q", "")
                val answer = obj.optString("a", "")
                if (answer.isBlank()) {
                    AppLogger.w("ConceptParser", "Concept '$id' has empty answer, skipping")
                    continue
                }
                val finalQuestion = question.ifBlank {
                    "解释一下：$title"
                }
                result.add(
                    ConceptCard(
                        id = id,
                        title = title,
                        question = finalQuestion,
                        answer = answer,
                        sourceNoteId = noteId,
                        sourceOrd = ord
                    )
                )
            } catch (e: Exception) {
                AppLogger.e("ConceptParser", "Failed to parse concept item at index $i", e)
            }
        }
        return result
    }
}
