package com.ankilistener.app.util

import android.text.Spanned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object HtmlUtils {

    /**
     * Removes [sound:...] tags from the text.
     */
    fun cleanMediaTags(text: String): String {
        return text.replace(Regex("\\[sound:[^\\]]+\\]"), "")
    }

    /**
     * Parses HTML text into a Spanned object.
     */
    fun parseHtml(text: String): Spanned {
        val cleaned = cleanMediaTags(text)
        return HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    /**
     * Converts a Spanned object to Compose AnnotatedString.
     */
    fun Spanned.toAnnotatedString(): AnnotatedString {
        val spanned = this
        return buildAnnotatedString {
            append(spanned.toString())
            val spans = spanned.getSpans(0, spanned.length, Any::class.java)
            spans.forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                when (span) {
                    is StyleSpan -> {
                        when (span.style) {
                            android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                            android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                            android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                        }
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                    is BackgroundColorSpan -> addStyle(SpanStyle(background = Color(span.backgroundColor)), start, end)
                    is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    is AbsoluteSizeSpan -> {
                        addStyle(SpanStyle(fontSize = span.size.sp), start, end)
                    }
                    is RelativeSizeSpan -> {
                        addStyle(SpanStyle(fontSize = span.sizeChange.em), start, end)
                    }
                }
            }
        }
    }

    /**
     * Extracts plain text for TTS.
     */
    fun extractTtsText(text: String): String {
        return parseHtml(text).toString()
    }

    /**
     * Removes ankilistener:concepts:v1 JSON comment blocks from HTML.
     * Used for display and TTS so the raw JSON is not shown or spoken.
     */
    fun removeAnkiListenerConceptBlocks(html: String): String {
        return ConceptCardParser.stripBlocks(html)
    }

    /**
     * Extracts only the answer part (after <hr id=answer>) for TTS.
     */
    fun extractAnswerOnly(html: String): String {
        // Anki standard separator for back side
        val parts = html.split(Regex("<hr\\s+id=[\"']?answer[\"']?\\s*/?>", RegexOption.IGNORE_CASE))
        val answerPart = if (parts.size > 1) parts.last() else html
        return extractTtsText(answerPart)
    }
}
