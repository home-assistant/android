package io.homeassistant.companion.android.frontend

import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.frontend.WebViewAction.ApplySafeAreaInsets.Companion.SafeAreaInsets
import io.homeassistant.companion.android.frontend.WebViewAction.ReadThemeColors.Companion.ThemeColors
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.haptic.HapticFeedbackPerformer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(EvaluateJavascriptUsage::class)
class WebViewActionTest {

    private val webView: WebView = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(HapticFeedbackPerformer)
        every { HapticFeedbackPerformer.perform(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(HapticFeedbackPerformer)
    }

    @Test
    fun `Given Forward when run and canGoForward is true then goForward is called and result completes`() = runTest {
        every { webView.canGoForward() } returns true
        val action = WebViewAction.Forward()

        action.run(webView)

        verify { webView.goForward() }
        assertTrue(action.result.isCompleted)
    }

    @Test
    fun `Given Forward when run and canGoForward is false then goForward is not called but result completes`() = runTest {
        every { webView.canGoForward() } returns false
        val action = WebViewAction.Forward()

        action.run(webView)

        verify(exactly = 0) { webView.goForward() }
        assertTrue(action.result.isCompleted)
    }

    @Test
    fun `Given Reload when run then reload is called and result completes`() = runTest {
        val action = WebViewAction.Reload()

        action.run(webView)

        verify { webView.reload() }
        assertTrue(action.result.isCompleted)
    }

    @Test
    fun `Given Haptic when run then HapticFeedbackPerformer is invoked with the type and result completes`() = runTest {
        val action = WebViewAction.Haptic(HapticType.Success)

        action.run(webView)

        verify { HapticFeedbackPerformer.perform(webView, HapticType.Success) }
        assertTrue(action.result.isCompleted)
    }

    @Test
    fun `Given ClearHistory when run then clearHistory is called and result completes`() = runTest {
        val action = WebViewAction.ClearHistory()

        action.run(webView)

        verify { webView.clearHistory() }
        assertTrue(action.result.isCompleted)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Given NavigateToDefaultPanelViaSidebar when run then evaluateJavascript is called and result completes`() = runTest {
        val callbackSlot = slot<ValueCallback<String>>()
        every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
        val action = WebViewAction.NavigateToDefaultPanelViaSidebar()

        action.run(webView)

        verify {
            webView.evaluateJavascript(
                match { it.contains("defaultPanel") && it.contains("ha-sidebar") },
                any(),
            )
        }
        callbackSlot.captured.onReceiveValue(null)
        assertTrue(action.result.isCompleted)
    }

    @Test
    fun `Given EvaluateScript when run then evaluateJavascript is called and result completes with callback value`() = runTest {
        val callbackSlot = slot<ValueCallback<String>>()
        every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
        val action = WebViewAction.EvaluateScript(script = "doThing()")

        action.run(webView)

        verify { webView.evaluateJavascript("doThing()", any()) }
        // Simulate the WebView invoking the callback with a result
        callbackSlot.captured.onReceiveValue("\"ok\"")
        assertTrue(action.result.isCompleted)
        assertEquals("\"ok\"", action.result.await())
    }

    @Test
    fun `Given EvaluateScript when run and callback returns null then result completes with null`() = runTest {
        val callbackSlot = slot<ValueCallback<String>>()
        every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
        val action = WebViewAction.EvaluateScript(script = "void(0)")

        action.run(webView)

        callbackSlot.captured.onReceiveValue(null)
        assertEquals(null, action.result.await())
    }

    @Test
    fun `Given OpenMoreInfo when run then it dispatches hass-more-info for the entity`() = runTest {
        val scriptSlot = slot<String>()
        every { webView.evaluateJavascript(capture(scriptSlot), any()) } just Runs

        WebViewAction.OpenMoreInfo("light.kitchen").run(webView)

        val script = scriptSlot.captured
        assertTrue(script.contains("hass-more-info"))
        assertTrue(script.contains("\"light.kitchen\""))
    }

    @Test
    fun `Given OpenMoreInfo with a hostile entity id when run then the value is JSON-escaped and cannot break out`() = runTest {
        val scriptSlot = slot<String>()
        every { webView.evaluateJavascript(capture(scriptSlot), any()) } just Runs

        // Crafted to close the string/object and inject code if interpolated raw.
        WebViewAction.OpenMoreInfo("""x"}});alert(1);//""").run(webView)

        val script = scriptSlot.captured
        // The embedded double quote must be backslash-escaped (proof of JSON encoding), so the
        // payload stays inside the entityId string literal instead of becoming executable code.
        assertTrue(script.contains("\\\""))
    }

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
    fun `Given unparseable or out-of-range tokens when reading theme colors then the colors are null`() = runTest {
        assertEquals(
            ThemeColors(statusBarColor = null, backgroundColor = null),
            readThemeColors("\"not-a-color-SPACER-rgb(300, 0, 0)\""),
        )
    }

    @Test
    fun `Given ApplySafeAreaInsets when run then the safe area CSS properties are set`() = runTest {
        val action = WebViewAction.ApplySafeAreaInsets(SafeAreaInsets(top = 10f, bottom = 20f, left = 5f, right = 8f))

        action.run(webView)

        verify {
            webView.evaluateJavascript(
                match {
                    it.contains("--app-safe-area-inset-top', '10.0px'") &&
                        it.contains("--app-safe-area-inset-bottom', '20.0px'") &&
                        it.contains("--app-safe-area-inset-left', '5.0px'") &&
                        it.contains("--app-safe-area-inset-right', '8.0px'")
                },
                null,
            )
        }
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
