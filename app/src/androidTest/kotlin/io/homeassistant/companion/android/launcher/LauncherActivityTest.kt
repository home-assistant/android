package io.homeassistant.companion.android.launcher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

class LauncherActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<LauncherActivity>()

    @get:Rule
    val ruleChain = DetectLeaksAfterTestSuccess()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
