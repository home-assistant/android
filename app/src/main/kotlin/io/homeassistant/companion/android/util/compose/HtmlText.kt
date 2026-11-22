package io.homeassistant.companion.android.util.compose

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat

private val LINE_BREAK_REGEX = "(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex()
private const val DEFAULT_RELATIVE_SIZE_SP = 12

/**
 * Converts HTML, as returned by a rendered Home Assistant template, into an [AnnotatedString] by
 * translating the [android.text.Spanned] styles produced by [HtmlCompat.fromHtml] into Compose
 * [SpanStyle]s. Shared by every screen that displays a rendered template.
 */
fun parseHtml(renderedText: String): AnnotatedString = buildAnnotatedString {
    // Replace both actual and literal (escaped) line break characters with <br>
    val renderedSpanned = HtmlCompat.fromHtml(
        renderedText.replace(LINE_BREAK_REGEX, "<br>"),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )
    append(renderedSpanned.toString())
    renderedSpanned.getSpans(0, renderedSpanned.length, CharacterStyle::class.java).forEach { span ->
        val start = renderedSpanned.getSpanStart(span)
        val end = renderedSpanned.getSpanEnd(span)
        when (span) {
            is AbsoluteSizeSpan -> addStyle(SpanStyle(fontSize = span.size.sp), start, end)
            is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            is RelativeSizeSpan -> {
                addStyle(SpanStyle(fontSize = (span.sizeChange * DEFAULT_RELATIVE_SIZE_SP).sp), start, end)
            }
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
}
