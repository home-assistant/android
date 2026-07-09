package io.homeassistant.companion.android.frontend

import android.annotation.SuppressLint
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.core.util.TypedValueCompat.pxToDp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.prefs.ScreenOrientation
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.frontend.WebViewAction.ApplySafeAreaInsets.Companion.SafeAreaInsets
import io.homeassistant.companion.android.frontend.barcode.BarcodeScannerUiState
import io.homeassistant.companion.android.frontend.barcode.ui.BarcodeScanner
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.dialog.PendingDialogHandler
import io.homeassistant.companion.android.frontend.error.ErrorAction
import io.homeassistant.companion.android.frontend.error.ErrorActionIntent
import io.homeassistant.companion.android.frontend.error.ErrorActions
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorScreen
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.filechooser.FileChooserEffect
import io.homeassistant.companion.android.frontend.filechooser.FileChooserRequest
import io.homeassistant.companion.android.frontend.improv.ui.ImprovOverlay
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.js.FrontendJsCallback
import io.homeassistant.companion.android.frontend.permissions.PendingPermissionHandler
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModel
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModelFactory
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import io.homeassistant.companion.android.util.sensitive
import io.homeassistant.companion.android.webview.insecure.BlockInsecureScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber

/** Minimum swipe velocity (pixels/second) to trigger a gesture action. */
private const val MINIMUM_GESTURE_VELOCITY = 75f

/** Test tag applied to the WebView custom view fullscreen overlay. */
@VisibleForTesting
internal const val CUSTOM_VIEW_OVERLAY_TAG = "custom_view_overlay"

