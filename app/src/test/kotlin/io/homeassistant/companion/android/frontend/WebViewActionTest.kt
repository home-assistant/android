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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val TEST_URL = "https://example.com/manifest.json"

/** [TEST_URL] as the JSON-encoded value evaluateJavascript reports for the completion variable. */
private const val TEST_URL_JSON = "\"$TEST_URL\""

@OptIn(EvaluateJavascriptUsage::class)
class WebViewActionTest {

    private val webView: WebView = mockk(relaxed = true)

    @Nested
    inner class Forward {

        @Test
        fun `Given canGoForward is true when run then goForward is called and result completes`() = runTest {
            every { webView.canGoForward() } returns true
            val action = WebViewAction.Forward()

            action.run(webView)

            verify { webView.goForward() }
            assertTrue(action.result.isCompleted)
        }

        @Test
        fun `Given canGoForward is false when run then goForward is not called but result completes`() = runTest {
            every { webView.canGoForward() } returns false
            val action = WebViewAction.Forward()

            action.run(webView)

            verify(exactly = 0) { webView.goForward() }
            assertTrue(action.result.isCompleted)
        }
    }

    @Nested
    inner class Reload {

        @Test
        fun `Given Reload when run then reload is called and result completes`() = runTest {
            val action = WebViewAction.Reload()

            action.run(webView)

            verify { webView.reload() }
            assertTrue(action.result.isCompleted)
        }
    }

    @Nested
    inner class Haptic {

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
        fun `Given Haptic when run then HapticFeedbackPerformer is invoked with the type and result completes`() = runTest {
            val action = WebViewAction.Haptic(HapticType.Success)

            action.run(webView)

            verify { HapticFeedbackPerformer.perform(webView, HapticType.Success) }
            assertTrue(action.result.isCompleted)
        }
    }

    @Nested
    inner class ClearHistory {

        @Test
        fun `Given ClearHistory when run then clearHistory is called and result completes`() = runTest {
            val action = WebViewAction.ClearHistory()

            action.run(webView)

            verify { webView.clearHistory() }
            assertTrue(action.result.isCompleted)
        }
    }

    @Nested
    inner class NavigateToDefaultPanelViaSidebar {

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
    }

    @Nested
    inner class EvaluateScript {

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
            assertEquals("\"ok\"", action.await())
        }

