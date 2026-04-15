package io.homeassistant.companion.android.frontend

import android.annotation.SuppressLint
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorScreen
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.js.FrontendJsCallback
import io.homeassistant.companion.android.frontend.permissions.MultiplePermissionsEffect
import io.homeassistant.companion.android.frontend.permissions.NotificationPermissionPrompt
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.frontend.permissions.SinglePermissionEffect
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModel
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModelFactory
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import io.homeassistant.companion.android.util.sensitive
import io.homeassistant.companion.android.webview.insecure.BlockInsecureScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Minimum swipe velocity (pixels/second) to trigger a gesture action. */
private const val MINIMUM_GESTURE_VELOCITY = 75f

/**
 * Frontend screen that renders based on the ViewModel's current view state.
 *
 * The WebView is always rendered at the base layer to prevent it to not load the URL.
 * Loading indicators, error screens, and blocking screens are overlaid on top.
 *
 * @param onBackClick Callback when user navigates back
 * @param viewModel The ViewModel providing state and handling actions
 * @param onOpenExternalLink Callback to open external links
 * @param onBlockInsecureHelpClick Callback when user taps help on the insecure screen
 * @param onOpenSettings Callback to open app settings
 * @param onOpenLocationSettings Callback to open location settings
 * @param onConfigureHomeNetwork Callback to configure home network (receives serverId)
 * @param onSecurityLevelHelpClick Callback when user taps help on security level screen
 * @param onShowSnackbar Callback to show snackbar messages
 * @param modifier Modifier for the screen
 */
@Composable
internal fun FrontendScreen(
    onBackClick: () -> Unit,
    viewModel: FrontendViewModel,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onBlockInsecureHelpClick: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
    onSecurityLevelHelpClick: suspend () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val pendingPermissionRequest by viewModel.pendingPermissionRequest.collectAsStateWithLifecycle()

    // Create SecurityLevel ViewModel only when needed
    val securityLevelViewModel: LocationForSecureConnectionViewModel? =
        if (viewState is FrontendViewState.SecurityLevelRequired) {
            hiltViewModel<LocationForSecureConnectionViewModel, LocationForSecureConnectionViewModelFactory>(
                creationCallback = { factory -> factory.create(viewState.serverId) },
            )
        } else {
            null
        }

    FrontendScreenContent(
        onBackClick = onBackClick,
        viewState = viewState,
        errorStateProvider = viewModel as FrontendConnectionErrorStateProvider,
        webViewClient = viewModel.webViewClient,
        webChromeClient = viewModel.webChromeClient,
        frontendJsCallback = viewModel.frontendJsCallback,
        pendingPermissionRequest = pendingPermissionRequest,
        onClearPendingPermissionRequest = viewModel::clearPendingPermissionRequest,
        onBlockInsecureRetry = viewModel::onRetry,
        onOpenExternalLink = onOpenExternalLink,
        onBlockInsecureHelpClick = onBlockInsecureHelpClick,
        onOpenSettings = onOpenSettings,
        onChangeSecurityLevel = viewModel::onShowSecurityLevelScreen,
        onOpenLocationSettings = onOpenLocationSettings,
        onConfigureHomeNetwork = onConfigureHomeNetwork,
        securityLevelViewModel = securityLevelViewModel,
        onSecurityLevelDone = viewModel::onSecurityLevelDone,
        onSecurityLevelHelpClick = onSecurityLevelHelpClick,
        onShowSnackbar = onShowSnackbar,
        onWebViewCreationFailed = viewModel::onWebViewCreationFailed,
        onDownloadRequested = viewModel::onDownloadRequested,
        webViewActions = viewModel.webViewActions,
        onGesture = viewModel::onGesture,
        modifier = modifier,
    )
}

