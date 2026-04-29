package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HttpAuthDialogTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given dialog shown then the content is displayed and button enabled`() {
        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com requires a username and password.",
                    isAuthError = false,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.auth_request)).assertIsDisplayed()
            onNodeWithText("https://example.com requires a username and password.").assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.username)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.password)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.remember)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.ok)).assertIsEnabled()
            onNodeWithText(stringResource(commonR.string.cancel)).assertIsEnabled()
        }
    }

    @Test
    fun `Given fields filled when OK clicked then onProceed receives values`() {
        var capturedUsername = ""
        var capturedPassword = ""
        var capturedRemember = false

        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com",
                    isAuthError = false,
                    onProceed = { username, password, remember ->
                        capturedUsername = username
                        capturedPassword = password
                        capturedRemember = remember
                    },
                    onCancel = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.username)).performTextInput("admin")
            onNodeWithText(stringResource(commonR.string.password)).performTextInput("secret")
            onNodeWithText(stringResource(commonR.string.remember)).performClick()
            onNodeWithText(stringResource(commonR.string.ok)).performClick()

            assertEquals("admin", capturedUsername)
            assertEquals("secret", capturedPassword)
            assertTrue(capturedRemember)
        }
    }

    @Test
    fun `Given password hidden when toggle visibility clicked then password is shown`() {
        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com",
                    isAuthError = false,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
                )
            }

            // Initially shows "Show password" content description
            onNodeWithContentDescription(stringResource(commonR.string.show_password)).assertIsDisplayed()

            // Click toggle
            onNodeWithContentDescription(stringResource(commonR.string.show_password)).performClick()

            // Now shows "Hide password" content description
            onNodeWithContentDescription(stringResource(commonR.string.hide_password)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given isAuthError true when dialog shown then rejection notice is displayed`() {
        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com",
                    isAuthError = true,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.auth_request_rejected)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given isAuthError false when dialog shown then rejection notice is absent`() {
        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com",
                    isAuthError = false,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
                )
            }

            onAllNodesWithText(stringResource(commonR.string.auth_request_rejected)).assertCountEquals(0)
        }
    }

    @Test
    fun `Given remember unchecked when OK clicked then onProceed receives false`() {
        var capturedRemember = true

        composeTestRule.apply {
            setContent {
                HttpAuthDialog(
                    message = "https://example.com",
                    isAuthError = false,
                    onProceed = { _, _, remember -> capturedRemember = remember },
                    onCancel = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.username)).performTextInput("admin")
            onNodeWithText(stringResource(commonR.string.password)).performTextInput("secret")
            onNodeWithText(stringResource(commonR.string.ok)).performClick()

            assertFalse(capturedRemember)
        }
    }
}