/**
 * Frontend screen that renders based on the ViewModel's current view state.
 *
 * The WebView is always rendered at the base layer to prevent it to not load the URL.
 * Loading indicators, error screens, and blocking screens are overlaid on top.
 *
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
    viewModel: FrontendViewModel,
    onOpenExternalLink: suspend (Uri) -> Unit,
    onBlockInsecureHelpClick: suspend () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
    onSecurityLevelHelpClick: suspend () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
    onPipReadinessChanged: (PipReadiness?) -> Unit = {},
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val pendingPermissionRequest by viewModel.pendingPermissionRequest.collectAsStateWithLifecycle()
    val pendingDialog by viewModel.pendingDialog.collectAsStateWithLifecycle()
    val pendingFileChooser by viewModel.pendingFileChooser.collectAsStateWithLifecycle()
    val autoPlayVideoEnabled by viewModel.autoPlayVideoEnabled.collectAsStateWithLifecycle()
    val screenOrientation by viewModel.screenOrientation.collectAsStateWithLifecycle()
    val keepScreenOnEnabled by viewModel.keepScreenOnEnabled.collectAsStateWithLifecycle()
    val improvScanRequested by viewModel.improvScanRequested.collectAsStateWithLifecycle()

    // The fullscreen View handed over by the WebView is Activity-scoped. Keep it in screen
    // state so it does not leak across configuration changes via the ViewModel.
    var customView by remember { mutableStateOf<View?>(null) }
    val webChromeClient = remember(viewModel) {
        viewModel.createWebChromeClient(
            onShowCustomView = { customView = it },
            onHideCustomView = { customView = null },
        )
    }

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
        viewState = viewState,
        errorStateProvider = viewModel as FrontendConnectionErrorStateProvider,
        webViewClient = viewModel.webViewClient,
        webChromeClient = webChromeClient,
        customView = customView,
        frontendJsCallback = viewModel.frontendJsCallback,
        pendingPermissionRequest = pendingPermissionRequest,
        pendingDialog = pendingDialog,
        pendingFileChooser = pendingFileChooser,
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
        onErrorAction = viewModel::onErrorAction,
        onDownloadRequested = viewModel::onDownloadRequested,
        webViewActions = viewModel.webViewActions,
        onSafeAreaInsetsChanged = viewModel::onSafeAreaInsetsChanged,
        onGesture = viewModel::onGesture,
        onLeavingApp = viewModel::onLeavingApp,
        onExoPlayerFullscreenChanged = viewModel::onExoPlayerFullscreenChanged,
        onImprovConnectDevice = viewModel::onImprovConnectDevice,
        onImprovRestart = viewModel::onImprovRestart,
        onImprovDismiss = viewModel::onImprovSheetDismissed,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        onBarcodeCancelled = viewModel::onBarcodeCancelled,
        autoPlayVideoEnabled = autoPlayVideoEnabled,
        screenOrientation = screenOrientation,
        keepScreenOnEnabled = keepScreenOnEnabled,
        onPipReadinessChanged = onPipReadinessChanged,
        improvScanRequested = improvScanRequested,
        processImprovScanRequests = viewModel::processImprovScanRequests,
        modifier = modifier,
    )
}

@Composable
internal fun FrontendScreenContent(
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
    onErrorAction: (ErrorActionIntent) -> Unit = {},
    customView: View? = null,
    autoPlayVideoEnabled: Boolean = false,
    screenOrientation: ScreenOrientation = ScreenOrientation.SYSTEM,
    keepScreenOnEnabled: Boolean = false,
    pendingPermissionRequest: PermissionRequest? = null,
    pendingDialog: FrontendDialog? = null,
    pendingFileChooser: FileChooserRequest? = null,
    errorStateProvider: FrontendConnectionErrorStateProvider = FrontendConnectionErrorStateProvider.noOp,
    securityLevelViewModel: LocationForSecureConnectionViewModel? = null,
    onSecurityLevelDone: () -> Unit = {},
    onDownloadRequested: (url: String, contentDisposition: String, mimetype: String) -> Unit = { _, _, _ -> },
    webViewActions: Flow<WebViewAction> = emptyFlow(),
    onSafeAreaInsetsChanged: (SafeAreaInsets) -> Unit = {},
    onGesture: (GestureDirection, Int) -> Unit = { _, _ -> },
    onLeavingApp: (String?) -> Unit = {},
    onExoPlayerFullscreenChanged: (Boolean) -> Unit = {},
    onBarcodeScanned: (rawValue: String, format: BarcodeFormat) -> Unit = { _, _ -> },
    onBarcodeCancelled: (forAction: Boolean) -> Unit = {},
    onPipReadinessChanged: (PipReadiness?) -> Unit = {},
    onImprovConnectDevice: (ssid: String, password: String) -> Unit = { _, _ -> },
    onImprovRestart: () -> Unit = {},
    onImprovDismiss: () -> Unit = {},
    improvScanRequested: Boolean = false,
    processImprovScanRequests: suspend () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val content = (viewState as? FrontendViewState.Content)

    // Until the frontend theme is read, fall back to the loading surface color (shown behind the
    // transparent system bars) so their icons stay legible, mirroring the launch screen.
    val loadingSurfaceColor = LocalHAColorScheme.current.colorSurfaceDefault

    // Consume back only while the dashboard (Content) is shown and the WebView has history to pop.
    BackHandler(enabled = content?.canGoBack == true) { webView?.goBack() }

    FrontendScreenEffects(
        webView = webView,
        url = viewState.url,
        frontendJsCallback = frontendJsCallback,
        webViewActions = webViewActions,
        pendingFileChooser = pendingFileChooser,
        autoPlayVideoEnabled = autoPlayVideoEnabled,
        improvScanRequested = improvScanRequested,
        processImprovScanRequests = processImprovScanRequests,
        screenOrientation = screenOrientation,
        keepScreenOnEnabled = keepScreenOnEnabled,
        onLeavingApp = onLeavingApp,
        statusBarColor = content?.statusBarColor ?: loadingSurfaceColor,
        navigationBarColor = content?.backgroundColor ?: loadingSurfaceColor,
        onSafeAreaInsetsChanged = onSafeAreaInsetsChanged,
    )

    FrontendScreenHandlers(pendingPermissionRequest = pendingPermissionRequest, pendingDialog = pendingDialog)

    Box(modifier = modifier.fillMaxSize()) {
        // Always render WebView at base layer
        SafeHAWebView(
            onWebViewCreated = { webView = it },
            webViewClient = webViewClient,
            webChromeClient = webChromeClient,
            contentState = viewState as? FrontendViewState.Content,
            onWebViewCreationFailed = onWebViewCreationFailed,
            onDownloadRequested = onDownloadRequested,
            onGesture = onGesture,
            autoPlayVideoEnabled = autoPlayVideoEnabled,
        )

        PipEligibleOverlays(
            contentState = viewState as? FrontendViewState.Content,
            customView = customView,
            onExoPlayerFullscreenChanged = onExoPlayerFullscreenChanged,
            onPipReadinessChanged = onPipReadinessChanged,
        )

        ImprovOverlay(
            state = (viewState as? FrontendViewState.Content)?.improvUiState,
            onConnectDevice = onImprovConnectDevice,
            onRestart = onImprovRestart,
            onDismiss = onImprovDismiss,
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
            onErrorAction = onErrorAction,
        )

        FrontendBarcodeOverlay(
            barcodeScanner = (viewState as? FrontendViewState.Content)?.barcodeScanner,
            onScanned = onBarcodeScanned,
            onCancelled = onBarcodeCancelled,
        )
    }
}

@Composable
private fun FrontendScreenHandlers(pendingPermissionRequest: PermissionRequest?, pendingDialog: FrontendDialog?) {
    PendingPermissionHandler(
        pendingRequest = pendingPermissionRequest,
    )

    PendingDialogHandler(
        pendingDialog = pendingDialog,
    )
}

@Composable
private fun FrontendScreenEffects(
    webView: WebView?,
    url: String,
    frontendJsCallback: FrontendJsCallback,
    webViewActions: Flow<WebViewAction>,
    pendingFileChooser: FileChooserRequest?,
    autoPlayVideoEnabled: Boolean,
    improvScanRequested: Boolean,
    processImprovScanRequests: suspend () -> Unit,
    screenOrientation: ScreenOrientation,
    keepScreenOnEnabled: Boolean,
    onLeavingApp: (String?) -> Unit,
    statusBarColor: Color?,
    navigationBarColor: Color?,
    onSafeAreaInsetsChanged: (SafeAreaInsets) -> Unit,
) {
    SystemBarsAppearanceEffect(
        statusBarColor = statusBarColor,
        navigationBarColor = navigationBarColor,
    )

    ReportSafeAreaInsetsEffect(onSafeAreaInsetsChanged = onSafeAreaInsetsChanged)

    ImprovScanLifecycleEffect(
        scanRequested = improvScanRequested,
        processImprovScanRequests = processImprovScanRequests,
    )

    WebViewEffects(
        webView = webView,
        url = url,
        frontendJsCallback = frontendJsCallback,
        webViewActions = webViewActions,
        autoPlayVideoEnabled = autoPlayVideoEnabled,
    )

    FileChooserEffect(
        pendingRequest = pendingFileChooser,
    )

    ScreenOrientationEffect(orientation = screenOrientation)

    KeepScreenOnEffect(enabled = keepScreenOnEnabled)

    LeavingAppEffect(webView = webView, onLeavingApp = onLeavingApp)

    WebViewStopLifecycleEffect(webView = webView)
}

/**
 * Freezes the WebView while the host activity is stopped (screen off or app backgrounded) and
 * resumes it when the activity starts again.
 */
