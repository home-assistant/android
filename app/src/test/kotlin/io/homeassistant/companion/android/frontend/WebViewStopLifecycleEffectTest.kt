package io.homeassistant.companion.android.frontend

import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class WebViewStopLifecycleEffectTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given webview when host activity stops then it pauses and on start resumes`() {
        val webView = mockk<WebView>(relaxed = true)

        composeTestRule.setContent {
            WebViewStopLifecycleEffect(webView = webView)
        }
        composeTestRule.waitForIdle()
        // Registering the observer while RESUMED replays ON_START (a resume); ignore that first call.
        clearMocks(webView, answers = false)

        // Screen off / app backgrounded dispatches ON_STOP.
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        verifyOrder {
            webView.onPause()
            webView.pauseTimers()
        }

        clearMocks(webView, answers = false)

        // Returning to the foreground dispatches ON_START.
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        verifyOrder {
            webView.resumeTimers()
            webView.onResume()
        }
    }
}
