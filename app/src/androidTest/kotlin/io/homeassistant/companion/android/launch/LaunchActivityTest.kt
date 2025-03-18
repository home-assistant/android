package io.homeassistant.companion.android.launch

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class LaunchActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
        fail()
    }
}
