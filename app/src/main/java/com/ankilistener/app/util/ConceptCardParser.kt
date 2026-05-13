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
        AppLogger.d(TAG, "parse() called: noteId=$noteId, ord=$ord, html length=${html.length}")
        AppLogger.d(TAG, "parse() raw html (first 500): ${html.take(500)}")
        AppLogger.d(TAG, "parse() raw html (last 500): ${html.takeLast(500)}")
        AppLogger.d(TAG, "parse() contains '<!--': ${html.contains("<!--")}")
        AppLogger.d(TAG, "parse() contains '\"items\"': ${html.contains("\"items\"")}")

        val jsonStr = findConceptJson(html)
        if (jsonStr == null) {
            AppLogger.w(TAG, "parse() findConceptJson returned null - no concept block found")
            return emptyList()
        }
        AppLogger.d(TAG, "parse() found JSON (first 300): ${jsonStr.take(300)}")

        return try {
            val root = JSONObject(jsonStr)
            val items = root.getJSONArray("items")
            AppLogger.d(TAG, "parse() items array length=${items.length()}")
            val result = parseItems(items, noteId, ord)
            AppLogger.i(TAG, "parse() successfully parsed ${result.size} concepts")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse concepts JSON", e)
            emptyList()
        }
    }

    fun parseFollowUps(html: String, noteId: Long, ord: Int): List<FollowUpCard> {
        AppLogger.d(TAG, "parseFollowUps() called: noteId=$noteId, ord=$ord")

        val jsonStr = findConceptJson(html)
        if (jsonStr == null) {
            AppLogger.d(TAG, "parseFollowUps: no concept block found")
            return emptyList()
        }

        return try {
            val root = JSONObject(jsonStr)
            if (!root.has("追问")) {
                AppLogger.d(TAG, "parseFollowUps: no '追问' key in JSON")
                return emptyList()
            }
            val arr = root.getJSONArray("追问")
            AppLogger.d(TAG, "parseFollowUps: 追问 array length=${arr.length()}")
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
                val id = obj.getString("id")
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
                AppLogger.i(TAG, "parseFollowUpItems[$i]: added follow-up id=$id")
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
        AppLogger.d(TAG, "findConceptJson: found ${allMatches.size} HTML comment(s)")
        for ((i, match) in allMatches.withIndex()) {
            val inner = match.groupValues[1]
            AppLogger.d(TAG, "findConceptJson: comment[$i] length=${inner.length}, preview: ${inner.trim().take(100)}")
            val json = extractConceptJson(inner)
            if (json != null) {
                AppLogger.d(TAG, "findConceptJson: comment[$i] matched as concept JSON")
                return json
            }
            AppLogger.d(TAG, "findConceptJson: comment[$i] not a concept block")
        }
        AppLogger.w(TAG, "findConceptJson: none of ${allMatches.size} comments contained concept JSON")
        return null
    }

    private fun extractConceptJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        AppLogger.d(TAG, "extractConceptJson: '{' at $start, '}' at $end, raw length=${raw.length}")
        if (start < 0 || end <= start) return null
        val json = raw.substring(start, end + 1)
        val hasItems = json.contains("\"items\"")
        val hasFollowUps = json.contains("\"追问\"")
        AppLogger.d(TAG, "extractConceptJson: substring length=${json.length}, contains items=$hasItems, contains 追问=$hasFollowUps")
        return if (hasItems || hasFollowUps) json else null
    }

    private fun parseItems(items: JSONArray, noteId: Long, ord: Int): List<ConceptCard> {
        val result = mutableListOf<ConceptCard>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until items.length()) {
            try {
                val obj = items.getJSONObject(i)
                AppLogger.d(TAG, "parseItems[$i]: keys=${obj.keys().asSequence().toList()}")
                val id = obj.getString("id")
                if (id in seenIds) {
                    AppLogger.w(TAG, "Duplicate concept id '$id', skipping")
                    continue
                }
                seenIds.add(id)
                val title = obj.optString("title", "")
                val (question, answer) = extractQuestionAnswer(obj)
                AppLogger.d(TAG, "parseItems[$i]: id=$id, title=$title, q.len=${question.length}, a.len=${answer.length}")
                AppLogger.d(TAG, "parseItems[$i]: question=${question.take(80)}")
                AppLogger.d(TAG, "parseItems[$i]: answer=${answer.take(80)}")
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
                AppLogger.i(TAG, "parseItems[$i]: added concept id=$id, title=$title")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to parse concept item at index $i", e)
            }
        }
        return result
    }

    private fun extractQuestionAnswer(obj: JSONObject): Pair<String, String> {
        val hasQ = obj.has("q")
        val hasA = obj.has("a")
        AppLogger.d(TAG, "extractQA: hasQ=$hasQ, hasA=$hasA")
        if (hasQ || hasA) {
            val q = obj.optString("q", "")
            val a = obj.optString("a", "")
            AppLogger.d(TAG, "extractQA: using legacy q/a format")
            return q to a
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "title") continue
            val value = obj.optString(key, "")
            AppLogger.d(TAG, "extractQA: using dynamic key, key='${key.take(60)}', value.len=${value.length}")
            return key to value
        }
        AppLogger.w(TAG, "extractQA: no question/answer found")
        return "" to ""
    }
}
