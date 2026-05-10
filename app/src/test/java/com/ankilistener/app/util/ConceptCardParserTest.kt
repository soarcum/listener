package com.ankilistener.app.util

import org.junit.Assert.*
import org.junit.Test

class ConceptCardParserTest {

    @Test
    fun `parse single concept`() {
        val html = """
            Some answer text.
            <!-- ankilistener:concepts:v1
            {
              "items": [
                {
                  "id": "state-sync-error",
                  "title": "状态同步易出错",
                  "q": "为什么状态同步易出错？",
                  "a": "因为业务数据变化后需要手动同步。"
                }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 100L, ord = 0)
        assertEquals(1, result.size)
        val concept = result[0]
        assertEquals("state-sync-error", concept.id)
        assertEquals("状态同步易出错", concept.title)
        assertEquals("为什么状态同步易出错？", concept.question)
        assertEquals("因为业务数据变化后需要手动同步。", concept.answer)
        assertEquals(100L, concept.sourceNoteId)
        assertEquals(0, concept.sourceOrd)
    }

    @Test
    fun `parse multiple concepts`() {
        val html = """
            <!-- ankilistener:concepts:v1
            {
              "items": [
                {
                  "id": "concept-a",
                  "title": "概念A",
                  "q": "问题A？",
                  "a": "答案A。"
                },
                {
                  "id": "concept-b",
                  "title": "概念B",
                  "q": "问题B？",
                  "a": "答案B。"
                }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 200L, ord = 1)
        assertEquals(2, result.size)
        assertEquals("concept-a", result[0].id)
        assertEquals("concept-b", result[1].id)
    }

    @Test
    fun `no concept block returns empty list`() {
        val html = "<p>Just regular HTML answer</p>"
        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `malformed JSON returns empty list`() {
        val html = """
            <!-- ankilistener:concepts:v1
            { broken json
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `duplicate id ignores later entry`() {
        val html = """
            <!-- ankilistener:concepts:v1
            {
              "items": [
                { "id": "dup", "title": "First", "q": "Q1?", "a": "A1." },
                { "id": "dup", "title": "Second", "q": "Q2?", "a": "A2." }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertEquals(1, result.size)
        assertEquals("First", result[0].title)
    }

    @Test
    fun `empty answer skips concept`() {
        val html = """
            <!-- ankilistener:concepts:v1
            {
              "items": [
                { "id": "no-answer", "title": "T", "q": "Q?", "a": "" },
                { "id": "has-answer", "title": "T2", "q": "Q2?", "a": "Yes." }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertEquals(1, result.size)
        assertEquals("has-answer", result[0].id)
    }

    @Test
    fun `empty question uses title as fallback`() {
        val html = """
            <!-- ankilistener:concepts:v1
            {
              "items": [
                { "id": "no-q", "title": "状态同步", "q": "", "a": "答案。" }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertEquals(1, result.size)
        assertEquals("解释一下：状态同步", result[0].question)
    }
}
