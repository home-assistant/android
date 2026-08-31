package io.homeassistant.companion.android.launch

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

class LaunchActivityTest {

    @get:Rule(order = 1)
    private val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    @get:Rule(order = 2)
    private val detectLeaksRule = DetectLeaksAfterTestSuccess()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