@VisibleForTesting
@Composable
internal fun WebViewStopLifecycleEffect(webView: WebView?) {
    val lifecycleOwner = LocalActivity.current as? LifecycleOwner ?: return
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> webView?.apply {
                    Timber.d("Webview stopped")
                    onPause()
                    pauseTimers()
                }
                Lifecycle.Event.ON_START -> webView?.apply {
                    Timber.d("Webview started")
                    resumeTimers()
                    onResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Calls [onLeavingApp] with the WebView's current URL when the host activity stops (the app leaves
 * the foreground). Uses the activity lifecycle — not the navigation back-stack entry — so in-app
 * navigation (e.g. opening Settings) does not trigger it.
 */
@Composable
private fun LeavingAppEffect(webView: WebView?, onLeavingApp: (String?) -> Unit) {
    val lifecycleOwner = LocalActivity.current as? LifecycleOwner ?: return
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner) {
        onLeavingApp(webView?.url)
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
    onErrorAction: (ErrorActionIntent) -> Unit,
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
            actions = viewState.actions,
            onErrorAction = onErrorAction,
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
    actions: List<ErrorAction>,
    onErrorAction: (ErrorActionIntent) -> Unit,
    onOpenExternalLink: suspend (Uri) -> Unit,
) {
    FrontendConnectionErrorScreen(
        stateProvider = errorStateProvider,
        onOpenExternalLink = onOpenExternalLink,
        modifier = Modifier.fillMaxSize(),
        actions = {
            ErrorActions(
                actions = actions,
                onAction = onErrorAction,
                modifier = Modifier.padding(bottom = HADimens.SPACE6),
            )
        },
    )
}

/**
 * Wrapper for WebView that handles safe area insets.
 */
@Composable
private fun SafeHAWebView(
    onWebViewCreated: (WebView) -> Unit,
    webViewClient: WebViewClient,
    contentState: FrontendViewState.Content?,
    onWebViewCreationFailed: (Throwable) -> Unit,
    autoPlayVideoEnabled: Boolean,
    webChromeClient: WebChromeClient? = null,
    onDownloadRequested: (url: String, contentDisposition: String, mimetype: String) -> Unit = { _, _, _ -> },
    onGesture: (GestureDirection, Int) -> Unit = { _, _ -> },
) {
    val serverHandleInsets = contentState?.serverHandleInsets ?: false
    val insets = WindowInsets.safeDrawing
    val insetsPaddingValues = insets.asPaddingValues()

    // Until the frontend theme is read, fall back to the loading surface color so the reserved
    // system bar areas match the screen behind them instead of leaving the WebView to bleed through.
    val fallbackColor = LocalHAColorScheme.current.colorSurfaceDefault
    val statusBarColor = contentState?.statusBarColor ?: fallbackColor
    val backgroundColor = contentState?.backgroundColor ?: fallbackColor

    Column(modifier = Modifier.fillMaxSize()) {
        // The top is never drawn edge-to-edge: we always reserve and color the status bar strip.
        // Before considering removal, https://github.com/home-assistant/frontend/issues/29125 and
        // contrast with drawers (left/right status bar cannot be different colors) should be addressed.
        statusBarColor.Overlay(
            modifier = Modifier
                .height(insetsPaddingValues.calculateTopPadding())
                .fillMaxWidth()
                // We don't want the status bar to color the left and right areas
                .padding(insets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
        )

        // Main content row with left/right safe areas
        Row(modifier = Modifier.weight(1f)) {
            // Left safe area
            if (!serverHandleInsets) {
                backgroundColor.Overlay(
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
                    configureForFrontend(
                        webViewClient = webViewClient,
                        webChromeClient = webChromeClient,
                        onWebViewCreated = onWebViewCreated,
                        onDownloadRequested = onDownloadRequested,
                        onGesture = onGesture,
                        autoPlayVideoEnabled = autoPlayVideoEnabled,
                    )
                },
                onWebViewCreationFailed = onWebViewCreationFailed,
            )

            // Right safe area
            if (!serverHandleInsets) {
                backgroundColor.Overlay(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(insetsPaddingValues.calculateRightPadding(LayoutDirection.Ltr)),
                )
            }
        }

        // Bottom navigation bar overlay (when server doesn't handle insets)
        if (!serverHandleInsets) {
            backgroundColor.Overlay(
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
 * Applies the WebView configuration required to host the Home Assistant frontend
 *
 * The `ClickableViewAccessibility` lint is suppressed because the touch listener only observes
 * multi-pointer swipe gestures (returning `false` for every event) — clicks still flow through
 * the WebView's built-in handling, so overriding `performClick` would add no accessibility value.
 */
@SuppressLint("ClickableViewAccessibility")
private fun WebView.configureForFrontend(
    webViewClient: WebViewClient,
    webChromeClient: WebChromeClient?,
    onWebViewCreated: (WebView) -> Unit,
    onDownloadRequested: (url: String, contentDisposition: String, mimetype: String) -> Unit,
    onGesture: (GestureDirection, Int) -> Unit,
    autoPlayVideoEnabled: Boolean,
) {
    onWebViewCreated(this)

    this.webViewClient = webViewClient

    webChromeClient?.let { this.webChromeClient = it }

    settings.mediaPlaybackRequiresUserGesture = !autoPlayVideoEnabled

    // Enable first-party cookies globally and third-party cookies for this WebView.
    // The Home Assistant frontend relies on third-party cookies for some integrations
    // (e.g. embedded content served from a different origin).
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureForFrontend, true)
    }

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
}

/**
 * Handles WebView side effects: URL loading, [WebViewAction] dispatch, and reapplying the
 * "Autoplay video" preference (which requires a [WebView.reload] to take effect on the loaded page).
 */
@Composable
private fun WebViewEffects(
    webView: WebView?,
    url: String,
    frontendJsCallback: FrontendJsCallback,
    webViewActions: Flow<WebViewAction>,
    autoPlayVideoEnabled: Boolean,
) {
    if (webView != null) {
        LaunchedEffect(webView, url) {
            frontendJsCallback.attachToWebView(webView)
            Timber.v("Load url ${sensitive(url)}")
            webView.loadUrl(url)
        }
        DisposableEffect(webView) {
            onDispose {
                frontendJsCallback.detachFromWebView(webView)
            }
        }
        LaunchedEffect(webView) {
            webViewActions.collect { action ->
                action.run(webView)
            }
        }
        LaunchedEffect(autoPlayVideoEnabled, webView) {
            val target = !autoPlayVideoEnabled
            if (webView.settings.mediaPlaybackRequiresUserGesture == target) return@LaunchedEffect
            webView.settings.mediaPlaybackRequiresUserGesture = target
            webView.reload()
        }
    }
}

@Composable
private fun ImprovScanLifecycleEffect(scanRequested: Boolean, processImprovScanRequests: suspend () -> Unit) {
    if (scanRequested) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                processImprovScanRequests()
            }
        }
    }
}

/**
 * Applies the user's "Screen orientation" preference to the hosting activity's
 * `requestedOrientation` while the frontend is composed.
 *
 * On dispose the previous value is restored so leaving the dashboard (e.g. navigating to
 * settings) does not leak this preference to other screens that share the same activity.
 */
@Composable
private fun ScreenOrientationEffect(orientation: ScreenOrientation) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, orientation) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = orientation.activityInfo
        onDispose {
            activity.requestedOrientation = previous
        }
    }
}

