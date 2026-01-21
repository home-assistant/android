package io.homeassistant.companion.android.frontend

import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.webview.HA_WEBVIEW_TAG
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class FrontendScreenTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given Loading state then loading indicator and webview are displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendScreen(
                    onBackClick = {},
                    viewState = FrontendViewState.Loading(
                        serverId = 1,
                        url = "https://example.com",
                    ),
                    webViewClient = WebViewClient(),
                    javascriptInterface = FrontendJavascriptInterface.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onRetry = {},
                    onOpenExternalLink = {},
                )
            }

            assertIsLoading(true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given LoadServer state then loading indicator and webview are displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendScreen(
                    onBackClick = {},
                    viewState = FrontendViewState.LoadServer(serverId = 1),
                    webViewClient = WebViewClient(),
                    javascriptInterface = FrontendJavascriptInterface.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onRetry = {},
                    onOpenExternalLink = {},
                )
            }

            assertIsLoading(true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Error state then error screen with retry button and webview are displayed`() {
        var retryCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreen(
                    onBackClick = {},
                    viewState = FrontendViewState.Error(
                        serverId = 1,
                        url = "https://example.com",
                        error = FrontendError.UnreachableError(
                            message = commonR.string.webview_error_HOST_LOOKUP,
                            errorDetails = "Connection failed",
                            rawErrorType = "HostLookupError",
                        ),
                    ),
                    webViewClient = WebViewClient(),
                    javascriptInterface = FrontendJavascriptInterface.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onRetry = { retryCalled = true },
                    onOpenExternalLink = {},
                )
            }

            assertIsLoading(false)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()

            // Error title should be displayed
            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertIsDisplayed()
            // Error message should be displayed
            onNodeWithText(stringResource(commonR.string.webview_error_HOST_LOOKUP)).assertIsDisplayed()
            // Retry button should be displayed (may need scrolling)
            onNodeWithText(stringResource(commonR.string.retry)).performScrollTo().performClick()
            assertTrue("onRetry should be called when retry button is clicked", retryCalled)
        }
    }

    @Test
    fun `Given Content state then webview is displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendScreen(
                    onBackClick = {},
                    viewState = FrontendViewState.Content(
                        serverId = 1,
                        url = "https://example.com",
                    ),
                    webViewClient = WebViewClient(),
                    javascriptInterface = FrontendJavascriptInterface.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onRetry = {},
                    onOpenExternalLink = {},
                )
            }

            assertIsLoading(false)

            // Error screen should not be displayed
            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertDoesNotExist()
            // Retry button should not be displayed
            onNodeWithText(stringResource(commonR.string.retry)).assertDoesNotExist()

            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    private fun AndroidComposeTestRule<ActivityScenarioRule<HiltComponentActivity>, HiltComponentActivity>.assertIsLoading(show: Boolean) {
        val node = onNodeWithContentDescription(stringResource(commonR.string.loading_content_description))
        if (show) {
            node.assertIsDisplayed()
        } else {
            node.assertDoesNotExist()
        }
    }
}