        @Test
        fun `Given the callback returns null when run then result completes with null`() = runTest {
            val callbackSlot = slot<ValueCallback<String>>()
            every { webView.evaluateJavascript(any(), capture(callbackSlot)) } just Runs
            val action = WebViewAction.EvaluateScript(script = "void(0)")

            action.run(webView)

            callbackSlot.captured.onReceiveValue(null)
            assertEquals(null, action.await())
        }
    }

    @Nested
    inner class OpenMoreInfo {

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
        fun `Given a hostile entity id when run then the value is JSON-escaped and cannot break out`() = runTest {
            val scriptSlot = slot<String>()
            every { webView.evaluateJavascript(capture(scriptSlot), any()) } just Runs

            // Crafted to close the string/object and inject code if interpolated raw.
            WebViewAction.OpenMoreInfo("""x"}});alert(1);//""").run(webView)

            val script = scriptSlot.captured
            // The embedded double quote must be backslash-escaped (proof of JSON encoding), so the
            // payload stays inside the entityId string literal instead of becoming executable code.
            assertTrue(script.contains("\\\""))
        }
    }

    @Nested
    inner class ApplySafeAreaInsets {

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
    }

    @Nested
    inner class ApplyZoom {

        @Test
        fun `Given pinchToZoomEnabled true when run then supportZoom and builtInZoomControls are enabled and viewport allows scaling`() = runTest {
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
        fun `Given pinchToZoomEnabled false when run then supportZoom and builtInZoomControls are disabled and viewport is restored`() = runTest {
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

    /**
     * Tests [WebViewAction.PingUrl] by capturing the scripts it evaluates and feeding results back
     * through the captured callbacks, standing in for the WebView.
     */
    @Nested
    inner class PingUrl {

        /** Callbacks of the evaluated scripts: the ping script first, then one per completion poll. */
        private val evaluateCallbacks = mutableListOf<ValueCallback<String>>()
        private val evaluatedScripts = mutableListOf<String>()
        private val delayedRunnables = mutableListOf<Runnable>()

        @BeforeEach
        fun setup() {
            every { webView.evaluateJavascript(any(), any()) } answers {
                evaluatedScripts += firstArg<String>()
                evaluateCallbacks += secondArg<ValueCallback<String>>()
            }
            every { webView.postDelayed(any(), any()) } answers {
                delayedRunnables += firstArg<Runnable>()
                true
            }
        }

        @Test
        fun `Given PingUrl when run then a HEAD request bypassing the caches is sent for the url`() = runTest {
            WebViewAction.PingUrl(TEST_URL).run(webView)

            val script = evaluatedScripts.first()
            assertTrue(script.contains(TEST_URL_JSON), "the script should fetch the JSON-encoded url")
            assertTrue(script.contains("method: 'HEAD'"), "the request should be a HEAD request")
            assertTrue(script.contains("cache: 'no-store'"), "the request should bypass the HTTP cache")
        }

        @Test
        fun `Given a hostile url when run then the url is JSON-escaped and cannot break out`() = runTest {
            // Crafted to close the string literal and inject code if interpolated raw.
            WebViewAction.PingUrl("""x");alert(1);//""").run(webView)

            // The embedded double quote must be backslash-escaped (proof of JSON encoding), so the
            // payload stays inside the url string literal instead of becoming executable code.
            assertTrue(evaluatedScripts.first().contains("\\\""))
        }

        @Test
        fun `Given the request completes when awaited then await returns`() = runTest {
            val action = WebViewAction.PingUrl(TEST_URL)
            action.run(webView)

            evaluateCallbacks[0].onReceiveValue(null)
            // First completion poll reports the completed url.
            evaluateCallbacks[1].onReceiveValue(TEST_URL_JSON)

            action.await()
            assertEquals(0, delayedRunnables.size, "no retry should be scheduled once completed")
        }

        @Test
        fun `Given the request is still running then polling retries until it completes`() = runTest {
            val action = WebViewAction.PingUrl(TEST_URL)
            action.run(webView)

            evaluateCallbacks[0].onReceiveValue(null)
            // The completion variable is still null: a retry must be scheduled.
            evaluateCallbacks[1].onReceiveValue("null")
            assertEquals(1, delayedRunnables.size)

            delayedRunnables.removeFirst().run()
            evaluateCallbacks[2].onReceiveValue(TEST_URL_JSON)

            action.await()
        }

        @Test
        fun `Given two pings then each uses its own completion variable so a superseded ping cannot overwrite the other`() = runTest {
            WebViewAction.PingUrl(TEST_URL).run(webView)
            WebViewAction.PingUrl(TEST_URL).run(webView)

            val firstVariable = evaluatedScripts[0].substringAfter("window.").substringBefore(" =")
            val secondVariable = evaluatedScripts[1].substringAfter("window.").substringBefore(" =")
            assertNotEquals(firstVariable, secondVariable)
        }

        @Test
        fun `Given a completion for another url then it is ignored and polling continues`() = runTest {
            val action = WebViewAction.PingUrl(TEST_URL)
            action.run(webView)

            evaluateCallbacks[0].onReceiveValue(null)
            evaluateCallbacks[1].onReceiveValue("\"https://other.example/manifest.json\"")

            assertFalse(action.result.isCompleted, "a stale completion must not complete this ping")
            assertEquals(1, delayedRunnables.size, "polling should continue")
        }

        @Test
        fun `Given the request never completes when awaited then await throws after the timeout and stops the polling`() = runTest {
            val action = WebViewAction.PingUrl(TEST_URL)
            action.run(webView)
            evaluateCallbacks[0].onReceiveValue(null)
            evaluateCallbacks[1].onReceiveValue("null")

            val awaitResult = async { runCatching { action.await() } }
            advanceTimeBy(WebViewAction.PingUrl.PING_TIMEOUT + 1.seconds)
            runCurrent()

            assertInstanceOf(TimeoutCancellationException::class.java, awaitResult.await().exceptionOrNull())

            // The pending retry must stop polling instead of scheduling another one.
            val evaluateCount = evaluateCallbacks.size
            delayedRunnables.removeFirst().run()
            assertEquals(evaluateCount, evaluateCallbacks.size, "polling should stop once await gave up")
        }
    }
}
