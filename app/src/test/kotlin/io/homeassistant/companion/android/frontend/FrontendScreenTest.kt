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
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.webview.HA_WEBVIEW_TAG
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Loading(
                        serverId = 1,
                        url = "https://example.com",
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
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
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.LoadServer(serverId = 1),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            assertIsLoading(true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Error state then error screen with retry button and webview are displayed`() {
        var retryCalled = false
        val error = FrontendConnectionError.UnreachableError(
            message = commonR.string.webview_error_HOST_LOOKUP,
            errorDetails = "Connection failed",
            rawErrorType = "HostLookupError",
        )
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Error(
                        serverId = 1,
                        url = "https://example.com",
                        error = error,
                    ),
                    errorStateProvider = FakeConnectionErrorStateProvider(
                        url = "https://example.com",
                        error = error,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = { retryCalled = true },
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
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
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Content(
                        serverId = 1,
                        url = "https://example.com",
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            assertIsLoading(false)

            // Error screen should not be displayed
            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertDoesNotExist()
            // Retry button should not be displayed
            onNodeWithText(stringResource(commonR.string.retry)).assertDoesNotExist()
            // Insecure should not be displayed
            onNodeWithText(stringResource(commonR.string.block_insecure_title)).assertDoesNotExist()

            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Insecure state then block insecure screen is displayed and webview displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 1,
                        missingHomeSetup = true,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            assertIsLoading(false)

            // Block insecure screen should be displayed
            onNodeWithText(stringResource(commonR.string.block_insecure_title)).assertIsDisplayed()
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given SecurityLevelRequired state then security level screen is displayed and webview displayed`() {
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            assertIsLoading(false)

            // Security level screen should be displayed
            onNodeWithText(stringResource(commonR.string.location_secure_connection_title)).assertIsDisplayed()
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Insecure state when open settings clicked then onOpenSettings is called`() {
        var openSettingsCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 1,
                        missingHomeSetup = false,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = { openSettingsCalled = true },
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithText(stringResource(commonR.string.block_insecure_open_settings)).performScrollTo().performClick()

            assertTrue("onOpenSettings should be called when open settings button is clicked", openSettingsCalled)
        }
    }

    @Test
    fun `Given Insecure state when change security level clicked then onChangeSecurityLevel is called`() {
        var changeSecurityLevelCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 1,
                        missingHomeSetup = false,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = { changeSecurityLevelCalled = true },
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithText(stringResource(commonR.string.block_insecure_change_security_level)).performScrollTo().performClick()

            assertTrue("onChangeSecurityLevel should be called when change security level button is clicked", changeSecurityLevelCalled)
        }
    }

    @Test
    fun `Given Insecure state when retry clicked then onRetry is called`() {
        var retryCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 1,
                        missingHomeSetup = false,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = { retryCalled = true },
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.block_insecure_retry)).performClick()

            assertTrue("onRetry should be called when retry button is clicked", retryCalled)
        }
    }

    @Test
    fun `Given Insecure state with missingHomeSetup when configure home clicked then onConfigureHomeNetwork is called`() {
        var configureHomeNetworkServerId: Int? = null
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 42,
                        missingHomeSetup = true,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { serverId -> configureHomeNetworkServerId = serverId },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithText(stringResource(commonR.string.block_insecure_action_configure_home)).performScrollTo().performClick()

            assertTrue("onConfigureHomeNetwork should be called with correct serverId", configureHomeNetworkServerId == 42)
        }
    }

    @Test
    fun `Given Insecure state when help clicked then onHelpClick is called`() {
        var helpClickCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.Insecure(
                        serverId = 1,
                        missingHomeSetup = false,
                        missingLocation = false,
                    ),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = { helpClickCalled = true },
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()

            assertTrue("onHelpClick should be called when help button is clicked", helpClickCalled)
        }
    }

    @Test
    fun `Given SecurityLevelRequired state when close clicked then onSecurityLevelDone is called`() {
        var configuredCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onSecurityLevelDone = { configuredCalled = true },
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.close)).performClick()

            assertTrue("onSecurityLevelDone should be called when close button is clicked", configuredCalled)
        }
    }

    @Test
    fun `Given SecurityLevelRequired state when help clicked then onSecurityLevelHelpClick is called`() {
        var helpClickCalled = false
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                    webViewClient = WebViewClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = { helpClickCalled = true },
                    onShowSnackbar = { _, _ -> true },
                )
            }

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()

            assertTrue("onSecurityLevelHelpClick should be called when help button is clicked", helpClickCalled)
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

private class FakeConnectionErrorStateProvider(
    url: String?,
    error: FrontendConnectionError?,
) : FrontendConnectionErrorStateProvider {
    override val urlFlow: StateFlow<String?> = MutableStateFlow(url)
    override val errorFlow: StateFlow<FrontendConnectionError?> = MutableStateFlow(error)
    override val connectivityCheckState: StateFlow<ConnectivityCheckState> =
        MutableStateFlow(ConnectivityCheckState())
    override fun runConnectivityChecks() = Unit
}
