package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Tests for all possible spans supported by [parseHtml] with:
 * - no markup (verify nothing changes)
 * - each markup separate
 * - markup combined
 */
class HtmlTextScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with no special markup is unchanged`() {
        val text = "This is text without any special markup"
        val annotated = parseHtml(text)
        // Test both the string before and after parsing; they should be shown identically
        Column {
            Text(text)
            Text(annotated)
        }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with bold text`() {
        val annotated = parseHtml("This is <b>bold</b> and <strong>strong</strong> text")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with italic text`() {
        val annotated = parseHtml("This is <i>italic</i> and <em>emphasized</em> text")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with underlined text`() {
        val annotated = parseHtml("This is <u>underlined</u> text")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with relative size text`() {
        // Note the big text size is only very slightly bigger, compare a's
        val annotated = parseHtml("This is a <big>large</big> and <small>small</small> text")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with colored text`() {
        val annotated = parseHtml("This is <font color='#03a9f4'>HA blue</font> and <font color='red'>red</font> text")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with line breaks`() {
        val annotated = parseHtml("This is<br>text\nwith\r\nline\rbreaks\\nin\\r\\none\\rstring")
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with combined markup 1`() {
        val annotated =
            parseHtml(
                "This <b>is <i>an</i> example</b><br><big>Of HTML</big><br>Of HTML<br><span style='color:#03a9f4'><small>And markups</small></span>",
            )
        Text(annotated)
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun `Parsed text with combined markup 2`() {
        val annotated =
            parseHtml(
                "<u>Aquarium status<br></u>The temperature is <i><font color=\"#4CAF50\">22.5°C</font> (± 0.5°C)</i>\nHumidity: <b>45% <small>(-6%)</small></b>",
            )
        Text(annotated)
    }
}
