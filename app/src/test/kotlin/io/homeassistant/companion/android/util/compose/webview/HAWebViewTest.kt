package io.homeassistant.companion.android.util.compose.webview

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.util.FailFast
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HAWebViewTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val webView = mockk<WebView>(relaxed = true)
    private val manager = mockk<WebViewTimersManager>(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region Debugging toggle

    @Test
    fun `Given debugging enabled when the WebView is created then contents debugging is enabled first`() {
        mockkStatic(WebView::class)
        every { WebView.setWebContentsDebuggingEnabled(any()) } just runs
        var failure: Throwable? = null

        composeTestRule.setContent {
            HAWebView(onWebViewCreationFailed = { failure = it }, debuggingEnabled = true)
        }
        composeTestRule.waitForIdle()

        assertNull(failure)
        verify { WebView.setWebContentsDebuggingEnabled(true) }
    }

    @Test
    fun `Given debugging unknown when the WebView is created then contents debugging is never disabled`() {
        mockkStatic(WebView::class)
        every { WebView.setWebContentsDebuggingEnabled(any()) } just runs

        composeTestRule.setContent {
            HAWebView(onWebViewCreationFailed = {}, debuggingEnabled = null)
        }
        composeTestRule.waitForIdle()

        verify(exactly = 0) { WebView.setWebContentsDebuggingEnabled(false) }
    }

    @Test
    fun `Given the WebView provider fails to start when the WebView is created then the failure is reported`() {
        // Chromium refuses to start when another process of the app still holds the data-directory lock
        mockkStatic(WebView::class)
        every { WebView.setWebContentsDebuggingEnabled(any()) } throws RuntimeException("lock owner unknown")
        var failure: Throwable? = null

        composeTestRule.setContent {
            HAWebView(onWebViewCreationFailed = { failure = it }, debuggingEnabled = true)
        }
        composeTestRule.waitForIdle()

        assertEquals("lock owner unknown", failure?.message)
    }

    // endregion

    // region WebViewTimersManager

    @Test
    fun `Given no started WebView when a WebView starts then timers resume`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)

        timersManager.onWebViewStarted(webView)

        verify(exactly = 1) { webView.resumeTimers() }
        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given a started WebView when a second WebView starts then timers are not resumed again`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        val otherWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStarted(otherWebView)

        verify(exactly = 0) { otherWebView.resumeTimers() }
    }

    @Test
    fun `Given a started WebView when it stops then timers pause only once the delay elapsed`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStopped()
        runCurrent()
        verify(exactly = 0) { webView.pauseTimers() }

        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY - 1.seconds)
        runCurrent()
        verify(exactly = 0) { webView.pauseTimers() }

        advanceTimeBy(1.seconds)
        runCurrent()
        verify(exactly = 1) { webView.pauseTimers() }
    }

    @Test
    fun `Given two started WebViews when only one stops then timers are not paused`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        val otherWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStarted(otherWebView)

        timersManager.onWebViewStopped()
        advancePauseDelay()

        verify(exactly = 0) { webView.pauseTimers() }
        verify(exactly = 0) { otherWebView.pauseTimers() }
    }

    @Test
    fun `Given a screen transition where the new WebView starts before the old one stops then timers are never paused`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        val newWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)

        // Android starts the new activity before stopping the old one (B.onStart before A.onStop).
        timersManager.onWebViewStarted(newWebView)
        timersManager.onWebViewStopped()
        advancePauseDelay()

        verify(exactly = 0) { webView.pauseTimers() }
        verify(exactly = 0) { newWebView.pauseTimers() }
    }

    @Test
    fun `Given a stopped WebView when a WebView starts within the delay then the pause is cancelled and timers resume`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStopped()
        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY / 2)

        timersManager.onWebViewStarted(webView)
        advancePauseDelay()

        verify(exactly = 2) { webView.resumeTimers() }
        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given a WebView restarted during the delay when it stops again then timers pause once after the new delay`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStopped()
        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY / 2)
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStopped()
        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY / 2)
        runCurrent()
        verify(exactly = 0) { webView.pauseTimers() }

        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY / 2)
        runCurrent()
        verify(exactly = 1) { webView.pauseTimers() }
    }

    @Test
    fun `Given no started WebView when a WebView stops then it fails fast without pausing timers`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        var failFastTriggered = false
        FailFast.setHandler { _, _ -> failFastTriggered = true }

        timersManager.onWebViewStopped()
        advancePauseDelay()

        assertTrue(failFastTriggered)
        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given the last stopped WebView collected during the delay when it elapses then a surviving WebView pauses timers`() = runTest {
        val timersManager = WebViewTimersManager(backgroundScope)
        timersManager.onWebViewStarted(webView)
        val collectedRef = startAndStopTransientWebView(timersManager)
        timersManager.onWebViewStopped()
        awaitCollected(collectedRef)

        advancePauseDelay()

        verify(exactly = 1) { webView.pauseTimers() }
    }

    /**
     * Starts and stops a second WebView while [webView] is still started, returning only a weak
     * reference so the instance can be collected once this function returns. A real Robolectric
     * WebView is used because MockK keeps its mocks reachable.
     */
    private fun startAndStopTransientWebView(timersManager: WebViewTimersManager): WeakReference<WebView> {
        val transient = WebView(ApplicationProvider.getApplicationContext())
        timersManager.onWebViewStarted(transient)
        timersManager.onWebViewStopped()
        return WeakReference(transient)
    }

    private fun awaitCollected(ref: WeakReference<*>) {
        repeat(GC_ATTEMPTS) {
            if (ref.get() == null) return
            System.gc()
        }
        fail("The WebView was not collected, the test cannot exercise the collected branch")
    }

    private fun TestScope.advancePauseDelay() {
        advanceTimeBy(WEBVIEW_TIMERS_PAUSE_DELAY)
        runCurrent()
    }

    // endregion

    // region WebViewLifecycleEffect

    @Test
    fun `Given webview when host activity starts and stops then it resumes and pauses with the timers`() {
        composeTestRule.setContent {
            WebViewLifecycleEffect(webView = webView, manager = manager)
        }
        composeTestRule.waitForIdle()
        verifyOrder {
            manager.onWebViewStarted(webView)
            webView.onResume()
        }

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        verifyOrder {
            webView.onPause()
            manager.onWebViewStopped()
        }

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        verify(exactly = 2) { manager.onWebViewStarted(webView) }
        verify(exactly = 2) { webView.onResume() }
    }

    @Test
    fun `Given webview when effect leaves composition then it pauses and unregisters from the timers`() {
        val showEffect = mutableStateOf(true)
        composeTestRule.setContent {
            val show by showEffect
            if (show) {
                WebViewLifecycleEffect(webView = webView, manager = manager)
            }
        }
        composeTestRule.waitForIdle()
        verify(exactly = 1) { manager.onWebViewStarted(webView) }

        showEffect.value = false
        composeTestRule.waitForIdle()
        verifyOrder {
            webView.onPause()
            manager.onWebViewStopped()
        }
    }

    @Test
    fun `Given null webview when host starts then nothing is notified`() {
        composeTestRule.setContent {
            WebViewLifecycleEffect(webView = null, manager = manager)
        }
        composeTestRule.waitForIdle()

        verify(exactly = 0) { manager.onWebViewStarted(any()) }
    }

    // endregion
}

private const val GC_ATTEMPTS = 20
