package io.homeassistant.companion.android.frontend

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.barcode.BarcodeScannerUiState
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.matterthread.MatterThreadTerminal
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.util.compose.HAPreviews

class FrontendScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen LoadServer state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.LoadServer(serverId = 1),
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Loading state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Loading(
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen SecurityLevelRequired state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Insecure state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Insecure(
                    serverId = 1,
                    missingHomeSetup = true,
                    missingLocation = false,
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content state`() {
        HAThemeForPreview {
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
            )
        }
    }

    @SuppressLint("NewApi")
    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with notification permission prompt`() {
        HAThemeForPreview {
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
                pendingPermissionRequest = PermissionRequest.Notification(serverId = 1, onResult = {}, onDismiss = {}),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with JS confirm dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.Confirm(
                    message = "Are you sure you want to proceed?",
                    onConfirm = {},
                    onCancel = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Error`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Error(
                    serverId = 1,
                    url = "https://example.com",
                    error = FrontendConnectionError.Unreachable(
                        message = commonR.string.webview_error_HOST_LOOKUP,
                        errorDetails = "Connection timed out",
                        rawErrorType = "HostLookupError",
                    ),
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with HTTP auth dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.HttpAuth(
                    host = "example.com",
                    message = { "https://example.com requires a username and password." },
                    isAuthError = false,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with HTTP auth dialog in error state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.HttpAuth(
                    host = "example.com",
                    message = { "https://example.com requires a username and password." },
                    isAuthError = true,
                    onProceed = { _, _, _ -> },
                    onCancel = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with Matter Thread progress dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.MatterThreadProgressDialog,
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with Matter Thread no dataset dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.MatterThreadTerminalDialog(
                    terminal = MatterThreadTerminal.Dialog.ThreadNoDataset,
                    onDismiss = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content with Matter Thread not connected dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                pendingDialog = FrontendDialog.MatterThreadTerminalDialog(
                    terminal = MatterThreadTerminal.Dialog.ThreadNotConnected,
                    onDismiss = {},
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content state with custom view overlay`() {
        HAThemeForPreview {
            val context = LocalContext.current
            val view = View(context).apply { setBackgroundColor(AndroidColor.RED) }
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                customView = view,
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
                onShowSnackbar = { _, _ -> false },
                onWebViewCreationFailed = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen barcode scanner overlay`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    barcodeScanner = BarcodeScannerUiState(
                        messageId = 1,
                        title = "Scan a code",
                        description = "Point the camera at the code",
                        alternativeOptionLabel = "Enter manually",
                    ),
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
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen barcode scanner notify dialog`() {
        HAThemeForPreview {
            FrontendScreenContent(
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                    barcodeScanner = BarcodeScannerUiState(
                        messageId = 1,
                        title = "Scan a code",
                        description = "Point the camera at the code",
                        alternativeOptionLabel = null,
                    ),
                ),
                pendingDialog = FrontendDialog.Information(
                    message = "This code is already paired",
                    onDismiss = {},
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
            )
        }
    }
}
