package io.homeassistant.companion.android.frontend

import android.webkit.ValueCallback
import android.webkit.WebView
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(EvaluateScriptUsage::class)
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
}
