package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SimpleConfirmDialogTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given dialog shown then title message and buttons are displayed`() {
        composeTestRule.apply {
            setContent {
                SimpleConfirmDialog(
                    FrontendDialog.Confirm(
                        message = "Are you sure?",
                        onConfirm = {},
                        onCancel = {},
                    ),
                )
            }

            onNodeWithText(stringResource(commonR.string.app_name)).assertIsDisplayed()
            onNodeWithText("Are you sure?").assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.ok)).assertIsDisplayed().assertIsEnabled()
            onNodeWithText(stringResource(commonR.string.cancel)).assertIsDisplayed().assertIsEnabled()
        }
    }

    @Test
    fun `Given dialog shown when OK clicked then onConfirm is called`() {
        var confirmCalled = false

        composeTestRule.apply {
            setContent {
                SimpleConfirmDialog(
                    FrontendDialog.Confirm(
                        message = "Are you sure?",
                        onConfirm = { confirmCalled = true },
                        onCancel = {},
                    ),
                )
            }

            onNodeWithText(stringResource(commonR.string.ok)).performClick()

            assertTrue(confirmCalled)
        }
    }

    @Test
    fun `Given dialog shown when Cancel clicked then onCancel is called`() {
        var cancelCalled = false

        composeTestRule.apply {
            setContent {
                SimpleConfirmDialog(
                    FrontendDialog.Confirm(
                        message = "Are you sure?",
                        onConfirm = {},
                        onCancel = { cancelCalled = true },
                    ),
                )
            }

            onNodeWithText(stringResource(commonR.string.cancel)).performClick()

            assertTrue(cancelCalled)
        }
    }
}
