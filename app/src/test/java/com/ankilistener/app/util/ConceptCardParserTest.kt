package com.ankilistener.app.util

import org.junit.Assert.*
import org.junit.Test

class ConceptCardParserTest {

    @Test
    fun `parse new format with dynamic question key`() {
        val html = """
            Some answer text.
            <!--
            {
              "items": [
                {
                  "id": "0",
                  "title": "状态同步易出错",
                  "在 Vue 和 Three.js 混合开发中，为什么直接同步状态容易导致[[状态同步易出错]]？": "因为业务数据变化后，需要手动将 Vue 的响应式状态同步到 Three.js 对象上，这种命令式同步代码分散且容易遗漏。"
                }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 100L, ord = 0)
        assertEquals(1, result.size)
        val concept = result[0]
        assertEquals("0", concept.id)
        assertEquals("状态同步易出错", concept.title)
        assertEquals(
            "在 Vue 和 Three.js 混合开发中，为什么直接同步状态容易导致[[状态同步易出错]]？",
            concept.question
        )
        assertTrue(concept.answer.startsWith("因为业务数据变化后"))
        assertEquals(100L, concept.sourceNoteId)
        assertEquals(0, concept.sourceOrd)
    }

    @Test
    fun `parse multiple concepts in new format`() {
        val html = """
            <!--
            {
              "items": [
                { "id": "0", "title": "A", "问题A？": "答案A。" },
                { "id": "1", "title": "B", "问题B？": "答案B。" }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 200L, ord = 1)
        assertEquals(2, result.size)
        assertEquals("0", result[0].id)
        assertEquals("问题A？", result[0].question)
        assertEquals("答案A。", result[0].answer)
        assertEquals("1", result[1].id)
        assertEquals("问题B？", result[1].question)
    }

    @Test
    fun `parse legacy q-a format still works`() {
        val html = """
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
        assertEquals("为什么状态同步易出错？", concept.question)
        assertEquals("因为业务数据变化后需要手动同步。", concept.answer)
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
            <!--
            { "items": [ broken json
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unrelated html comment is ignored`() {
        val html = """
            <p>Answer</p>
            <!-- TODO: random unrelated comment -->
        """.trimIndent()
        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `duplicate id ignores later entry`() {
        val html = """
            <!--
            {
              "items": [
                { "id": "dup", "title": "First", "Q1?": "A1." },
                { "id": "dup", "title": "Second", "Q2?": "A2." }
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
            <!--
            {
              "items": [
                { "id": "no-answer", "title": "T", "Q?": "" },
                { "id": "has-answer", "title": "T2", "Q2?": "Yes." }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertEquals(1, result.size)
        assertEquals("has-answer", result[0].id)
    }

    @Test
    fun `missing question uses title as fallback`() {
        val html = """
            <!--
            {
              "items": [
                { "id": "no-q", "title": "状态同步", "a": "答案。" }
              ]
            }
            -->
        """.trimIndent()

        val result = ConceptCardParser.parse(html, noteId = 1L, ord = 0)
        assertEquals(1, result.size)
        assertEquals("解释一下：状态同步", result[0].question)
    }

    @Test
    fun `stripBlocks removes concept comment but keeps unrelated ones`() {
        val html = """
            <p>Answer</p>
            <!-- TODO: keep me -->
            <!--
            { "items": [ { "id": "0", "title": "T", "Q?": "A." } ] }
            -->
        """.trimIndent()

        val cleaned = ConceptCardParser.stripBlocks(html)
        assertTrue(cleaned.contains("TODO: keep me"))
        assertFalse(cleaned.contains("\"items\""))
    }
}