@Composable
internal fun FrontendScreenContent(
    onBackClick: () -> Unit,
    viewState: FrontendViewState,
    webViewClient: WebViewClient,
    webChromeClient: WebChromeClient,
    frontendJsCallback: FrontendJsCallback,
    onBlockInsecureRetry: () -> Unit,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onBlockInsecureHelpClick: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeSecurityLevel: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
    onSecurityLevelHelpClick: suspend () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onWebViewCreationFailed: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    pendingPermissionRequest: PermissionRequest<*>? = null,
    onClearPendingPermissionRequest: () -> Unit = {},
    errorStateProvider: FrontendConnectionErrorStateProvider = FrontendConnectionErrorStateProvider.noOp,
    securityLevelViewModel: LocationForSecureConnectionViewModel? = null,
    onSecurityLevelDone: () -> Unit = {},
    onDownloadRequested: (url: String, contentDisposition: String, mimetype: String) -> Unit = { _, _, _ -> },
    webViewActions: Flow<WebViewAction> = emptyFlow(),
    onGesture: (GestureDirection, Int) -> Unit = { _, _ -> },
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    WebViewEffects(
        webView = webView,
        url = viewState.url,
        frontendJsCallback = frontendJsCallback,
        webViewActions = webViewActions,
    )

    PendingPermissionHandler(
        pendingRequest = pendingPermissionRequest,
        onClearPendingPermissionRequest = onClearPendingPermissionRequest,
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Always render WebView at base layer
        SafeHAWebView(
            onBackClick = onBackClick,
            onWebViewCreated = { webView = it },
            webViewClient = webViewClient,
            webChromeClient = webChromeClient,
            contentState = viewState as? FrontendViewState.Content,
            onWebViewCreationFailed = onWebViewCreationFailed,
            onDownloadRequested = onDownloadRequested,
            onGesture = onGesture,
        )

        StateOverlay(
            viewState = viewState,
            errorStateProvider = errorStateProvider,
            securityLevelViewModel = securityLevelViewModel,
            onSecurityLevelDone = onSecurityLevelDone,
            onSecurityLevelHelpClick = onSecurityLevelHelpClick,
            onRetry = onBlockInsecureRetry,
            onInsecureHelpClick = onBlockInsecureHelpClick,
            onOpenSettings = onOpenSettings,
            onChangeSecurityLevel = onChangeSecurityLevel,
            onOpenLocationSettings = onOpenLocationSettings,
            onConfigureHomeNetwork = onConfigureHomeNetwork,
            onOpenExternalLink = onOpenExternalLink,
            onShowSnackbar = onShowSnackbar,
        )
    }
}

/**
 * Renders the appropriate overlay based on the current view state.
 */
@Composable
private fun StateOverlay(
    viewState: FrontendViewState,
    errorStateProvider: FrontendConnectionErrorStateProvider,
    securityLevelViewModel: LocationForSecureConnectionViewModel?,
    onSecurityLevelDone: () -> Unit,
    onSecurityLevelHelpClick: suspend () -> Unit,
    onRetry: () -> Unit,
    onInsecureHelpClick: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeSecurityLevel: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    when (viewState) {
        is FrontendViewState.LoadServer,
        is FrontendViewState.Loading,
        -> LoadingScreen(modifier = Modifier.background(LocalHAColorScheme.current.colorSurfaceDefault))

        is FrontendViewState.Content -> {
            // No overlay for content state to show the underlying WebView
        }

        is FrontendViewState.SecurityLevelRequired -> SecurityLevelOverlay(
            viewModel = securityLevelViewModel,
            onSecurityLevelDone = onSecurityLevelDone,
            onHelpClick = onSecurityLevelHelpClick,
            onShowSnackbar = onShowSnackbar,
        )

        is FrontendViewState.Insecure -> InsecureOverlay(
            viewState = viewState,
            onRetry = onRetry,
            onHelpClick = onInsecureHelpClick,
            onOpenSettings = onOpenSettings,
            onChangeSecurityLevel = onChangeSecurityLevel,
            onOpenLocationSettings = onOpenLocationSettings,
            onConfigureHomeNetwork = onConfigureHomeNetwork,
        )

        is FrontendViewState.Error -> ErrorOverlay(
            errorStateProvider = errorStateProvider,
            error = viewState.error,
            onRetry = onRetry,
            onOpenSettings = onOpenSettings,
            onOpenExternalLink = onOpenExternalLink,
        )
    }
}

