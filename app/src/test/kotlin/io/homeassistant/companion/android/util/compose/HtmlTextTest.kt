package io.homeassistant.companion.android.util.compose

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HtmlTextTest {

    @Test
    fun `Given plain text when parsing html then it is returned unstyled`() {
        val result = parseHtml("hello world")

        assertEquals("hello world", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `Given an empty string when parsing html then an empty AnnotatedString is returned`() {
        val result = parseHtml("")

        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `Given bold html when parsing then a bold span is applied to the range`() {
        val result = parseHtml("<b>hello</b>")

        assertEquals("hello", result.text)
        val span = result.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun `Given italic html when parsing then an italic span is applied to the range`() {
        val result = parseHtml("<i>hello</i>")

        assertEquals("hello", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
    }

    @Test
    fun `Given nested bold and italic html when parsing then both styles are applied`() {
        val result = parseHtml("<b><i>hello</i></b>")

        assertEquals("hello", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `Given underline html when parsing then an underline span is applied`() {
        val result = parseHtml("<u>hello</u>")

        assertEquals("hello", result.text)
        val span = result.spanStyles.single()
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    @Test
    fun `Given a font color html when parsing then a matching color span is applied`() {
        val result = parseHtml("<font color=\"#ff0000\">hello</font>")

        assertEquals("hello", result.text)
        val span = result.spanStyles.single()
        assertEquals(Color(AndroidColor.parseColor("#ff0000")), span.item.color)
    }

    @Test
    fun `Given a big tag when parsing then a larger relative font size span is applied`() {
        val result = parseHtml("<big>hello</big>")

        val span = result.spanStyles.single()
        assertEquals((1.25f * 12).sp, span.item.fontSize)
    }

    @Test
    fun `Given a small tag when parsing then a smaller relative font size span is applied`() {
        val result = parseHtml("<small>hello</small>")

        val span = result.spanStyles.single()
        assertEquals((0.8f * 12).sp, span.item.fontSize)
    }

    @Test
    fun `Given a real line break when parsing then it is preserved as a line break`() {
        val result = parseHtml("line1\nline2")

        assertEquals("line1\nline2", result.text)
    }

    @Test
    fun `Given a literal escaped line break when parsing then it is rendered as a line break`() {
        val result = parseHtml("line1\\nline2")

        assertEquals("line1\nline2", result.text)
    }
}
