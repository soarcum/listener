package com.ankilistener.app.data

data class ConceptCard(
    val id: String,
    val title: String,
    val question: String,
    val answer: String,
    val sourceNoteId: Long,
    val sourceOrd: Int
)
