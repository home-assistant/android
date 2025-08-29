package io.homeassistant.companion.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LauncherTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<Launcher>()

    @get:Rule(order = 2)
    val ruleChain = DetectLeaksAfterTestSuccess()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
