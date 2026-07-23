package io.homeassistant.companion.android.frontend

import android.Manifest
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.View
import android.webkit.PermissionRequest as WebViewPermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.zxing.BarcodeFormat
import com.wifi.improv.ErrorState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.common.data.prefs.ScreenOrientation
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.frontend.barcode.BarcodeScannerUiState
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.error.ErrorActionIntent
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.error.errorActions
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.FakePermissionResultRegistry
import io.homeassistant.companion.android.util.compose.webview.HA_WEBVIEW_TAG
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
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
    fun `Given Error state then error screen with recovery action and webview are displayed`() {
        var action: ErrorActionIntent? = null
        val error = FrontendConnectionError.Unreachable(
            message = commonR.string.webview_error_HOST_LOOKUP,
            errorDetails = "Connection failed",
            rawErrorType = "HostLookupError",
        )
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Error(
                    serverId = 1,
                    url = "https://example.com",
                    error = error,
                    actions = errorActions(error, isInternalConnection = false),
                ),
                errorStateProvider = FakeConnectionErrorStateProvider(url = "https://example.com", error = error),
                onErrorAction = { action = it },
            )

            assertIsLoading(false)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.error_connection_failed)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.webview_error_HOST_LOOKUP)).assertIsDisplayed()
            // The external-connection actions refresh the external URL.
            onNodeWithText(stringResource(commonR.string.refresh_external)).performScrollTo().performClick()
            assertEquals(ErrorActionIntent.Refresh, action)
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
    fun `Given Content with null Improv state then Improv sheet is not displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
            )

            onNodeWithText(stringResource(commonR.string.improv_wifi_title)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.improv_device_provisioned)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given Content with SearchingDevice then Improv sheet shows connecting caption`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    improvUiState = ImprovUIState.SearchingDevice(deviceName = "Smart Plug"),
                ),
            )

            onNodeWithText(stringResource(commonR.string.improv_device_connecting)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given Content with ConfiguringDevice when continue clicked then onImprovConnectDevice is called with credentials`() {
        var connectArgs: Pair<String, String>? = null
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    improvUiState = ImprovUIState.ConfiguringDevice(
                        deviceName = "Smart Plug",
                        deviceAddress = "AA:BB",
                        activeSsid = "Home Wi-Fi",
                    ),
                ),
                onImprovConnectDevice = { ssid, password -> connectArgs = ssid to password },
            )

            onNodeWithText(stringResource(commonR.string.improv_wifi_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.password)).performTextInput("supersecret")
            onNodeWithText(stringResource(commonR.string.continue_connect)).performClick()
            waitForIdle()
            assertEquals("Home Wi-Fi" to "supersecret", connectArgs)
        }
    }

    @Test
    fun `Given Content with Errored when try again clicked then onImprovRestart is called`() {
        var restartCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    improvUiState = ImprovUIState.Errored(
                        deviceName = "Smart Plug",
                        deviceAddress = "AA:BB",
                        error = ErrorState.UNABLE_TO_CONNECT,
                    ),
                ),
                onImprovRestart = { restartCalled = true },
            )

            onNodeWithText(stringResource(commonR.string.improv_error_unable_to_connect)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.continue_connect)).performClick()
            waitForIdle()
            assertTrue("onImprovRestart should be called when try again is clicked", restartCalled)
        }
    }

    @Test
    fun `Given Content with Provisioned when continue clicked then onImprovDismiss is called`() {
        var dismissCalled = false
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    improvUiState = ImprovUIState.Provisioned(domain = "acme"),
                ),
                onImprovDismiss = { dismissCalled = true },
            )

            onNodeWithText(stringResource(commonR.string.improv_device_provisioned)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.continue_connect)).performClick()
            waitForIdle()
            assertTrue("onImprovDismiss should be called when continue is clicked", dismissCalled)
        }
    }

    @Test
    fun `Given no pending notification permission then notification prompt is not displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
            )

            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_title)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given notification prompt displayed when deny clicked then onPermissionResult is called with false`() {
        assertNotificationPermissionClick(
            buttonStringRes = commonR.string.notification_permission_dialog_deny,
            permissionGranted = false,
            expectedResult = false,
        )
    }

    @Test
    fun `Given notification prompt displayed when allow clicked and granted then onPermissionResult is called with true`() {
        assertNotificationPermissionClick(
            buttonStringRes = commonR.string.notification_permission_dialog_allow,
            permissionGranted = true,
            expectedResult = true,
        )
    }

    @Test
    fun `Given notification prompt displayed when allow clicked and denied then onPermissionResult is called with false`() {
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
        var persistedResult: Boolean? = null

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                pendingPermissionRequest = PermissionRequest.Notification(
                    serverId = 1,
                    onResult = { granted -> persistedResult = granted },
                    onDismiss = {},
                ),
                registry = registry,
            )

            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_allow)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.notification_permission_dialog_deny)).assertIsDisplayed()
            onNodeWithText(stringResource(buttonStringRes)).performClick()

            waitForIdle()
            assertTrue(
                "persistResult should be called with $expectedResult but was $persistedResult",
                persistedResult == expectedResult,
            )
        }
    }

    @Test
    fun `Given WebView requests camera when permission not granted then system permission dialog is launched for CAMERA`() = runTest {
        val registry = FakePermissionResultRegistry(grantedPermissions = setOf(Manifest.permission.CAMERA))
        val permissionRequest: WebViewPermissionRequest = mockk(relaxed = true) {
            every { resources } returns arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)
        }
        val permissionManager = createPermissionManager()
        // onWebViewPermissionRequest now suspends until the user responds via pending.onResult,
        // so we launch it; the registry below will resume it with the simulated grant result.
        val managerJob = launch { permissionManager.onWebViewPermissionRequest(permissionRequest) }
        advanceUntilIdle()

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                pendingPermissionRequest = permissionManager.pendingPermissionRequest.value,
                registry = registry,
            )
            waitForIdle()

            registry.assertPermissionsRequested(Manifest.permission.CAMERA)
        }
        // Wait for the manager's post-result logic (grant/deny on the WebView request) to run
        // before asserting on the mocked WebView request.
        managerJob.join()
        verify { permissionRequest.grant(arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
    }

    @Test
    fun `Given WebView requests mic when permission denied then WebView request is denied`() = runTest {
        val registry = FakePermissionResultRegistry(grantedPermissions = emptySet())
        val permissionRequest: WebViewPermissionRequest = mockk(relaxed = true) {
            every { resources } returns arrayOf(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE)
        }
        val permissionManager = createPermissionManager()
        val managerJob = launch { permissionManager.onWebViewPermissionRequest(permissionRequest) }
        advanceUntilIdle()

        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                pendingPermissionRequest = permissionManager.pendingPermissionRequest.value,
                registry = registry,
            )
            waitForIdle()

            registry.assertPermissionsRequested(Manifest.permission.RECORD_AUDIO)
        }
        managerJob.join()
        verify { permissionRequest.deny() }
    }

    private fun createPermissionManager(): PermissionManager = PermissionManager(
        serverManager = mockk<ServerManager>(relaxed = true),
        settingsDao = mockk<SettingsDao>(relaxed = true),
        fcmSupport = false,
        notificationStatusProvider = mockk(relaxed = true),
        permissionChecker = { false },
        checkLocalNetworkPermissionUseCase = mockk(relaxed = true),
        prefsRepository = mockk(relaxed = true),
    )

    private fun AndroidComposeTestRule<ActivityScenarioRule<HiltComponentActivity>, HiltComponentActivity>.setFrontendScreen(
        viewState: FrontendViewState,
        errorStateProvider: FrontendConnectionErrorStateProvider = FrontendConnectionErrorStateProvider.noOp,
        pendingPermissionRequest: PermissionRequest? = null,
        onBlockInsecureRetry: () -> Unit = {},
        onErrorAction: (ErrorActionIntent) -> Unit = {},
        onBlockInsecureHelpClick: suspend () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onChangeSecurityLevel: () -> Unit = {},
        onOpenLocationSettings: () -> Unit = {},
        onConfigureHomeNetwork: (serverId: Int) -> Unit = { _ -> },
        onSecurityLevelHelpClick: suspend () -> Unit = {},
        onSecurityLevelDone: () -> Unit = {},
        onImprovConnectDevice: (ssid: String, password: String) -> Unit = { _, _ -> },
        onImprovRestart: () -> Unit = {},
        onImprovDismiss: () -> Unit = {},
        registry: ActivityResultRegistry? = null,
    ) {
        setContent {
            val content: @Composable () -> Unit = {
                FrontendScreenContent(
                    viewState = viewState,
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    errorStateProvider = errorStateProvider,
                    pendingPermissionRequest = pendingPermissionRequest,
                    onBlockInsecureRetry = onBlockInsecureRetry,
                    onErrorAction = onErrorAction,
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = onBlockInsecureHelpClick,
                    onOpenSettings = onOpenSettings,
                    onChangeSecurityLevel = onChangeSecurityLevel,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onConfigureHomeNetwork = onConfigureHomeNetwork,
                    onSecurityLevelHelpClick = onSecurityLevelHelpClick,
                    onSecurityLevelDone = onSecurityLevelDone,
                    onShowSnackbar = { _, _ -> true },
                    onWebViewCreationFailed = {},
                    onImprovConnectDevice = onImprovConnectDevice,
                    onImprovRestart = onImprovRestart,
                    onImprovDismiss = onImprovDismiss,
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

    /**
     * Renders [FrontendScreenContent] in a [Content][FrontendViewState.Content] state carrying the
     * given [barcodeScanner]. Wrapped in [LocalInspectionMode] so the scanner skips the real camera
     * (and Accompanist reports the permission as granted), letting the overlay chrome render.
     */
    private fun AndroidComposeTestRule<ActivityScenarioRule<HiltComponentActivity>, HiltComponentActivity>.setBarcodeOverlay(
        barcodeScanner: BarcodeScannerUiState,
        pendingDialog: FrontendDialog? = null,
        onBarcodeScanned: (rawValue: String, format: BarcodeFormat) -> Unit = { _, _ -> },
        onBarcodeCancelled: (forAction: Boolean) -> Unit = {},
    ) {
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                FrontendScreenContent(
                    viewState = FrontendViewState.Content(
                        serverId = 1,
                        url = "https://example.com",
                        barcodeScanner = barcodeScanner,
                    ),
                    pendingDialog = pendingDialog,
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                    onWebViewCreationFailed = {},
                    onBarcodeScanned = onBarcodeScanned,
                    onBarcodeCancelled = onBarcodeCancelled,
                )
            }
        }
    }

    @Test
    fun `Given Content with barcodeScanner then scanner overlay is shown`() {
        composeTestRule.apply {
            setBarcodeOverlay(
                barcodeScanner = BarcodeScannerUiState(
                    messageId = 1,
                    title = "Scan a code",
                    description = "Point the camera",
                    alternativeOptionLabel = null,
                ),
            )

            onNodeWithText("Scan a code").assertIsDisplayed()
            onNodeWithText("Point the camera").assertIsDisplayed()
        }
    }

    @Test
    fun `Given barcode overlay when close icon tapped then onBarcodeCancelled false`() {
        val cancels = mutableListOf<Boolean>()
        composeTestRule.apply {
            setBarcodeOverlay(
                barcodeScanner = BarcodeScannerUiState(
                    messageId = 1,
                    title = "Scan",
                    description = "Point",
                    alternativeOptionLabel = null,
                ),
                onBarcodeCancelled = { cancels += it },
            )

            onNodeWithContentDescription(stringResource(commonR.string.cancel)).performClick()
            assertEquals(listOf(false), cancels)
        }
    }

    @Test
    fun `Given barcode overlay when back pressed then onBarcodeCancelled false`() {
        val cancels = mutableListOf<Boolean>()
        composeTestRule.apply {
            setBarcodeOverlay(
                barcodeScanner = BarcodeScannerUiState(
                    messageId = 1,
                    title = "Scan",
                    description = "Point",
                    alternativeOptionLabel = null,
                ),
                onBarcodeCancelled = { cancels += it },
            )

            runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            waitForIdle()
            assertEquals(listOf(false), cancels)
        }
    }

    @Test
    fun `Given a notification dialog over the scanner then it is shown and dismiss invokes callback`() {
        var dismissed = false
        composeTestRule.apply {
            setBarcodeOverlay(
                barcodeScanner = BarcodeScannerUiState(
                    messageId = 1,
                    title = "Scan",
                    description = "Point",
                    alternativeOptionLabel = null,
                ),
                pendingDialog = FrontendDialog.Information("Already paired", onDismiss = { dismissed = true }),
            )

            onNodeWithText("Already paired").assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.ok)).performClick()
            assertTrue("onDismiss should be called when OK tapped", dismissed)
        }
    }

    @Test
    fun `Given WebViewCreationError state then error screen with open settings button is displayed`() {
        var action: ErrorActionIntent? = null
        val error = FrontendConnectionError.Unrecoverable.WebViewCreationError(
            throwable = UnsatisfiedLinkError("dlopen failed: libwebviewchromium.so is 32-bit instead of 64-bit"),
        )
        composeTestRule.apply {
            setContent {
                FrontendScreenContent(
                    viewState = FrontendViewState.Error(
                        serverId = 1,
                        url = "https://example.com",
                        error = error,
                        actions = errorActions(error, isInternalConnection = false),
                    ),
                    errorStateProvider = FakeConnectionErrorStateProvider(
                        url = "https://example.com",
                        error = error,
                    ),
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    onBlockInsecureRetry = {},
                    onErrorAction = { action = it },
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                    onWebViewCreationFailed = {},
                )
            }

            assertIsLoading(false)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()

            // Error title should be displayed
            onNodeWithText(stringResource(commonR.string.webview_creation_error_title)).assertIsDisplayed()
            // Error message should be displayed
            onNodeWithText(stringResource(commonR.string.webview_creation_failed)).assertIsDisplayed()
            // No refresh action for an unrecoverable error
            onNodeWithText(stringResource(commonR.string.refresh_external)).assertDoesNotExist()
            onNodeWithText(stringResource(commonR.string.refresh_internal)).assertDoesNotExist()
            // Open Settings action should be displayed and dispatch GoToSettings
            onNodeWithText(stringResource(commonR.string.open_settings)).performScrollTo().performClick()
            assertEquals(ErrorActionIntent.GoToSettings, action)
        }
    }

    @Test
    fun `Given Content state with customView then overlay is displayed`() {
        composeTestRule.setContent {
            val context = LocalContext.current
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                customView = View(context),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
            )
        }

        composeTestRule.onNodeWithTag(CUSTOM_VIEW_OVERLAY_TAG).assertIsDisplayed()
    }

    @Test
    fun `Given Content state without customView then overlay is not displayed`() {
        composeTestRule.apply {
            setFrontendScreen(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
            )
            onNodeWithTag(CUSTOM_VIEW_OVERLAY_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `Given Content with customView when reporter runs then PipReadiness is published with default aspect`() {
        val captured = mutableListOf<PipReadiness?>()

        composeTestRule.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                customView = View(context),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                onPipReadinessChanged = { captured += it },
            )
        }

        composeTestRule.runOnIdle {
            assertEquals(Rational(16, 9), captured.lastOrNull()?.aspectRatio)
        }
    }

    @Test
    fun `Given no customView and no fullscreen player when reporter runs then PipReadiness is null`() {
        val captured = mutableListOf<PipReadiness?>()

        composeTestRule.setContent {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                onPipReadinessChanged = { captured += it },
            )
        }

        composeTestRule.runOnIdle {
            assertNull(captured.lastOrNull())
        }
    }

    @Test
    fun `Given screenOrientation toggles at runtime then activity requestedOrientation follows`() {
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val orientationState = mutableStateOf(ScreenOrientation.SYSTEM)
        composeTestRule.setContent {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                screenOrientation = orientationState.value,
            )
        }

        composeTestRule.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, composeTestRule.activity.requestedOrientation)
        }

        orientationState.value = ScreenOrientation.PORTRAIT
        composeTestRule.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)
        }

        orientationState.value = ScreenOrientation.LANDSCAPE
        composeTestRule.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, composeTestRule.activity.requestedOrientation)
        }

        orientationState.value = ScreenOrientation.SYSTEM
        composeTestRule.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, composeTestRule.activity.requestedOrientation)
        }
    }

    @Test
    fun `Given screenOrientation is PORTRAIT when content leaves composition then previous orientation is restored`() {
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val visible = mutableStateOf(true)
        composeTestRule.setContent {
            if (visible.value) {
                FrontendScreenContent(
                    viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                    onWebViewCreationFailed = {},
                    screenOrientation = ScreenOrientation.PORTRAIT,
                )
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)
        }

        visible.value = false
        composeTestRule.runOnIdle {
            assertEquals(
                "requestedOrientation should be restored once the frontend leaves composition",
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                composeTestRule.activity.requestedOrientation,
            )
        }
    }

    @Test
    fun `Given keepScreenOnEnabled is true then host view keepScreenOn is set`() {
        var capturedView: View? = null
        composeTestRule.setContent {
            capturedView = LocalView.current
            FrontendScreenContent(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                keepScreenOnEnabled = true,
            )
        }

        composeTestRule.runOnIdle {
            assertTrue("host view should keep the screen on when preference is enabled", capturedView!!.keepScreenOn)
        }
    }

    @Test
    fun `Given keepScreenOnEnabled is false then host view keepScreenOn is cleared`() {
        var capturedView: View? = null
        composeTestRule.setContent {
            capturedView = LocalView.current
            FrontendScreenContent(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                keepScreenOnEnabled = false,
            )
        }

        composeTestRule.runOnIdle {
            assertFalse(
                "host view should not keep the screen on when preference is disabled",
                capturedView!!.keepScreenOn,
            )
        }
    }

    @Test
    fun `Given keepScreenOnEnabled toggles at runtime then host view keepScreenOn follows`() {
        val enabledState = mutableStateOf(false)
        var capturedView: View? = null
        composeTestRule.setContent {
            capturedView = LocalView.current
            FrontendScreenContent(
                viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
                frontendJsCallback = FrontendJsBridge.noOp,
                onBlockInsecureRetry = {},
                onOpenExternalLink = {},
                onBlockInsecureHelpClick = {},
                onOpenSettings = {},
                onChangeSecurityLevel = {},
                onOpenLocationSettings = {},
                onConfigureHomeNetwork = { _ -> },
                onSecurityLevelHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                onWebViewCreationFailed = {},
                keepScreenOnEnabled = enabledState.value,
            )
        }

        composeTestRule.runOnIdle { assertFalse(capturedView!!.keepScreenOn) }

        enabledState.value = true
        composeTestRule.runOnIdle { assertTrue(capturedView!!.keepScreenOn) }

        enabledState.value = false
        composeTestRule.runOnIdle { assertFalse(capturedView!!.keepScreenOn) }
    }

    @Test
    fun `Given keepScreenOnEnabled is true when content leaves composition then keepScreenOn is cleared`() {
        val visible = mutableStateOf(true)
        var capturedView: View? = null
        composeTestRule.setContent {
            capturedView = LocalView.current
            if (visible.value) {
                FrontendScreenContent(
                    viewState = FrontendViewState.Content(serverId = 1, url = "https://example.com"),
                    webViewClient = WebViewClient(),
                    webChromeClient = WebChromeClient(),
                    frontendJsCallback = FrontendJsBridge.noOp,
                    onBlockInsecureRetry = {},
                    onOpenExternalLink = {},
                    onBlockInsecureHelpClick = {},
                    onOpenSettings = {},
                    onChangeSecurityLevel = {},
                    onOpenLocationSettings = {},
                    onConfigureHomeNetwork = { _ -> },
                    onSecurityLevelHelpClick = {},
                    onShowSnackbar = { _, _ -> true },
                    onWebViewCreationFailed = {},
                    keepScreenOnEnabled = true,
                )
            }
        }

        composeTestRule.runOnIdle { assertTrue(capturedView!!.keepScreenOn) }

        visible.value = false
        composeTestRule.runOnIdle {
            assertFalse(
                "keepScreenOn should be cleared once the frontend leaves composition",
                capturedView!!.keepScreenOn,
            )
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