/**
 * Toggles `View.keepScreenOn` (mapped by the platform to `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`)
 * based on the user's "Keep screen on" preference while the frontend is composed.
 *
 * The flag is always cleared on dispose so it does not leak to other screens that share the
 * hosting activity window.
 */
@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose {
            view.keepScreenOn = false
        }
    }
}

/**
 * Adjusts the system bar icon appearance (light or dark) to keep the icons legible against the
 * frontend theme colors: status bar icons contrast with [statusBarColor] and navigation bar icons
 * with [navigationBarColor].
 *
 * The previous appearance is restored on dispose so it does not leak to other screens that share
 * the hosting activity window.
 */
@Composable
private fun SystemBarsAppearanceEffect(statusBarColor: Color?, navigationBarColor: Color?) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity, view, statusBarColor, navigationBarColor) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars

        statusBarColor?.let { controller.isAppearanceLightStatusBars = it.isLight() }
        navigationBarColor?.let { controller.isAppearanceLightNavigationBars = it.isLight() }

        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}

/** Whether this color is light enough that dark foreground icons are needed for contrast. */
private fun Color.isLight(): Boolean = ColorUtils.calculateLuminance(toArgb()) >= 0.5

/**
 * Reports the device safe-area insets (system bars and display cutouts, in dp) to
 * [onSafeAreaInsetsChanged] whenever they change, so the ViewModel can forward them to the frontend
 * for edge-to-edge layout. Reading window insets is a UI concern, so only the reporting lives here.
 */
