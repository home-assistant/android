package io.homeassistant.companion.android.frontend

import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.flow.emptyFlow

class FrontendScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen LoadServer state`() {
        HAThemeForPreview {
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Loading state`() {
        HAThemeForPreview {
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen SecurityLevelRequired state`() {
        HAThemeForPreview {
            FrontendScreen(
                onBackClick = {},
                viewState = FrontendViewState.SecurityLevelRequired(serverId = 1),
                webViewClient = WebViewClient(),
                javascriptInterface = FrontendJavascriptInterface.noOp,
                scriptsToEvaluate = emptyFlow(),
                onRetry = {},
                onOpenExternalLink = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Insecure state`() {
        HAThemeForPreview {
            FrontendScreen(
                onBackClick = {},
                viewState = FrontendViewState.Insecure(serverId = 1),
                webViewClient = WebViewClient(),
                javascriptInterface = FrontendJavascriptInterface.noOp,
                scriptsToEvaluate = emptyFlow(),
                onRetry = {},
                onOpenExternalLink = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Content state`() {
        HAThemeForPreview {
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Error`() {
        HAThemeForPreview {
            FrontendScreen(
                onBackClick = {},
                viewState = FrontendViewState.Error(
                    serverId = 1,
                    url = "https://example.com",
                    error = FrontendError.UnreachableError(
                        message = commonR.string.webview_error_HOST_LOOKUP,
                        errorDetails = "Connection timed out",
                        rawErrorType = "HostLookupError",
                    ),
                ),
                webViewClient = WebViewClient(),
                javascriptInterface = FrontendJavascriptInterface.noOp,
                scriptsToEvaluate = emptyFlow(),
                onRetry = {},
                onOpenExternalLink = {},
            )
        }
    }
}
