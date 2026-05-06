package com.ankilistener.app.util

import android.text.Spanned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color

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
}
