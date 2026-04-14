package io.homeassistant.companion.android.frontend

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.flow.emptyFlow

class FrontendScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen LoadServer state`() {
        HAThemeForPreview {
            FrontendScreenContent(
                onBackClick = {},
                viewState = FrontendViewState.LoadServer(serverId = 1),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onBackClick = {},
                viewState = FrontendViewState.Loading(
                    serverId = 1,
                    url = "https://example.com",
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onBackClick = {},
                viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onBackClick = {},
                viewState = FrontendViewState.Insecure(
                    serverId = 1,
                    missingHomeSetup = true,
                    missingLocation = false,
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onBackClick = {},
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onBackClick = {},
                viewState = FrontendViewState.Content(
                    serverId = 1,
                    url = "https://example.com",
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onWebViewCreationFailed = {},
                pendingPermissionRequest = PermissionRequest.Notification(1) {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Error`() {
        HAThemeForPreview {
            FrontendScreenContent(
                onBackClick = {},
                viewState = FrontendViewState.Error(
                    serverId = 1,
                    url = "https://example.com",
                    error = FrontendConnectionError.UnreachableError(
                        message = commonR.string.webview_error_HOST_LOOKUP,
                        errorDetails = "Connection timed out",
                        rawErrorType = "HostLookupError",
                    ),
                ),
                webViewClient = WebViewClient(),
                webChromeClient = WebChromeClient(),
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
                onWebViewCreationFailed = {},
            )
        }
    }
}
