package io.homeassistant.companion.android.splash

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SplashActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<SplashActivity>()

    @Test
    fun launchActivity() {
        composeTestRule.waitForIdle()
    }
}
