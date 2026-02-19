package io.homeassistant.companion.android.frontend

import android.Manifest
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.permissions.PendingWebViewPermissionRequest
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.FakePermissionResultRegistry
import io.homeassistant.companion.android.util.compose.webview.HA_WEBVIEW_TAG
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
            setFrontendScreen(
                viewState = FrontendViewState.Loading(serverId = 1, url = "https://example.com"),
            )

            assertIsLoading(true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given LoadServer state then loading indicator and webview are displayed`() {
        composeTestRule.apply {
            setFrontendScreen(viewState = FrontendViewState.LoadServer(serverId = 1))

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
            setFrontendScreen(
                viewState = FrontendViewState.Error(serverId = 1, url = "https://example.com", error = error),
                errorStateProvider = FakeConnectionErrorStateProvider(url = "https://example.com", error = error),
                onBlockInsecureRetry = { retryCalled = true },
            )

            assertIsLoading(false)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.webview_error_HOST_LOOKUP)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.retry)).performScrollTo().performClick()
            assertTrue("onRetry should be called when retry button is clicked", retryCalled)
        }
    }

    @Test
    fun `Given Content state then webview is displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
            )

            assertIsLoading(false)
            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.retry)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.block_insecure_title)).assertDoesNotExist()
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Insecure state then block insecure screen is displayed and webview displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 1, missingHomeSetup = true, missingLocation = false),
            )

            assertIsLoading(false)
            onNodeWithText(stringResource(commonR.string.block_insecure_title)).assertIsDisplayed()
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given SecurityLevelRequired state then security level screen is displayed and webview displayed`() {
        composeTestRule.apply {
            setFrontendScreen(viewState = FrontendViewState.SecurityLevelRequired(serverId = 1))

            assertIsLoading(false)
            onNodeWithText(stringResource(commonR.string.location_secure_connection_title)).assertIsDisplayed()
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Insecure state when open settings clicked then onOpenSettings is called`() {
        var openSettingsCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 1, missingHomeSetup = false, missingLocation = false),
                onOpenSettings = { openSettingsCalled = true },
            )

            onNodeWithText(stringResource(commonR.string.block_insecure_open_settings)).performScrollTo().performClick()
            assertTrue("onOpenSettings should be called when open settings button is clicked", openSettingsCalled)
        }
    }

    @Test
    fun `Given Insecure state when change security level clicked then onChangeSecurityLevel is called`() {
        var changeSecurityLevelCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 1, missingHomeSetup = false, missingLocation = false),
                onChangeSecurityLevel = { changeSecurityLevelCalled = true },
            )

            onNodeWithText(stringResource(commonR.string.block_insecure_change_security_level)).performScrollTo().performClick()
            assertTrue("onChangeSecurityLevel should be called when change security level button is clicked", changeSecurityLevelCalled)
        }
    }

    @Test
    fun `Given Insecure state when retry clicked then onRetry is called`() {
        var retryCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 1, missingHomeSetup = false, missingLocation = false),
                onBlockInsecureRetry = { retryCalled = true },
            )

            onNodeWithContentDescription(stringResource(commonR.string.block_insecure_retry)).performClick()
            assertTrue("onRetry should be called when retry button is clicked", retryCalled)
        }
    }

    @Test
    fun `Given Insecure state with missingHomeSetup when configure home clicked then onConfigureHomeNetwork is called`() {
        var configureHomeNetworkServerId: Int? = null
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 42, missingHomeSetup = true, missingLocation = false),
                onConfigureHomeNetwork = { serverId -> configureHomeNetworkServerId = serverId },
            )

            onNodeWithText(stringResource(commonR.string.block_insecure_action_configure_home)).performScrollTo().performClick()
            assertTrue("onConfigureHomeNetwork should be called with correct serverId", configureHomeNetworkServerId == 42)
        }
    }

    @Test
    fun `Given Insecure state when help clicked then onHelpClick is called`() {
        var helpClickCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Insecure(serverId = 1, missingHomeSetup = false, missingLocation = false),
                onBlockInsecureHelpClick = { helpClickCalled = true },
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            assertTrue("onHelpClick should be called when help button is clicked", helpClickCalled)
        }
    }

    @Test
    fun `Given SecurityLevelRequired state when close clicked then onSecurityLevelDone is called`() {
        var configuredCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                onSecurityLevelDone = { configuredCalled = true },
            )

            onNodeWithContentDescription(stringResource(commonR.string.close)).performClick()
            assertTrue("onSecurityLevelDone should be called when close button is clicked", configuredCalled)
        }
    }

    @Test
    fun `Given SecurityLevelRequired state when help clicked then onSecurityLevelHelpClick is called`() {
        var helpClickCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                onSecurityLevelHelpClick = { helpClickCalled = true },
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            assertTrue("onSecurityLevelHelpClick should be called when help button is clicked", helpClickCalled)
        }
    }

    @Test
    fun `Given Content state with showNotificationPermission false then notification prompt is not displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com", showNotificationPermission = false),
            )

            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_title)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given notification prompt displayed when deny clicked then onNotificationPermissionResult is called with false`() {
        assertNotificationPermissionClick(
            buttonStringRes = commonR.string.notification_permission_dialog_deny,
            permissionGranted = false,
            expectedResult = false,
        )
    }

    @Test
    fun `Given notification prompt displayed when allow clicked and granted then onNotificationPermissionResult is called with true`() {
        assertNotificationPermissionClick(
            buttonStringRes = commonR.string.notification_permission_dialog_allow,
            permissionGranted = true,
            expectedResult = true,
        )
    }

    @Test
    fun `Given notification prompt displayed when allow clicked and denied then onNotificationPermissionResult is called with false`() {
        assertNotificationPermissionClick(
            buttonStringRes = commonR.string.notification_permission_dialog_allow,
            permissionGranted = false,
            expectedResult = false,
        )
    }

    private fun assertNotificationPermissionClick(
        buttonStringRes: Int,
        permissionGranted: Boolean,
        expectedResult: Boolean,
    ) {
        val grantedPermissions = if (permissionGranted) setOf(Manifest.permission.POST_NOTIFICATIONS) else emptySet()
        val registry = FakePermissionResultRegistry(grantedPermissions = grantedPermissions)
        var permissionResult: Boolean? = null

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com", showNotificationPermission = true),
                onNotificationPermissionResult = { permissionResult = it },
                supportsNotificationPermission = true,
                registry = registry,
            )

            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_allow)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_deny)).assertIsDisplayed()
            onNodeWithText(stringResource(buttonStringRes)).performClick()

            assertTrue(
                "onNotificationPermissionResult should be called with $expectedResult",
                permissionResult == expectedResult,
            )
        }
    }

    @Test
    fun `Given WebView requests camera when permission not granted then system permission dialog is launched for CAMERA`() {
        val registry = FakePermissionResultRegistry(grantedPermissions = setOf(Manifest.permission.CAMERA))
        val permissionRequest: PermissionRequest = mockk(relaxed = true) {
            every { resources } returns arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        }
        val permissionManager = createPermissionManager()
        permissionManager.onWebViewPermissionRequest(permissionRequest)

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                pendingWebViewPermission = permissionManager.pendingWebViewPermission.value,
                onWebViewPermissionResult = permissionManager::onWebViewPermissionResult,
                registry = registry,
            )
            waitForIdle()

            registry.assertPermissionsRequested(Manifest.permission.CAMERA)
            verify { permissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
        }
    }

    @Test
    fun `Given WebView requests mic when permission denied then WebView request is denied`() {
        val registry = FakePermissionResultRegistry(grantedPermissions = emptySet())
        val permissionRequest: PermissionRequest = mockk(relaxed = true) {
            every { resources } returns arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        }
        val permissionManager = createPermissionManager()
        permissionManager.onWebViewPermissionRequest(permissionRequest)

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                pendingWebViewPermission = permissionManager.pendingWebViewPermission.value,
                onWebViewPermissionResult = permissionManager::onWebViewPermissionResult,
                registry = registry,
            )
            waitForIdle()

            registry.assertPermissionsRequested(Manifest.permission.RECORD_AUDIO)
            verify { permissionRequest.deny() }
        }
    }

    private fun createPermissionManager(): PermissionManager = PermissionManager(
        serverManager = mockk<ServerManager>(relaxed = true),
        settingsDao = mockk<SettingsDao>(relaxed = true),
        hasFcmPushSupport = false,
        notificationStatusProvider = mockk(relaxed = true),
        permissionChecker = { false },
    )

    private fun AndroidComposeTestRule<ActivityScenarioRule<HiltComponentActivity>, HiltComponentActivity>.setFrontendScreen(
        viewState: FrontendViewState,
        errorStateProvider: FrontendConnectionErrorStateProvider = FrontendConnectionErrorStateProvider.noOp,
        pendingWebViewPermission: PendingWebViewPermissionRequest? = null,
        onWebViewPermissionResult: (Map<String, Boolean>) -> Unit = {},
        onBlockInsecureRetry: () -> Unit = {},
        onBlockInsecureHelpClick: suspend () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onChangeSecurityLevel: () -> Unit = {},
        onOpenLocationSettings: () -> Unit = {},
        onConfigureHomeNetwork: (serverId: Int) -> Unit = { _ -> },
        onSecurityLevelHelpClick: suspend () -> Unit = {},
        onSecurityLevelDone: () -> Unit = {},
        onNotificationPermissionResult: (Boolean) -> Unit = {},
        supportsNotificationPermission: Boolean = false,
        registry: ActivityResultRegistry? = null,
    ) {
        setContent {
            val content: @Composable () -> Unit = {
                FrontendScreenContent(
                    onBackClick = {},
                    viewState = viewState,
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    scriptsToEvaluate = emptyFlow(),
                    errorStateProvider = errorStateProvider,
                    pendingWebViewPermission = pendingWebViewPermission,
                    onWebViewPermissionResult = onWebViewPermissionResult,
                    onBlockInsecureRetry = onBlockInsecureRetry,
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = onBlockInsecureHelpClick,
                    onOpenSettings = onOpenSettings,
                    onChangeSecurityLevel = onChangeSecurityLevel,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onConfigureHomeNetwork = onConfigureHomeNetwork,
                    onSecurityLevelHelpClick = onSecurityLevelHelpClick,
                    onSecurityLevelDone = onSecurityLevelDone,
                    onNotificationPermissionResult = onNotificationPermissionResult,
                    onShowSnackbar = { _, _ -> true },
                    supportsNotificationPermission = supportsNotificationPermission,
                )
            }

            if (registry != null) {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = registry
                    },
                ) {
                    content()
                }
            } else {
                content()
            }
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