/** Overlay for configuring connection security level. */
@Composable
private fun SecurityLevelOverlay(
    viewModel: LocationForSecureConnectionViewModel?,
    onSecurityLevelDone: () -> Unit,
    onHelpClick: suspend () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    val backgroundModifier = Modifier.background(LocalHAColorScheme.current.colorSurfaceDefault)
    if (viewModel != null) {
        LocationForSecureConnectionScreen(
            viewModel = viewModel,
            onGoToNextScreen = { _ -> onSecurityLevelDone() },
            onHelpClick = onHelpClick,
            onShowSnackbar = onShowSnackbar,
            onCloseClick = onSecurityLevelDone,
            isStandaloneScreen = true,
            modifier = backgroundModifier,
        )
    } else {
        // Previews: use stateless version
        LocationForSecureConnectionScreen(
            initialAllowInsecureConnection = null,
            onAllowInsecureConnection = {},
            onHelpClick = onHelpClick,
            onShowSnackbar = onShowSnackbar,
            onCloseClick = onSecurityLevelDone,
            isStandaloneScreen = true,
            modifier = backgroundModifier,
        )
    }
}

/** Overlay shown when insecure connection is blocked. */
@Composable
private fun InsecureOverlay(
    viewState: FrontendViewState.Insecure,
    onRetry: () -> Unit,
    onHelpClick: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeSecurityLevel: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
) {
    BlockInsecureScreen(
        missingHomeSetup = viewState.missingHomeSetup,
        missingLocation = viewState.missingLocation,
        onRetry = onRetry,
        onHelpClick = onHelpClick,
        onOpenSettings = onOpenSettings,
        onChangeSecurityLevel = onChangeSecurityLevel,
        onOpenLocationSettings = onOpenLocationSettings,
        onConfigureHomeNetwork = { onConfigureHomeNetwork(viewState.serverId) },
        modifier = Modifier.background(LocalHAColorScheme.current.colorSurfaceDefault),
    )
}

/** Overlay shown when a connection error occurs. */
@Composable
private fun ErrorOverlay(
    errorStateProvider: FrontendConnectionErrorStateProvider,
    error: FrontendConnectionError?,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExternalLink: suspend (Uri) -> Unit,
) {
    FrontendConnectionErrorScreen(
        stateProvider = errorStateProvider,
        onOpenExternalLink = onOpenExternalLink,
        modifier = Modifier.fillMaxSize(),
        actions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                if (error !is FrontendConnectionError.UnrecoverableError) {
                    HAAccentButton(
                        text = stringResource(commonR.string.retry),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HAPlainButton(
                    text = stringResource(commonR.string.open_settings),
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HADimens.SPACE6),
                )
            }
        },
    )
}

/**
 * Wrapper for WebView that handles safe area insets.
 */
