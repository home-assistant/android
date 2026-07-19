package io.homeassistant.companion.android.frontend

import android.util.DisplayMetrics
import android.webkit.ValueCallback
import android.webkit.WebView
import io.homeassistant.companion.android.frontend.WebViewAction.ApplySafeAreaInsets.Companion.SafeAreaInsets
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

    @Test
    fun `Given ApplyZoom with pinchToZoomEnabled true when run then supportZoom and builtInZoomControls are enabled and viewport allows scaling`() = runTest {
        every { webView.resources.displayMetrics } returns DisplayMetrics().apply { density = 1.0f }
        val scriptSlot = slot<String>()
        every { webView.evaluateJavascript(capture(scriptSlot), any()) } just Runs
        val action = WebViewAction.ApplyZoom(zoomLevel = 100, pinchToZoomEnabled = true)

        action.run(webView)

        verify { webView.settings.setSupportZoom(true) }
        verify { webView.settings.builtInZoomControls = true }
        assertTrue(scriptSlot.captured.contains("let overrideZoom = true;"))
    }

    @Test
    fun `Given ApplyZoom with pinchToZoomEnabled false when run then supportZoom and builtInZoomControls are disabled and viewport is restored`() = runTest {
        every { webView.resources.displayMetrics } returns DisplayMetrics().apply { density = 1.0f }
        val scriptSlot = slot<String>()
        every { webView.evaluateJavascript(capture(scriptSlot), any()) } just Runs
        val action = WebViewAction.ApplyZoom(zoomLevel = 100, pinchToZoomEnabled = false)

        action.run(webView)

        verify { webView.settings.setSupportZoom(false) }
        verify { webView.settings.builtInZoomControls = false }
        assertTrue(scriptSlot.captured.contains("let overrideZoom = false;"))
    }
}
