package io.homeassistant.companion.android.launch

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

class LaunchActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    @get:Rule
    val ruleChain = DetectLeaksAfterTestSuccess()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
