package com.ankilistener.app.data

import android.util.Base64
import com.ankilistener.app.util.HtmlUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiAnswerApiClient {

    fun evaluateAnswer(
        settings: AiSettings,
        card: Card,
        prompt: String,
        audioFile: File,
        turnHistory: List<AiReviewTurnRecord>
    ): AiAnswerResult {
        val endpoint = settings.endpoint.trim()
        require(endpoint.isNotBlank()) { "AI 接口地址不能为空" }

        val request = JSONObject().apply {
            put("model", settings.model)
            put("prompt", prompt)
            put("followUpEnabled", settings.followUpEnabled)
            put("card", JSONObject().apply {
                put("noteId", card.id)
                put("ord", card.ord)
                put("frontHtml", card.front)
                put("backHtml", card.back)
                put("frontText", HtmlUtils.extractTtsText(card.front))
                put("backText", HtmlUtils.extractTtsText(card.back))
                put("answerOnlyText", HtmlUtils.extractAnswerOnly(card.back))
                put("isMarked", card.isMarked)
            })
            put("audio", JSONObject().apply {
                put("fileName", audioFile.name)
                put("mimeType", "audio/mp4")
                put("base64", Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP))
            })
            put("turnHistory", JSONArray().apply {
                turnHistory.forEach { turn ->
                    put(JSONObject().apply {
                        put("turnIndex", turn.turnIndex)
                        put("prompt", turn.prompt)
                        put("transcript", turn.transcript)
                        put("score", turn.score ?: JSONObject.NULL)
                        put("feedback", turn.feedback)
                        put("correction", turn.correction)
                        put("followUpQuestion", turn.followUpQuestion)
                        put("createdAt", turn.createdAt)
                    })
                }
            })
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (settings.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            }
        }

        try {
            connection.outputStream.use { output ->
                output.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException("AI API HTTP $responseCode: ${responseBody.take(400)}")
            }

            return parseResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): AiAnswerResult {
        if (body.isBlank()) {
            return AiAnswerResult(rawResponse = body)
        }

        return try {
            val json = JSONObject(body)
            AiAnswerResult(
                transcript = json.optString("transcript").ifBlank {
                    json.optString("text").ifBlank { json.optString("answerText") }
                },
                score = json.optIntOrNull("score"),
                feedback = json.optString("feedback").ifBlank { json.optString("comment") },
                correction = json.optString("correction").ifBlank { json.optString("suggestion") },
                followUpQuestion = json.optString("followUpQuestion").ifBlank {
                    json.optString("follow_up_question").ifBlank {
                        json.optString("followUp").ifBlank { json.optString("question") }
                    }
                },
                rawResponse = body
            )
        } catch (_: Exception) {
            AiAnswerResult(
                transcript = "",
                feedback = body,
                rawResponse = body
            )
        }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (!has(name) || isNull(name)) null else optInt(name)
    }
}
