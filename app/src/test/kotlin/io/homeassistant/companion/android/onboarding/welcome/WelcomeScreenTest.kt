package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class WelcomeScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given WelcomeScreen when click on buttons then triggers callbacks`() {
        var connectClicked = false
        var leanMoreClicked = false

        composeTestRule.apply {
            setContent {
                WelcomeScreen(
                    onConnectClick = { connectClicked = true },
                    onLearnMoreClick = { leanMoreClicked = true },
                )
            }

            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha)).assertIsDisplayed().performClick()
            assertTrue(connectClicked)

            onNodeWithText(stringResource(commonR.string.welcome_learn_more)).performScrollTo().assertIsDisplayed().performClick()
            assertTrue(leanMoreClicked)
        }
    }
}
