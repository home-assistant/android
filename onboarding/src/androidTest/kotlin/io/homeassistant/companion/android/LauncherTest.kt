package io.homeassistant.companion.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

class LauncherTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<Launcher>()

    @get:Rule
    val ruleChain = DetectLeaksAfterTestSuccess()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
