package io.homeassistant.companion.android.frontend

import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.frontend.WebViewAction.ReadThemeColors.Companion.ThemeColors
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests [WebViewAction.ReadThemeColors]. Runs with Robolectric because hex and named colors are
 * parsed with `android.graphics.Color.parseColor`, which is stubbed to always return 0 in plain
 * JVM unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebViewActionThemeColorsTest {

    private val webView: WebView = mockk(relaxed = true)

    @Test
    fun `Given ReadThemeColors when run then evaluateJavascript reads the theme tokens`() = runTest {
        val callbackSlot = slot<ValueCallback<String>>()
        every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
        val action = WebViewAction.ReadThemeColors()

        action.run(webView)

        verify {
            webView.evaluateJavascript(
                match { it.contains("--app-header-background-color") && it.contains("--primary-background-color") },
                any(),
            )
        }
    }

    @Test
    fun `Given a null result when reading theme colors then null is returned`() = runTest {
        assertNull(readThemeColors(null))
    }

    @Test
    fun `Given a result without exactly two tokens when reading theme colors then null is returned`() = runTest {
        assertNull(readThemeColors("\"rgb(1, 2, 3)\""))
        assertNull(readThemeColors("\"a-SPACER-b-SPACER-c\""))
    }

    @Test
    fun `Given null tokens when reading theme colors then both colors are null`() = runTest {
        assertEquals(ThemeColors(statusBarColor = null, backgroundColor = null), readThemeColors("\"null-SPACER-null\""))
    }

    @Test
    fun `Given rgb tokens when reading theme colors then the matching colors are returned`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = Color(18, 52, 86), backgroundColor = Color(4, 5, 6)),
            readThemeColors("\"rgb(18, 52, 86)-SPACER-rgb(4, 5, 6)\""),
        )
    }

    @Test
    fun `Given hex tokens when reading theme colors then the matching colors are returned`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = Color(0x12, 0x34, 0x56), backgroundColor = Color(0xAB, 0xCD, 0xEF)),
            readThemeColors("\"#123456-SPACER-#ABCDEF\""),
        )
    }

    @Test
    fun `Given a hex token with alpha when reading theme colors then the alpha is ignored`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = Color(0x12, 0x34, 0x56), backgroundColor = Color(0x12, 0x34, 0x56)),
            readThemeColors("\"#80123456-SPACER-#00123456\""),
        )
    }

    @Test
    fun `Given color name tokens when reading theme colors then the matching colors are returned`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = Color(255, 0, 0), backgroundColor = Color(255, 0, 255)),
            readThemeColors("\"red-SPACER-fuchsia\""),
        )
    }

    @Test
    fun `Given tokens with surrounding whitespace when reading theme colors then the values are trimmed`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = Color(0x12, 0x34, 0x56), backgroundColor = Color(1, 2, 3)),
            readThemeColors("\"  #123456  -SPACER-  rgb(1, 2, 3)  \""),
        )
    }

    @Test
    fun `Given unparseable or out-of-range tokens when reading theme colors then the colors are null`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = null, backgroundColor = null),
            readThemeColors("\"not-a-color-SPACER-rgb(300, 0, 0)\""),
        )
    }

    @Test
    fun `Given a short hex token when reading theme colors then the color is null`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = null, backgroundColor = Color(0, 0, 0)),
            readThemeColors("\"#123-SPACER-#000000\""),
        )
    }

    /**
     * Runs a [WebViewAction.ReadThemeColors] and feeds [raw] back as the WebView's script result,
     * returning the parsed [ThemeColors] the action completes with.
     */
    private suspend fun readThemeColors(raw: String?): ThemeColors? {
        val callbackSlot = slot<ValueCallback<String>>()
        every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
        val action = WebViewAction.ReadThemeColors()

        action.run(webView)
        callbackSlot.captured.onReceiveValue(raw)

        return action.result.await()
    }
}