@Composable
private fun ReportSafeAreaInsetsEffect(onSafeAreaInsetsChanged: (SafeAreaInsets) -> Unit) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val density = LocalDensity.current
    val displayMetrics = LocalResources.current.displayMetrics
    val layoutDirection = LocalLayoutDirection.current

    // The app already reserves and colors the status bar strip itself (see SafeHAWebView), so the
    // frontend must not add its own top inset — otherwise the top spacing would be applied twice.
    // Kept 0 until the frontend can go edge-to-edge at the top.
    // https://github.com/home-assistant/frontend/issues/29125
    val top = 0f
    val bottom = pxToDp(insets.getBottom(density).toFloat(), displayMetrics)
    val left = pxToDp(insets.getLeft(density, layoutDirection).toFloat(), displayMetrics)
    val right = pxToDp(insets.getRight(density, layoutDirection).toFloat(), displayMetrics)

    LaunchedEffect(top, bottom, left, right) {
        onSafeAreaInsetsChanged(SafeAreaInsets(top = top, bottom = bottom, left = left, right = right))
    }
}

/**
 * Renders PiP-eligible overlays and reports their combined [PipReadiness] to the host.
 */
@Composable
private fun BoxScope.PipEligibleOverlays(
    contentState: FrontendViewState.Content?,
    customView: View?,
    onExoPlayerFullscreenChanged: (Boolean) -> Unit,
    onPipReadinessChanged: (PipReadiness?) -> Unit,
) {
    val exoState = contentState?.exoPlayerState
    val readiness = remember(customView, exoState?.isFullScreen, exoState?.videoAspectRatio) {
        PipReadiness.from(customViewShown = customView != null, exoState = exoState)
    }

    LaunchedEffect(readiness) { onPipReadinessChanged(readiness) }
    DisposableEffect(Unit) {
        onDispose { onPipReadinessChanged(null) }
    }

    ExoPlayerOverlay(
        contentState = contentState,
        onFullscreenChanged = onExoPlayerFullscreenChanged,
    )
    CustomViewOverlay(customView = customView)
}

