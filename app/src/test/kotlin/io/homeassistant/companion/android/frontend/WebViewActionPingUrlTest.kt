package io.homeassistant.companion.android.frontend

import android.webkit.ValueCallback
import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TEST_URL = "https://example.com/manifest.json"

/** [TEST_URL] as the JSON-encoded value evaluateJavascript reports for the completion variable. */
private const val TEST_URL_JSON = "\"$TEST_URL\""

/**
 * Tests [WebViewAction.PingUrl] by capturing the scripts it evaluates and feeding results back
 * through the captured callbacks, standing in for the WebView.
 */
@OptIn(EvaluateJavascriptUsage::class)
class WebViewActionPingUrlTest {

    private val webView: WebView = mockk(relaxed = true)

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
