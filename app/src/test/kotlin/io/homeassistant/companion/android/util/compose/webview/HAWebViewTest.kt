package io.homeassistant.companion.android.util.compose.webview

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.util.FailFast
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertTrue
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

    // region WebViewTimersManager

    @Test
    fun `Given no started WebView when a WebView starts then timers resume`() {
        val timersManager = WebViewTimersManager()

        timersManager.onWebViewStarted(webView)

        verify(exactly = 1) { webView.resumeTimers() }
        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given a started WebView when a second WebView starts then timers are not resumed again`() {
        val timersManager = WebViewTimersManager()
        val otherWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStarted(otherWebView)

        verify(exactly = 0) { otherWebView.resumeTimers() }
    }

    @Test
    fun `Given a started WebView when it stops then timers pause`() {
        val timersManager = WebViewTimersManager()
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStopped(webView)

        verify(exactly = 1) { webView.pauseTimers() }
    }

    @Test
    fun `Given two started WebViews when only one stops then timers are not paused`() {
        val timersManager = WebViewTimersManager()
        val otherWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStarted(otherWebView)

        timersManager.onWebViewStopped(webView)

        verify(exactly = 0) { webView.pauseTimers() }
        verify(exactly = 0) { otherWebView.pauseTimers() }
    }

    @Test
    fun `Given a screen transition where the new WebView starts before the old one stops then timers are never paused`() {
        val timersManager = WebViewTimersManager()
        val newWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)

        // Android starts the new activity before stopping the old one (B.onStart before A.onStop).
        timersManager.onWebViewStarted(newWebView)
        timersManager.onWebViewStopped(webView)

        verify(exactly = 0) { webView.pauseTimers() }
        verify(exactly = 0) { newWebView.pauseTimers() }
    }

    @Test
    fun `Given all WebViews stopped when a WebView starts again then timers resume again`() {
        val timersManager = WebViewTimersManager()
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStopped(webView)

        timersManager.onWebViewStarted(webView)

        verify(exactly = 2) { webView.resumeTimers() }
    }

    @Test
    fun `Given no started WebView when a WebView stops then it fails fast without pausing timers`() {
        val timersManager = WebViewTimersManager()
        var failFastTriggered = false
        FailFast.setHandler { _, _ -> failFastTriggered = true }

        timersManager.onWebViewStopped(webView)

        assertTrue(failFastTriggered)
        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given a started WebView when it stops keeping timers running then timers are not paused`() {
        val timersManager = WebViewTimersManager()
        timersManager.onWebViewStarted(webView)

        timersManager.onWebViewStopped(webView, keepTimersRunning = true)

        verify(exactly = 0) { webView.pauseTimers() }
    }

    @Test
    fun `Given a WebView stopped keeping timers running when it starts and stops again then timers pause again`() {
        val timersManager = WebViewTimersManager()
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStopped(webView, keepTimersRunning = true)

        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStopped(webView)

        // The count reached zero in between: the restart resumes (a no-op on running timers)
        // and the next regular stop pauses again
        verify(exactly = 2) { webView.resumeTimers() }
        verify(exactly = 1) { webView.pauseTimers() }
    }

    @Test
    fun `Given two started WebViews when one stops keeping timers running then the last regular stop still pauses`() {
        val timersManager = WebViewTimersManager()
        val otherWebView = mockk<WebView>(relaxed = true)
        timersManager.onWebViewStarted(webView)
        timersManager.onWebViewStarted(otherWebView)

        timersManager.onWebViewStopped(webView, keepTimersRunning = true)
        timersManager.onWebViewStopped(otherWebView)

        verify(exactly = 1) { otherWebView.pauseTimers() }
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
            manager.onWebViewStopped(webView)
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
            manager.onWebViewStopped(webView)
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

    @Test
    fun `Given keep running while stopped when host activity stops then the WebView is not paused`() {
        composeTestRule.setContent {
            WebViewLifecycleEffect(webView = webView, keepRunningWhileStopped = { true }, manager = manager)
        }
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        verify(exactly = 0) { webView.onPause() }
        verify(exactly = 1) { manager.onWebViewStopped(webView, keepTimersRunning = true) }
    }

    @Test
    fun `Given keep running while stopped when host activity restarts then it registers and resumes as usual`() {
        composeTestRule.setContent {
            WebViewLifecycleEffect(webView = webView, keepRunningWhileStopped = { true }, manager = manager)
        }
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        verify(exactly = 2) { manager.onWebViewStarted(webView) }
        verify(exactly = 2) { webView.onResume() }
    }

    @Test
    fun `Given the screen commanded off only after start when host activity stops then the WebView keeps running`() {
        var keepRunning = false
        composeTestRule.setContent {
            WebViewLifecycleEffect(webView = webView, keepRunningWhileStopped = { keepRunning }, manager = manager)
        }
        composeTestRule.waitForIdle()

        keepRunning = true
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        verify(exactly = 0) { webView.onPause() }
        verify(exactly = 1) { manager.onWebViewStopped(webView, keepTimersRunning = true) }
    }

    // endregion
}
