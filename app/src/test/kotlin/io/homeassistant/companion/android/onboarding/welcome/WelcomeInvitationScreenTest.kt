package io.homeassistant.companion.android.onboarding.welcome

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_SERVER_URL = "http://homeassistant.local:8123"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class WelcomeInvitationScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given WelcomeInvitationScreen when click on buttons then triggers callbacks`() {
        var acceptClicked = false
        var rejectClicked = false
        var learnMoreClicked = false

        composeTestRule.apply {
            setContent {
                WelcomeInvitationScreen(
                    serverUrl = TEST_SERVER_URL,
                    onAcceptClick = { acceptClicked = true },
                    onRejectClick = { rejectClicked = true },
                    onLearnMoreClick = { learnMoreClicked = true },
                )
            }

            onNodeWithText(stringResource(commonR.string.welcome_invitation_accept))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            assertTrue(acceptClicked)

            onNodeWithText(stringResource(commonR.string.welcome_invitation_reject))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            assertTrue(rejectClicked)

            // Learn more lives in the top bar as a help action.
            onNodeWithContentDescription(stringResource(commonR.string.get_help))
                .assertIsDisplayed()
                .performClick()
            waitForIdle()
            assertTrue(learnMoreClicked)
        }
    }

    @Test
    fun `Given WelcomeInvitationScreen when displayed then shows server address and label`() {
        composeTestRule.apply {
            setContent {
                WelcomeInvitationScreen(
                    serverUrl = TEST_SERVER_URL,
                    onAcceptClick = {},
                    onRejectClick = {},
                    onLearnMoreClick = {},
                )
            }

            onNodeWithText(TEST_SERVER_URL).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.welcome_invitation_server_label))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun `Given WelcomeInvitationScreen when click on server address then copy url to clipboard`() {
        composeTestRule.apply {
            setContent {
                WelcomeInvitationScreen(
                    serverUrl = TEST_SERVER_URL,
                    onAcceptClick = {},
                    onRejectClick = {},
                    onLearnMoreClick = {},
                )
            }

            onNodeWithText(TEST_SERVER_URL).performScrollTo().performClick()
            waitForIdle()

            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            assertEquals(
                TEST_SERVER_URL,
                clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
            )
        }
    }
}
