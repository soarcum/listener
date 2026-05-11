package com.ankilistener.app.util

import org.junit.Assert.*
import org.junit.Test

class HtmlUtilsConceptBlockTest {

    @Test
    fun `removes concept block from HTML`() {
        val html = """
            <p>Answer text</p>
            <!--
            { "items": [{ "id": "x", "title": "X", "Q?": "A." }] }
            -->
        """.trimIndent()

        val cleaned = HtmlUtils.removeAnkiListenerConceptBlocks(html)
        assertFalse(cleaned.contains("\"items\""))
        assertTrue(cleaned.contains("Answer text"))
    }

    @Test
    fun `no concept block returns unchanged`() {
        val html = "<p>Normal answer</p>"
        val cleaned = HtmlUtils.removeAnkiListenerConceptBlocks(html)
        assertEquals(html, cleaned)
    }
}
