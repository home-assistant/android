package io.homeassistant.companion.android.frontend

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.frontend.externalbus.WebViewScript
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.frontend.error.FrontendErrorScreen
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber

/**
 * Pure UI Frontend screen that renders based on the current view state.
 *
 * The WebView is always rendered at the base layer to prevent expensive recreation.
 * Loading indicators and error screens are overlaid on top. Navigation states
 * (SecurityLevelRequired, Insecure) show a loading indicator while navigation occurs.
 *
 * @param viewState Current UI state
 * @param webViewClient WebViewClient for handling page loads, errors, and JavaScript interface attachment
 * @param scriptsToEvaluate Flow of scripts to evaluate in the WebView (external bus messages, auth callbacks)
 * @param onRetry Callback when user taps retry
 * @param onOpenExternalLink Callback to open external links
 * @param modifier Modifier for the screen
 */
@Composable
internal fun FrontendScreen(
    onBackClick: () -> Unit,
    viewState: FrontendViewState,
    webViewClient: WebViewClient,
    javascriptInterface: FrontendJavascriptInterface,
    scriptsToEvaluate: Flow<WebViewScript>,
    onRetry: () -> Unit,
    onOpenExternalLink: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track WebView reference for URL loading and JS evaluation
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Load URL when it changes
    val currentUrl = viewState.url
    if (webViewRef != null) {
        LaunchedEffect(webViewRef, currentUrl) {
            Timber.v("Load url ${if (BuildConfig.DEBUG) currentUrl else ""}")
            webViewRef?.loadUrl(currentUrl)
        }
    }

    // Collect scripts to evaluate in the WebView (external bus messages, auth callbacks)
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        scriptsToEvaluate.collect { webViewScript ->
            Timber.d("Evaluating script: ${webViewScript.script}")
            webView.evaluateJavascript(webViewScript.script) { result ->
                webViewScript.result.complete(result)
            }
        }
    }

    // Extract insets-related properties from Content state
    val serverHandleInsets = (viewState as? FrontendViewState.Content)?.serverHandleInsets ?: false
    val nightModeTheme = (viewState as? FrontendViewState.Content)?.nightModeTheme
    val statusBarColor = (viewState as? FrontendViewState.Content)?.statusBarColor
    val backgroundColor = (viewState as? FrontendViewState.Content)?.backgroundColor

    Box(modifier = modifier.fillMaxSize()) {
        // Always render WebView at base layer with safe area insets handling
        SafeHAWebView(
            onBackClick = onBackClick,
            onWebViewCreated = { webViewRef = it },
            webViewClient = webViewClient,
            javascriptInterface = javascriptInterface,
            serverHandleInsets = serverHandleInsets,
            nightModeTheme = nightModeTheme,
            statusBarColor = statusBarColor,
            backgroundColor = backgroundColor,
        )

        // Overlay content based on state
        when (viewState) {
            is FrontendViewState.LoadServer,
            is FrontendViewState.Loading,
            is FrontendViewState.SecurityLevelRequired,
            is FrontendViewState.Insecure,
            -> {
                // Show loading indicator while waiting for URL or navigating to other screens
                LoadingScreen(modifier = Modifier.background(LocalHAColorScheme.current.colorSurfaceDefault))
            }

            is FrontendViewState.Content -> {
                // WebView is visible, no overlay needed
            }

            is FrontendViewState.Error -> {
                FrontendErrorScreen(
                    url = viewState.url.takeIf { it.isNotBlank() },
                    error = viewState.error,
                    onOpenExternalLink = onOpenExternalLink,
                    modifier = Modifier.fillMaxSize(),
                    actions = {
                        HAAccentButton(
                            text = stringResource(commonR.string.retry),
                            onClick = onRetry,
                            modifier = Modifier.padding(bottom = HADimens.SPACE6),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Wrapper for WebView that handles safe area insets.
 *
 * If the Home Assistant frontend does not handle edge-to-edge insets
 * (core <2025.12), it wraps the WebView with colored overlays matching
 * the safe area insets.
 */
@Composable
private fun SafeHAWebView(
    onBackClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    webViewClient: WebViewClient,
    javascriptInterface: FrontendJavascriptInterface,
    serverHandleInsets: Boolean,
    nightModeTheme: NightModeTheme?,
    statusBarColor: Color?,
    backgroundColor: Color?,
) {
    val insets = WindowInsets.safeDrawing
    val insetsPaddingValues = insets.asPaddingValues()
    val isInPreview = LocalInspectionMode.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar overlay (when server doesn't handle insets)
        if (!serverHandleInsets) {
            statusBarColor?.Overlay(
                modifier = Modifier
                    .height(insetsPaddingValues.calculateTopPadding())
                    .fillMaxWidth()
                    // We don't want the status bar to color the left and right areas
                    .padding(insets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
            )
        }

        // Main content row with left/right safe areas
        Row(modifier = Modifier.weight(1f)) {
            // Left safe area
            if (!serverHandleInsets) {
                backgroundColor?.Overlay(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(insetsPaddingValues.calculateLeftPadding(LayoutDirection.Ltr)),
                )
            }

            // In preview/screenshot mode, show a placeholder instead of WebView
            // to avoid having issue with the javascript auto attach.
            if (isInPreview) {
                Text(
                    text = "WebviewPlaceholder",
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Transparent),
                )
            } else {
                HAWebView(
                    nightModeTheme = nightModeTheme,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Transparent),
                    configure = {
                        onWebViewCreated(this)
                        // Injecting the javascript interface should happen as early as possible in the process
                        // even before loading the server URL.
                        javascriptInterface.attachToWebView(this)
                        this.webViewClient = webViewClient
                    },
                    onBackPressed = onBackClick,
                )
            }

            // Right safe area
            if (!serverHandleInsets) {
                backgroundColor?.Overlay(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(insetsPaddingValues.calculateRightPadding(LayoutDirection.Ltr)),
                )
            }
        }

        // Bottom navigation bar overlay (when server doesn't handle insets)
        if (!serverHandleInsets) {
            backgroundColor?.Overlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(insetsPaddingValues.calculateBottomPadding()),
            )
        }
    }
}

/**
 * Draws a colored overlay using this color as the background.
 */
@Composable
private fun Color.Overlay(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .background(this),
    )
}

@HAPreviews
@Composable
private fun FrontendScreenLoadingPreview() {
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

@HAPreviews
@Composable
private fun FrontendScreenErrorPreview() {
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