@Composable
private fun SafeHAWebView(
    onBackClick: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    webViewClient: WebViewClient,
    contentState: FrontendViewState.Content?,
    onWebViewCreationFailed: (Throwable) -> Unit,
    webChromeClient: WebChromeClient? = null,
    onDownloadRequested: (url: String, contentDisposition: String, mimetype: String) -> Unit = { _, _, _ -> },
    onGesture: (GestureDirection, Int) -> Unit = { _, _ -> },
) {
    val serverHandleInsets = contentState?.serverHandleInsets ?: false
    val backgroundColor = contentState?.backgroundColor
    val insets = WindowInsets.safeDrawing
    val insetsPaddingValues = insets.asPaddingValues()

    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar overlay (when server doesn't handle insets)
        if (!serverHandleInsets) {
            contentState?.statusBarColor?.Overlay(
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

            HAWebView(
                nightModeTheme = contentState?.nightModeTheme,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent),
                configure = {
                    onWebViewCreated(this)
                    this.webViewClient = webViewClient
                    webChromeClient?.let { this.webChromeClient = it }
                    setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                        onDownloadRequested(url, contentDisposition, mimetype)
                    }
                    setOnTouchListener(
                        object : OnSwipeListener(context) {
                            override fun onSwipe(
                                e1: MotionEvent,
                                e2: MotionEvent,
                                velocity: Float,
                                direction: GestureDirection,
                                pointerCount: Int,
                            ): Boolean {
                                if (pointerCount > 1 && velocity >= MINIMUM_GESTURE_VELOCITY) {
                                    onGesture(direction, pointerCount)
                                }
                                return false
                            }

                            override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean = false
                        },
                    )
                },
                onBackPressed = onBackClick,
                onWebViewCreationFailed = onWebViewCreationFailed,
            )

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

/** Draws a colored spacer overlay using this color as the background. */
@Composable
private fun Color.Overlay(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.background(this))
}

/**
 * Routes a [PermissionRequest] to the appropriate UI and delivers results directly.
 *
 * When the user responds, this composable:
 * 1. Clears the pending slot via [onClearPendingPermissionRequest] (unblocking queued requests)
 * 2. Calls [PermissionRequest.onResult] directly using a composable coroutine scope
 *
 * Types with custom UI (e.g. [PermissionRequest.Notification] bottom sheet) are matched first.
 * Remaining types fall through to the system dialog based on their category:
 * [PermissionRequest.MultiplePermissions] or [PermissionRequest.SinglePermission].
 *
 * Adding a new permission type that uses the system dialog requires no changes here.
 */
@Composable
private fun PendingPermissionHandler(
    pendingRequest: PermissionRequest<*>?,
    onClearPendingPermissionRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    fun <T : PermissionRequest.Result> resolveResult(request: PermissionRequest<T>, result: T) {
        onClearPendingPermissionRequest()
        coroutineScope.launch { request.onResult(result) }
    }

    when (pendingRequest) {
        // Types with custom UI
        is PermissionRequest.Notification -> {
            @SuppressLint("InlinedApi")
            NotificationPermissionPrompt(
                onPermissionResult = { granted ->
                    resolveResult(pendingRequest, PermissionRequest.Result.Single(granted = granted))
                },
                onDismiss = onClearPendingPermissionRequest,
            )
        }
        // Default system dialogs by category
        is PermissionRequest.MultiplePermissions -> {
            MultiplePermissionsEffect(
                pendingRequest = pendingRequest,
                onPermissionResult = { permissions ->
                    resolveResult(pendingRequest, PermissionRequest.Result.Multiple(permissions = permissions))
                },
            )
        }
        is PermissionRequest.SinglePermission -> {
            SinglePermissionEffect(
                pendingRequest = pendingRequest,
                onPermissionResult = { granted ->
                    resolveResult(pendingRequest, PermissionRequest.Result.Single(granted = granted))
                },
            )
        }
        null -> { /* No pending permission */ }
    }
}

/**
 * Handles WebView side effects: URL loading and [WebViewAction] dispatch.
 */
@Composable
private fun WebViewEffects(
    webView: WebView?,
    url: String,
    frontendJsCallback: FrontendJsCallback,
    webViewActions: Flow<WebViewAction>,
) {
    if (webView != null) {
        LaunchedEffect(webView, url) {
            frontendJsCallback.attachToWebView(webView)
            Timber.v("Load url ${sensitive(url)}")
            webView.loadUrl(url)
        }
        LaunchedEffect(webView) {
            webViewActions.collect { action ->
                action.run(webView)
            }
        }
    }
}

@HAPreviews
@Composable
private fun FrontendScreenLoadingPreview() {
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

@HAPreviews
@Composable
private fun FrontendScreenErrorPreview() {
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

@HAPreviews
@Composable
private fun FrontendScreenInsecurePreview() {
    HAThemeForPreview {
        FrontendScreenContent(
            onBackClick = {},
            viewState = FrontendViewState.Insecure(
                serverId = 1,
                missingHomeSetup = true,
                missingLocation = true,
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

@HAPreviews
@Composable
private fun FrontendScreenSecurityLevelRequiredPreview() {
    HAThemeForPreview {
        FrontendScreenContent(
            onBackClick = {},
            viewState = FrontendViewState.SecurityLevelRequired(
                serverId = 1,
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