/**
 * Renders the WebView's HTML5 fullscreen view (e.g. a fullscreen `<video>`) over the WebView.
 *
 * Back handling is intentionally not added here: the WebView owns its fullscreen lifecycle.
 */
@Composable
private fun CustomViewOverlay(customView: View?) {
    val view: View = customView ?: return
    AndroidView(
        factory = { view },
        modifier = Modifier
            .fillMaxSize()
            .testTag(CUSTOM_VIEW_OVERLAY_TAG),
    )
}

/**
 * Renders the native ExoPlayer surface over the WebView.
 *
 * Back handling is intentionally not added here: the Home Assistant frontend reacts to the back event
 * itself and closes the player by emitting an external-bus event that collapses
 * [FrontendViewState.Content.exoPlayerState] (and clears this overlay). An app-side back handler would
 * pre-empt the frontend and break that flow, so back is left to propagate to the frontend.
 */
@Composable
private fun ExoPlayerOverlay(contentState: FrontendViewState.Content?, onFullscreenChanged: (Boolean) -> Unit) {
    val exoState = contentState?.exoPlayerState ?: return
    val size = exoState.size ?: return
    HAMediaPlayer(
        player = exoState.player,
        contentScale = ContentScale.Inside,
        modifier = Modifier
            .offset(exoState.left, exoState.top)
            .size(size),
        fullscreenModifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        onFullscreenClicked = onFullscreenChanged,
    )
}

/**
 * Renders the barcode scanner over the WebView while [barcodeScanner] is non-null.
 *
 * The scanner owns the camera and its permission (see
 * [io.homeassistant.companion.android.frontend.barcode.ui.BarcodeScanner]); this overlay only adds
 * the back-press-to-cancel behaviour. The [BackHandler] is composed only while the overlay is shown,
 * so it takes priority over the WebView's own back handling and a back press cancels the scan.
 */
@Composable
private fun FrontendBarcodeOverlay(
    barcodeScanner: BarcodeScannerUiState?,
    onScanned: (rawValue: String, format: BarcodeFormat) -> Unit,
    onCancelled: (forAction: Boolean) -> Unit,
) {
    val state = barcodeScanner ?: return

    BackHandler { onCancelled(false) }

    BarcodeScanner(
        title = state.title,
        description = state.description,
        alternativeOptionLabel = state.alternativeOptionLabel,
        onResult = onScanned,
        onCancel = onCancelled,
        modifier = Modifier.fillMaxSize(),
    )
}

@HAPreviews
@Composable
private fun FrontendScreenLoadingPreview() {
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

@HAPreviews
@Composable
private fun FrontendScreenErrorPreview() {
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

@HAPreviews
@Composable
private fun FrontendScreenInsecurePreview() {
    HAThemeForPreview {
        FrontendScreenContent(
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
