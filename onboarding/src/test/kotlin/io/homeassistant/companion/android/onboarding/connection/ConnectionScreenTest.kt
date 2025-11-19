package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.compose.composable.HA_WEBVIEW_TAG
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
class ConnectionScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given ConnectionScreen when url is null then webview is not displayed`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackClick = {},
                    isLoading = false,
                    isError = false,
                    url = null,
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when isLoading then show loading`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackClick = {},
                    isLoading = true,
                    isError = false,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.loading_content_description)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when not isLoading then don't show loading`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackClick = {},
                    isLoading = false,
                    isError = false,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.loading_content_description)).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when isError is true then don't show webview and show placeholder`() {
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackClick = {},
                    isLoading = false,
                    isError = true,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsNotDisplayed()
            onNodeWithTag(CONNECTION_SCREEN_ERROR_PLACEHOLDER_TAG).assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.loading_content_description)).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given ConnectionScreen when pressing back then triggers onBackClick`() {
        var backPressed = false
        composeTestRule.apply {
            setContent {
                ConnectionScreen(
                    onBackClick = {
                        backPressed = true
                    },
                    isLoading = false,
                    isError = false,
                    url = "",
                    webViewClient = WebViewClient(),
                )
            }

            activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(backPressed)
        }
    }
}
