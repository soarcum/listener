package com.ankilistener.app.util

import com.ankilistener.app.data.ConceptCard
import org.json.JSONArray
import org.json.JSONObject

object ConceptCardParser {

    private val COMMENT_REGEX: Regex? by lazy {
        runCatching {
            Regex("<!--([\\s\\S]*?)-->")
        }.onFailure { AppLogger.e("ConceptParser", "COMMENT_REGEX compile failed", it) }.getOrNull()
    }

    fun stripBlocks(html: String): String {
        val regex = COMMENT_REGEX ?: return html
        return regex.replace(html) { m ->
            if (extractConceptJson(m.groupValues[1]) != null) "" else m.value
        }
    }

    fun parse(html: String, noteId: Long, ord: Int): List<ConceptCard> {
        val jsonStr = findConceptJson(html) ?: return emptyList()
        return try {
            val root = JSONObject(jsonStr)
            val items = root.getJSONArray("items")
            parseItems(items, noteId, ord)
        } catch (e: Exception) {
            AppLogger.e("ConceptParser", "Failed to parse concepts JSON", e)
            emptyList()
        }
    }

    private fun findConceptJson(html: String): String? {
        val regex = COMMENT_REGEX ?: return null
        for (match in regex.findAll(html)) {
            val json = extractConceptJson(match.groupValues[1])
            if (json != null) return json
        }
        return null
    }

    // Pull a JSON object containing "items" out of a comment's inner text.
    // Tolerant of leading labels (e.g. legacy "ankilistener:concepts:v1") and surrounding whitespace.
    private fun extractConceptJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = raw.substring(start, end + 1)
        return if (json.contains("\"items\"")) json else null
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
                val (question, answer) = extractQuestionAnswer(obj)
                if (answer.isBlank()) {
                    AppLogger.w("ConceptParser", "Concept '$id' has empty answer, skipping")
                    continue
                }
                val finalQuestion = question.ifBlank { "解释一下：$title" }
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

    // New format: question is the dynamic key whose value is the answer.
    // Legacy format: explicit "q"/"a" keys. Prefer legacy when present.
    private fun extractQuestionAnswer(obj: JSONObject): Pair<String, String> {
        if (obj.has("q") || obj.has("a")) {
            return obj.optString("q", "") to obj.optString("a", "")
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "title") continue
            return key to obj.optString(key, "")
        }
        return "" to ""
    }
}
