package com.ankilistener.app.data

data class AiSettings(
    val enabled: Boolean = false,
    val endpoint: String = "http://172.22.64.1:3000/api/anki-listener/answer",
    val apiKey: String = "",
    val model: String = "default",
    val followUpEnabled: Boolean = true
)

data class AiAnswerResult(
    val transcript: String = "",
    val score: Int? = null,
    val feedback: String = "",
    val correction: String = "",
    val followUpQuestion: String = "",
    val rawResponse: String = ""
)

data class AiReviewTurnRecord(
    val turnIndex: Int,
    val prompt: String,
    val audioFilePath: String,
    val transcript: String,
    val score: Int?,
    val feedback: String,
    val correction: String,
    val followUpQuestion: String,
    val rawResponse: String,
    val createdAt: Long = System.currentTimeMillis()
)
