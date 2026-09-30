package io.homeassistant.companion.android.launch

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

class LaunchActivityTest {

    @get:Rule(order = 1)
    val detectLeaksRule = DetectLeaksAfterTestSuccess()

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
