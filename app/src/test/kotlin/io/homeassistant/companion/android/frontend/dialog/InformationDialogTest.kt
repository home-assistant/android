package io.homeassistant.companion.android.frontend.dialog

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class InformationDialogTest {

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given dialog shown then title message and OK button are displayed`() {
        composeTestRule.apply {
            setContent {
                InformationDialog(
                    FrontendDialog.Information(message = "Something happened", onDismiss = {}),
                    onOpenExternalLink = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.app_name)).assertIsDisplayed()
            onNodeWithText("Something happened").assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.ok)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given dialog shown when OK clicked then onDismiss is called`() {
        var dismissed = false

        composeTestRule.apply {
            setContent {
                InformationDialog(
                    FrontendDialog.Information(message = "Something happened", onDismiss = { dismissed = true }),
                    onOpenExternalLink = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.ok)).performClick()

            assertTrue(dismissed)
        }
    }

    @Test
    fun `Given dialog without moreInfoUrl then Learn more is not displayed`() {
        composeTestRule.apply {
            setContent {
                InformationDialog(
                    FrontendDialog.Information(message = "Something happened", onDismiss = {}),
                    onOpenExternalLink = {},
                )
            }

            onNodeWithText(stringResource(commonR.string.learn_more)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given dialog with moreInfoUrl when Learn more clicked then link opens and dialog stays`() {
        var openedUrl: Uri? = null

        composeTestRule.apply {
            setContent {
                InformationDialog(
                    FrontendDialog.Information(
                        message = "Something happened",
                        onDismiss = {},
                        moreInfoUrl = "https://example.com/docs".toUri(),
                    ),
                    onOpenExternalLink = { openedUrl = it },
                )
            }

            onNodeWithText(stringResource(commonR.string.learn_more)).performClick()

            assertEquals("https://example.com/docs".toUri(), openedUrl)
            onNodeWithText("Something happened").assertIsDisplayed()
        }
    }
}
