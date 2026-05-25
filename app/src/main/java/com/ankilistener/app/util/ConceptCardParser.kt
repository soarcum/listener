package com.ankilistener.app.util

import com.ankilistener.app.data.ConceptCard
import com.ankilistener.app.data.FollowUpCard
import org.json.JSONArray
import org.json.JSONObject

object ConceptCardParser {

    private const val TAG = "ConceptParser"

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
        val jsonStr = findConceptJson(html)
        if (jsonStr == null) {
            AppLogger.w(TAG, "parse() findConceptJson returned null - no concept block found")
            return emptyList()
        }

        return try {
            val root = JSONObject(jsonStr)
            val items = root.getJSONArray("items")
            parseItems(items, noteId, ord)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse concepts JSON", e)
            emptyList()
        }
    }

    fun parseFollowUps(html: String, noteId: Long, ord: Int): List<FollowUpCard> {
        val jsonStr = findConceptJson(html)
        if (jsonStr == null) {
            return emptyList()
        }

        return try {
            val root = JSONObject(jsonStr)
            val arr = when {
                root.has("追问") -> root.getJSONArray("追问")
                root.has("QA") -> root.getJSONArray("QA")
                else -> return emptyList()
            }
            parseFollowUpItems(arr, noteId, ord)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse follow-ups JSON", e)
            emptyList()
        }
    }

    private fun parseFollowUpItems(arr: JSONArray, noteId: Long, ord: Int): List<FollowUpCard> {
        val result = mutableListOf<FollowUpCard>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val id = obj.opt("id")?.toString() ?: continue
                if (id in seenIds) {
                    AppLogger.w(TAG, "Duplicate follow-up id '$id', skipping")
                    continue
                }
                seenIds.add(id)
                val (question, answer) = extractQuestionAnswer(obj)
                if (answer.isBlank()) {
                    AppLogger.w(TAG, "Follow-up '$id' has empty answer, skipping")
                    continue
                }
                if (question.isBlank()) {
                    AppLogger.w(TAG, "Follow-up '$id' has empty question, skipping")
                    continue
                }
                result.add(
                    FollowUpCard(
                        id = id,
                        question = question,
                        answer = answer,
                        sourceNoteId = noteId,
                        sourceOrd = ord
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to parse follow-up item at index $i", e)
            }
        }
        return result
    }

    private fun findConceptJson(html: String): String? {
        val regex = COMMENT_REGEX
        if (regex == null) {
            AppLogger.e(TAG, "findConceptJson: COMMENT_REGEX is null!")
            return null
        }
        val allMatches = regex.findAll(html).toList()
        for (match in allMatches) {
            val inner = match.groupValues[1]
            val json = extractConceptJson(inner)
            if (json != null) {
                return json
            }
        }
        AppLogger.w(TAG, "findConceptJson: none of ${allMatches.size} comments contained concept JSON")
        return null
    }

    private fun extractConceptJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = raw.substring(start, end + 1)
        val hasItems = json.contains("\"items\"")
        val hasFollowUps = json.contains("\"追问\"") || json.contains("\"QA\"")
        return if (hasItems || hasFollowUps) json else null
    }

    private fun parseItems(items: JSONArray, noteId: Long, ord: Int): List<ConceptCard> {
        val result = mutableListOf<ConceptCard>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until items.length()) {
            try {
                val obj = items.getJSONObject(i)
                val id = obj.opt("id")?.toString() ?: continue
                if (id in seenIds) {
                    AppLogger.w(TAG, "Duplicate concept id '$id', skipping")
                    continue
                }
                seenIds.add(id)
                val title = obj.optString("title", "")
                val (question, answer) = extractQuestionAnswer(obj)
                if (answer.isBlank()) {
                    AppLogger.w(TAG, "Concept '$id' has empty answer, skipping")
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
                AppLogger.e(TAG, "Failed to parse concept item at index $i", e)
            }
        }
        return result
    }

    private fun extractQuestionAnswer(obj: JSONObject): Pair<String, String> {
        val hasQ = obj.has("q")
        val hasA = obj.has("a")
        if (hasQ || hasA) {
            val q = obj.optString("q", "")
            val a = obj.optString("a", "")
            return q to a
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "title") continue
            val value = obj.optString(key, "")
            return key to value
        }
        AppLogger.w(TAG, "extractQA: no question/answer found")
        return "" to ""
    }
}
