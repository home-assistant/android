package io.homeassistant.companion.android.frontend

import android.net.Uri
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.prefs.ScreenOrientation
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.frontend.auth.FrontendHttpAuthHandler
import io.homeassistant.companion.android.frontend.auth.HttpAuthResult
import io.homeassistant.companion.android.frontend.barcode.FrontendBarcodeScannerHandler
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.download.DownloadResult
import io.homeassistant.companion.android.frontend.download.FrontendDownloadManager
import io.homeassistant.companion.android.frontend.error.ErrorActionIntent
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.error.errorActions
import io.homeassistant.companion.android.frontend.exoplayer.FrontendExoPlayerManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.NavigateToMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.SuccessResultMessage
import io.homeassistant.companion.android.frontend.filechooser.FileChooserManager
import io.homeassistant.companion.android.frontend.filechooser.FileChooserRequest
import io.homeassistant.companion.android.frontend.gesture.FrontendGestureManager
import io.homeassistant.companion.android.frontend.gesture.GestureResult
import io.homeassistant.companion.android.frontend.handler.FrontendBusObserver
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import io.homeassistant.companion.android.frontend.improv.FrontendImprovHandler
import io.homeassistant.companion.android.frontend.js.BridgeState
import io.homeassistant.companion.android.frontend.js.FrontendJsBridgeFactory
import io.homeassistant.companion.android.frontend.js.FrontendJsCallback
import io.homeassistant.companion.android.frontend.matterthread.FrontendMatterThreadHandler
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.frontend.url.FrontendUrlManager
import io.homeassistant.companion.android.frontend.url.UrlLoadResult
import io.homeassistant.companion.android.util.HAWebChromeClient
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import io.homeassistant.companion.android.util.LifecycleHandler
import io.homeassistant.companion.android.util.hasSameOrigin
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Maximum time to wait for the frontend to load before showing a timeout error. */
@VisibleForTesting
val CONNECTION_TIMEOUT = 10.seconds

private const val APP_PREFIX = "app://"
private const val INTENT_PREFIX = "intent:"
private const val SECURITY_ALERT_URL = "https://www.home-assistant.io/latest-security-alert/"

/**
 * URLs that must NOT trigger the "always show first view on app start" navigation.
 *
 * Matches the Home Assistant settings area — paths under `/config` (except `/config/dashboard`,
 * which is a regular dashboard) and under `/hassio` (apps).
 */
private val FIRST_VIEW_EXCLUDED_URL_REGEX =
    """.*://.*/(config/(?!\bdashboard\b)|hassio)/*.*""".toRegex()

/**
 * ViewModel for frontend screen.
 *
 * Handles loading the Home Assistant WebView, authentication, external bus communication,
 * and error handling. Implements [FrontendConnectionErrorStateProvider] to enable use of the shared error screen.
 *
 * This ViewModel acts as an orchestrator that delegates to specialized managers.
 */
@HiltViewModel
internal class FrontendViewModel @VisibleForTesting constructor(
    initialServerId: Int,
    initialTarget: FrontendTarget,
    webViewClientFactory: HAWebViewClientFactory,
    private val frontendBusObserver: FrontendBusObserver,
    private val externalBusRepository: FrontendExternalBusRepository,
    private val serverManager: ServerManager,
    private val urlManager: FrontendUrlManager,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
    private val permissionManager: PermissionManager,
    private val frontendJsBridgeFactory: FrontendJsBridgeFactory,
    private val downloadManager: FrontendDownloadManager,
    private val gestureManager: FrontendGestureManager,
    private val prefsRepository: PrefsRepository,
    private val dialogManager: FrontendDialogManager,
    private val fileChooserManager: FileChooserManager,
    private val httpAuthHandler: FrontendHttpAuthHandler,
    private val exoPlayerManager: FrontendExoPlayerManager,
    private val improvHandler: FrontendImprovHandler,
    private val barcodeScannerHandler: FrontendBarcodeScannerHandler,
    private val matterThreadHandler: FrontendMatterThreadHandler,
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        frontendBusObserver: FrontendBusObserver,
        externalBusRepository: FrontendExternalBusRepository,
        serverManager: ServerManager,
        urlManager: FrontendUrlManager,
        connectivityCheckRepository: ConnectivityCheckRepository,
        permissionManager: PermissionManager,
        frontendJsBridgeFactory: FrontendJsBridgeFactory,
        downloadManager: FrontendDownloadManager,
        gestureManager: FrontendGestureManager,
        prefsRepository: PrefsRepository,
        dialogManager: FrontendDialogManager,
        fileChooserManager: FileChooserManager,
        httpAuthHandler: FrontendHttpAuthHandler,
        exoPlayerManager: FrontendExoPlayerManager,
        improvHandler: FrontendImprovHandler,
        barcodeScannerHandler: FrontendBarcodeScannerHandler,
        matterThreadHandler: FrontendMatterThreadHandler,
        @NamedKeyChain keyChainRepository: KeyChainRepository,
    ) : this(
        initialServerId = savedStateHandle.toRoute<FrontendRoute>().serverId,
        initialTarget = savedStateHandle.toRoute<FrontendRoute>().target,
        webViewClientFactory = webViewClientFactory,
        frontendBusObserver = frontendBusObserver,
        externalBusRepository = externalBusRepository,
        serverManager = serverManager,
        urlManager = urlManager,
        connectivityCheckRepository = connectivityCheckRepository,
        permissionManager = permissionManager,
        frontendJsBridgeFactory = frontendJsBridgeFactory,
        downloadManager = downloadManager,
        gestureManager = gestureManager,
        prefsRepository = prefsRepository,
        dialogManager = dialogManager,
        fileChooserManager = fileChooserManager,
        httpAuthHandler = httpAuthHandler,
        exoPlayerManager = exoPlayerManager,
        improvHandler = improvHandler,
        barcodeScannerHandler = barcodeScannerHandler,
        matterThreadHandler = matterThreadHandler,
        keyChainRepository = keyChainRepository,
    )

    /**
     * Manages the frontend view state with protection against transitions out of unrecoverable states.
     *
     * Once a [FrontendConnectionError.Unrecoverable] is set, the current state is
     * fundamentally broken and no state transition can recover from it. All subsequent
     * [update] calls are ignored to prevent URL emissions, message results, or timeouts
     * from hiding the error screen.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class ViewStateManager(
        initialState: FrontendViewState,
        private val _state: MutableStateFlow<FrontendViewState> = MutableStateFlow(initialState),
    ) : StateFlow<FrontendViewState> by _state.asStateFlow() {

        /**
         * Updates the view state using the given [transform] function.
         *
         * If the current state is an [FrontendConnectionError] with an [FrontendConnectionError.Unrecoverable],
         * the update is silently ignored because the state cannot be recovered.
         */
        fun update(transform: (FrontendViewState) -> FrontendViewState) {
            _state.update { currentState ->
                if (currentState is FrontendViewState.Error &&
                    currentState.error is FrontendConnectionError.Unrecoverable
                ) {
                    Timber.w("Ignoring state transition: unrecoverable error present")
                    return
                }
                transform(currentState)
            }
        }
    }

    private val _viewState = ViewStateManager(
        FrontendViewState.LoadServer(
            serverId = initialServerId,
            target = initialTarget,
        ),
    )
    val viewState: StateFlow<FrontendViewState> = _viewState

    private val _connectivityCheckState = MutableStateFlow(ConnectivityCheckState())
    override val connectivityCheckState: StateFlow<ConnectivityCheckState> = _connectivityCheckState.asStateFlow()

    private val _events = MutableSharedFlow<FrontendEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FrontendEvent> = _events.asSharedFlow()

    private val _webViewActions = MutableSharedFlow<WebViewAction>(extraBufferCapacity = 1)
    val webViewActions: Flow<WebViewAction> = merge(_webViewActions, frontendBusObserver.webViewActions())

    override val urlFlow: StateFlow<String?> =
        _viewState.map { it.url }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, _viewState.value.url)

    override val errorFlow: StateFlow<FrontendConnectionError?> =
        _viewState.map { state -> (state as? FrontendViewState.Error)?.error }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, (_viewState.value as? FrontendViewState.Error)?.error)

    /**
     * JavaScript bridge for communication between the WebView and native code.
     *
     * Must be attached to the WebView via [io.homeassistant.companion.android.frontend.js.FrontendJsBridge.attachToWebView].
     */
    val frontendJsCallback: FrontendJsCallback = frontendJsBridgeFactory.create(
        scope = viewModelScope,
        stateProvider = { BridgeState(serverId = viewState.value.serverId, url = viewState.value.url) },
    )

    val webViewClient: HAWebViewClient = webViewClientFactory.create(
        currentUrlFlow = urlFlow,
        onFrontendError = ::onError,
        onCrash = ::onRetry,
        onPageFinished = ::onPageFinished,
        onUrlIntercepted = { uri, _ -> onUrlIntercepted(uri) },
        onReceivedHttpAuthRequest = { handler, host, resource, realm ->
            viewModelScope.launch {
                if (httpAuthHandler.handleAuthRequest(handler, host = host, resource = resource, realm = realm) ==
                    HttpAuthResult.Cancelled
                ) {
                    _events.tryEmit(FrontendEvent.ShowSnackbar(commonR.string.auth_cancel))
                }
            }
        },
        onCanGoBackChanged = { canGoBack ->
            // Only meaningful while the dashboard is shown; in any other state the WebView is hidden
            // behind an overlay, so the flag is dropped with the state.
            _viewState.update { state ->
                if (state is FrontendViewState.Content) state.copy(canGoBack = canGoBack) else state
            }
        },
    )

    /** The current pending file chooser request from the WebView, or null if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = fileChooserManager.pendingFileChooser

    /** The current pending permission request that needs user approval, or null if none. */
    val pendingPermissionRequest = permissionManager.pendingPermissionRequest

    /** The current pending dialog over the WebView, or null if none. */
    val pendingDialog = dialogManager.pendingDialog

    private var connectivityCheckJob: Job? = null

    /** Job tracking the urlFlow collection - cancelled when switching servers. */
    private var urlFlowJob: Job? = null

    /** Job tracking the zoom settings flow collection - restarted on each page load. */
    private var zoomObserverJob: Job? = null

    /**
     * Entity whose more-info dialog must be opened via JavaScript once the page finishes loading.
     * Set for servers older than HA 2025.6 (see [UrlLoadResult.Success.moreInfoEntityId]) and
     * cleared after it is dispatched in [onPageFinished].
     */
    private var pendingMoreInfoEntityId: String? = null

    /**
     * The user's "Autoplay video" preference.
     *
     * Lives outside [FrontendViewState] because the WebView is rendered during `Loading`,
     * `Content`, and `Error`states , and all three states need the value. Exposed as a [StateFlow]
     * so the screen can read the current value synchronously when configuring the WebView at
     * creation time (avoiding a one-shot reload once the persisted value lands) and react to
     * subsequent changes via collection.
     */
    val autoPlayVideoEnabled: StateFlow<Boolean> = flow {
        emitAll(prefsRepository.autoPlayVideoFlow())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    /**
     * The user's "Screen orientation" preference.
     *
     * Applied by the screen to the hosting activity's `requestedOrientation` so the dashboard
     * obeys the user's portrait/landscape/system preference. Exposed as a [StateFlow] so the
     * screen can read the current value synchronously when first attaching and react to changes.
     */
    val screenOrientation: StateFlow<ScreenOrientation> = flow {
        emitAll(prefsRepository.screenOrientationFlow())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = ScreenOrientation.SYSTEM)

    /**
     * The user's "Keep screen on" preference.
     *
     * Applied by the screen to the hosting window so the device does not lock while the
     * WebView is active. Exposed as a [StateFlow] so the screen can read the current value
     * synchronously when first attaching to the window and react to subsequent changes.
     */
    val keepScreenOnEnabled: StateFlow<Boolean> = flow {
        emitAll(prefsRepository.keepScreenOnFlow())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    /**
     * Whether the frontend currently wants the Improv BLE scan running. Observed by
     * [io.homeassistant.companion.android.frontend.FrontendScreen] to drive a lifecycle-bound
     * collect that keeps the scan alive while the screen is RESUMED and tears it down on
     * navigation or pause.
     */
    val improvScanRequested: StateFlow<Boolean> = improvHandler.scanRequested

    init {
        viewModelScope.launch {
            _viewState.collectLatest { state ->
                releaseExoPlayerIfLeavingContent(state)
                // Timeout watcher - cancels automatically when state changes from Loading
                watchLoadingTimeout(state)
            }
        }

        viewModelScope.launch {
            frontendBusObserver.messageResults().collect { result ->
                handleMessageResult(result)
            }
        }

        viewModelScope.launch {
            var wasFullScreen = false
            exoPlayerManager.state.collect { exoState ->
                if (wasFullScreen && exoState == null) {
                    _events.tryEmit(FrontendEvent.RequestFullscreen(fullscreen = false))
                }
                wasFullScreen = exoState?.isFullScreen == true
                _viewState.update { currentState ->
                    if (currentState is FrontendViewState.Content) {
                        currentState.copy(exoPlayerState = exoState)
                    } else {
                        currentState
                    }
                }
            }
        }

        viewModelScope.launch {
            improvHandler.uiState.collect { improvUiState ->
                _viewState.update { currentState ->
                    if (currentState is FrontendViewState.Content) {
                        currentState.copy(improvUiState = improvUiState)
                    } else {
                        currentState
                    }
                }
            }
        }

        viewModelScope.launch {
            improvHandler.events.collect { event ->
                when (event) {
                    is FrontendImprovHandler.Event.ReloadAtPath -> {
                        _viewState.update {
                            FrontendViewState.LoadServer(
                                serverId = event.serverId,
                                target = FrontendTarget.Path(event.path),
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            barcodeScannerHandler.state.collect { barcodeState ->
                _viewState.update { currentState ->
                    if (currentState is FrontendViewState.Content) {
                        currentState.copy(barcodeScanner = barcodeState)
                    } else {
                        currentState
                    }
                }
            }
        }

        collectMatterThreadEvents()

        loadServer()
    }

    override fun onCleared() {
        exoPlayerManager.close()
    }

    /**
     * Runs connectivity checks against the current server URL.
     * Results are emitted to [connectivityCheckState].
     */
    override fun runConnectivityChecks() {
        val currentUrl = _viewState.value.url
        connectivityCheckJob?.cancel()
        connectivityCheckJob = viewModelScope.launch {
            connectivityCheckRepository.runChecks(currentUrl).collect { state ->
                _connectivityCheckState.value = state
            }
        }
    }

    /**
     * Builds an [HAWebChromeClient] wired to this ViewModel for permission/JS handling, while
     * delegating WebView fullscreen view ownership to the caller.
     *
     * The fullscreen [android.view.View] handed over by `onShowCustomView` is bound to the
     * WebView's Activity context. Holding it in ViewModel state would leak that Activity across
     * configuration changes, so the caller (a Composable) keeps the View in screen-scoped state
     * and supplies setters via [onShowCustomView] and [onHideCustomView]. The ViewModel still
     * owns the system-fullscreen request and emits [FrontendEvent.RequestFullscreen] on the
     * caller's behalf.
     */
    fun createWebChromeClient(onShowCustomView: (View) -> Unit, onHideCustomView: () -> Unit): HAWebChromeClient =
        HAWebChromeClient(
            onPermissionRequest = { request ->
                viewModelScope.launch {
                    permissionManager.onWebViewPermissionRequest(request)
                }
            },
            onJsConfirm = { message, jsResult ->
                viewModelScope.launch {
                    if (dialogManager.showConfirm(message)) jsResult.confirm() else jsResult.cancel()
                }
                true
            },
            onShowFileChooser = { filePathCallback, fileChooserParams ->
                viewModelScope.launch {
                    filePathCallback.onReceiveValue(fileChooserManager.pickFiles(fileChooserParams))
                }
                true
            },
            onShowCustomView = { view ->
                onShowCustomView(view)
                _events.tryEmit(FrontendEvent.RequestFullscreen(fullscreen = true))
            },
            onHideCustomView = {
                onHideCustomView()
                _events.tryEmit(FrontendEvent.RequestFullscreen(fullscreen = false))
            },
        )

    fun onRetry() {
        _viewState.update {
            FrontendViewState.LoadServer(serverId = it.serverId)
        }
        loadServer()
    }

    /**
     * Handles a tap on a recovery action of the connection-error screen (see `errorActions`).
     */
    fun onErrorAction(intent: ErrorActionIntent) {
        when (intent) {
            ErrorActionIntent.RemoveServerAndRelaunch -> viewModelScope.launch {
                serverManager.removeServer(_viewState.value.serverId)
                _events.emit(FrontendEvent.Relaunch)
            }

            ErrorActionIntent.ClearKeychainAndRelaunch -> viewModelScope.launch {
                keyChainRepository.clear()
                _events.emit(FrontendEvent.Relaunch)
            }

            ErrorActionIntent.GoToSettings -> _events.tryEmit(FrontendEvent.NavigateToSettings)

            ErrorActionIntent.OpenSecuritySettings -> _events.tryEmit(FrontendEvent.OpenSecuritySettings)

            ErrorActionIntent.UpdateWebView -> _events.tryEmit(FrontendEvent.UpdateWebView)

            ErrorActionIntent.Refresh -> onRetry()

            ErrorActionIntent.Wait -> _viewState.update { state ->
                if (state is FrontendViewState.Error) {
                    FrontendViewState.Loading(serverId = state.serverId, url = state.url)
                } else {
                    state
                }
            }
        }
    }

    /**
     * Called when the system WebView fails to initialize.
     *
     * Transitions to [FrontendViewState.Error] with a [FrontendConnectionError.Unrecoverable.WebViewCreationError]
     * so the error screen is displayed with guidance to update the system WebView.
     */
    fun onWebViewCreationFailed(throwable: Throwable) {
        onError(FrontendConnectionError.Unrecoverable.WebViewCreationError(throwable = throwable))
    }

    fun onShowSecurityLevelScreen() {
        _viewState.update {
            FrontendViewState.SecurityLevelRequired(serverId = it.serverId)
        }
    }

    fun switchServer(serverId: Int) {
        _viewState.update {
            FrontendViewState.LoadServer(serverId = serverId)
        }
        loadServer()
    }

    /**
     * Called from the security level configuration screen after the user makes a choice or discard.
     * The actual saving of the preference is handled by [io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModel].
     */
    fun onSecurityLevelDone() {
        val serverId = _viewState.value.serverId
        urlManager.onSecurityLevelShown(serverId)
        _viewState.update {
            FrontendViewState.LoadServer(serverId = serverId)
        }
        loadServer()
    }

    /**
     * Handles a download request from the WebView.
     *
     * On pre-Q devices, awaits the storage permission via [PermissionManager.checkStoragePermissionForDownload]
     * before proceeding. If the user declines, the download is silently dropped.
     *
     * Delegates to [FrontendDownloadManager] to dispatch the download based on URI scheme, then
     * processes the [DownloadResult] to emit appropriate UI events.
     *
     * @param url The URL of the file to download
     * @param contentDisposition The Content-Disposition header value
     * @param mimetype The MIME type of the file
     */
    fun onDownloadRequested(url: String, contentDisposition: String, mimetype: String) {
        viewModelScope.launch {
            if (!permissionManager.checkStoragePermissionForDownload()) return@launch

            val result = downloadManager.downloadFile(
                url = url,
                contentDisposition = contentDisposition,
                mimetype = mimetype,
                serverId = viewState.value.serverId,
            )
            handleDownloadResult(result)
        }
    }

    /**
     * Called by the host after the NFC tag-write flow completes.
     *
     * Sends a `result` response back to the frontend correlated by [messageId]. Matches the legacy
     * behavior of always reporting `success = true` with an empty payload: the underlying
     * `NfcSetupActivity` only returns a non-zero result code on successful write, and the frontend
     * silently ignores responses whose id it no longer tracks.
     *
     * @param messageId The correlation id received back from the activity result. Corresponds to
     *   the id of the originating `tag/write` request on success, or `0` (`RESULT_CANCELED`) on
     *   cancellation.
     */
    fun onNfcWriteCompleted(messageId: Int) {
        viewModelScope.launch {
            externalBusRepository.send(SuccessResultMessage(messageId))
        }
    }

    /**
     * Handles a swipe gesture detected on the WebView.
     *
     * @param direction The swipe direction
     * @param pointerCount Number of pointers in the gesture
     */
    fun onGesture(direction: GestureDirection, pointerCount: Int) {
        viewModelScope.launch {
            val result = gestureManager.handleGesture(
                serverId = _viewState.value.serverId,
                direction = direction,
                pointerCount = pointerCount,
            )
            handleGestureResult(result)
        }
    }

    /**
     * Called when the ExoPlayer fullscreen state changes.
     *
     * Updates the player UI state and emits a [FrontendEvent.RequestFullscreen] so the
     * host activity can decide the actual system bar visibility.
     */
    fun onExoPlayerFullscreenChanged(isFullScreen: Boolean) {
        exoPlayerManager.onFullscreenChanged(isFullScreen)
        _events.tryEmit(FrontendEvent.RequestFullscreen(isFullScreen))
    }

    /**
     * Forwards a scanned code to the manager, which replies to the frontend.
     * The scanner stays open until the frontend sends bar_code/close.
     */
    fun onBarcodeScanned(rawValue: String, format: BarcodeFormat) {
        viewModelScope.launch { barcodeScannerHandler.onScanned(rawValue, format) }
    }

    /** Forwards a scanner cancellation (close icon / back press = false, alternative option = true). */
    fun onBarcodeCancelled(forAction: Boolean) {
        viewModelScope.launch { barcodeScannerHandler.onCancelled(forAction) }
    }

    /**
     * Called when the app is leaving the foreground (host activity stop). When the user enabled
     * "always show first view on app start", resets the frontend to its default dashboard so the
     * next launch starts there — unless the user is in the Home Assistant settings or add-ons area.
     *
     * The preference is read first on purpose: it suspends, which lets the pending stop dispatch
     * complete so [LifecycleHandler.isAppInBackground] reads an up-to-date value (the activity stop
     * is not yet reflected at the instant the stop event fires). The background check then excludes
     * the case where one of our own activities (e.g. NFC write) came to the foreground.
     */
    fun onLeavingApp(currentUrl: String?) {
        viewModelScope.launch {
            if (!prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()) return@launch
            if (!LifecycleHandler.isAppInBackground()) return@launch
            if (!shouldShowFirstView(currentUrl)) return@launch
            navigateToDefaultDashboard(_viewState.value.serverId)
        }
    }

    /**
     * Forwarded ActivityResult after a Matter/Thread Play Services
     * intent completes.
     */
    fun onMatterThreadIntentResult(result: ActivityResult) {
        viewModelScope.launch { matterThreadHandler.onMatterThreadIntentResult(result) }
    }

    private suspend fun handleGestureResult(result: GestureResult) {
        when (result) {
            is GestureResult.Navigate -> _events.emit(result.event)
            is GestureResult.PerformWebViewAction -> _webViewActions.emit(result.action)
            is GestureResult.SwitchServer -> switchServer(result.serverId)
            is GestureResult.NavigateToDefaultDashboard -> navigateToDefaultDashboard(_viewState.value.serverId)
            is GestureResult.Forwarded, is GestureResult.Ignored -> { /* no-op */ }
        }
    }

    /**
     * Clears the WebView history and navigates the frontend to the server's default dashboard.
     *
     * Uses the `navigate` external bus command on Home Assistant 2025.6+, falling back to a
     * sidebar-click script ([WebViewAction.NavigateToDefaultPanelViaSidebar]) on older servers that
     * do not support it. History is cleared first (and awaited) so the back stack is reset before
     * the navigation lands.
     */
    private suspend fun navigateToDefaultDashboard(serverId: Int) {
        val clearHistory = WebViewAction.ClearHistory()
        _webViewActions.emit(clearHistory)
        clearHistory.result.await()

        val version = serverManager.getServer(serverId)?.version
        if (NavigateToMessage.isAvailable(version)) {
            externalBusRepository.send(NavigateToMessage(path = "/", replace = true))
        } else {
            // Deliberate use of the deprecated legacy fallback for servers without `navigate` support.
            @Suppress("DEPRECATION")
            _webViewActions.emit(WebViewAction.NavigateToDefaultPanelViaSidebar())
        }
    }

    /**
     * Returns `true` when, on leaving the app, the frontend should be reset to its default dashboard
     * (the "first view"). Returns `false` for a missing URL and for the Home Assistant settings and
     * add-ons areas, where the user should return to where they were.
     */
    private fun shouldShowFirstView(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return !url.matches(FIRST_VIEW_EXCLUDED_URL_REGEX)
    }

    /**
     * Releases the ExoPlayer whenever the view state is anything other than [FrontendViewState.Content].
     *
     * The overlay only makes sense while the frontend WebView is interactive, so leaving
     * Content (server switch, error, retry) must tear the player down to avoid stale audio
     * or network usage.
     */
    private fun releaseExoPlayerIfLeavingContent(state: FrontendViewState) {
        if (state !is FrontendViewState.Content) {
            exoPlayerManager.close()
        }
    }

    /**
     * Waits the [CONNECTION_TIMEOUT] in [FrontendViewState.Loading] and emits an
     * [FrontendConnectionError.ExternalBusTimeout] if the frontend has not completed its external-bus
     * handshake (transitioned to [FrontendViewState.Content]) by then.
     */
    private suspend fun watchLoadingTimeout(state: FrontendViewState) {
        if (state !is FrontendViewState.Loading) return
        delay(CONNECTION_TIMEOUT)
        if (_viewState.value is FrontendViewState.Loading) {
            onError(FrontendConnectionError.ExternalBusTimeout)
        }
    }

    private fun loadServer() {
        urlFlowJob?.cancel()
        urlFlowJob = viewModelScope.launch {
            permissionManager.checkLocalNetworkPermission()
            val currentState = _viewState.value
            val target = when (currentState) {
                is FrontendViewState.LoadServer -> currentState.target
                is FrontendViewState.Loading -> currentState.target
                else -> FrontendTarget.Default
            }
            urlManager.serverUrlFlow(
                serverId = currentState.serverId,
                target = target,
            ).collect { result ->
                handleUrlResult(result)
            }
        }
    }

    /**
     * Bridges [matterThreadHandler] events onto the ViewModel's [FrontendEvent] stream so
     * the screen only has one event flow to collect.
     */
    private fun collectMatterThreadEvents() {
        viewModelScope.launch {
            matterThreadHandler.events.collect { event ->
                _events.emit(
                    when (event) {
                        is FrontendMatterThreadHandler.Event.LaunchIntent ->
                            FrontendEvent.LaunchMatterThreadIntent(event.intentSender)
                        is FrontendMatterThreadHandler.Event.ShowSnackbar ->
                            FrontendEvent.ShowSnackbar(
                                messageResId = event.snackbar.messageRes,
                                action = event.snackbar.helpUrl?.let { url ->
                                    FrontendEvent.ShowSnackbar.Action(
                                        labelResId = commonR.string.get_help,
                                        event = FrontendEvent.OpenExternalLink(url.toUri()),
                                    )
                                },
                            )
                    },
                )
            }
        }
    }

    private suspend fun handleMessageResult(result: FrontendHandlerEvent) {
        when (result) {
            is FrontendHandlerEvent.Connected -> {
                val wasLoading = _viewState.value is FrontendViewState.Loading
                _viewState.update { currentState ->
                    if (currentState is FrontendViewState.Loading) {
                        FrontendViewState.Content(
                            serverId = currentState.serverId,
                            url = currentState.url,
                        )
                    } else {
                        currentState
                    }
                }
                if (wasLoading) {
                    // Remove any previous navigation
                    _webViewActions.emit(WebViewAction.ClearHistory())
                    checkSecurityVersion(_viewState.value.serverId)
                }
                permissionManager.checkNotificationPermission(_viewState.value.serverId)
            }

            is FrontendHandlerEvent.Disconnected -> {
                // Disconnection handling not yet implemented
            }

            is FrontendHandlerEvent.ThemeUpdated -> {
                // Theme update handling not yet implemented
            }

            is FrontendHandlerEvent.OpenSettings -> {
                _events.emit(FrontendEvent.NavigateToSettings)
            }

            is FrontendHandlerEvent.OpenAssistSettings -> {
                _events.emit(FrontendEvent.NavigateToAssistSettings)
            }

            is FrontendHandlerEvent.ShowAssist -> {
                _events.emit(
                    FrontendEvent.NavigateToAssist(
                        serverId = _viewState.value.serverId,
                        pipelineId = result.pipelineId,
                        startListening = result.startListening,
                    ),
                )
            }

            is FrontendHandlerEvent.PerformHaptic -> {
                _webViewActions.emit(WebViewAction.Haptic(result.hapticType))
            }

            is FrontendHandlerEvent.AuthError -> {
                onError(result.error)
            }

            is FrontendHandlerEvent.DownloadCompleted -> {
                handleDownloadResult(result.result)
            }

            is FrontendHandlerEvent.WriteNfcTag -> {
                _events.tryEmit(FrontendEvent.NavigateToNfcWrite(messageId = result.messageId, tagId = result.tagId))
            }

            is FrontendHandlerEvent.ExoPlayerAction -> {
                exoPlayerManager.handle(result)
            }

            is FrontendHandlerEvent.StartImprovScan -> improvHandler.onStartImprovScan()

            is FrontendHandlerEvent.ConfigureImprovDevice ->
                improvHandler.onConfigureImprovDevice(result.deviceName)

            is FrontendHandlerEvent.EntityAddToExecuted -> {
                result.event?.let { _events.tryEmit(it) }
            }

            is FrontendHandlerEvent.StartMatterCommissioning -> {
                viewModelScope.launch { matterThreadHandler.onStartMatterCommissioning() }
            }

            is FrontendHandlerEvent.ImportThreadCredentials -> {
                viewModelScope.launch {
                    matterThreadHandler.onImportThreadCredentials(serverId = _viewState.value.serverId)
                }
            }

            is FrontendHandlerEvent.ShowBarcodeScanner -> barcodeScannerHandler.show(
                messageId = result.messageId,
                title = result.title,
                description = result.description,
                alternativeOptionLabel = result.alternativeOptionLabel,
            )

            is FrontendHandlerEvent.NotifyBarcodeScanner -> viewModelScope.launch {
                barcodeScannerHandler.notify(result.message)
            }

            FrontendHandlerEvent.CloseBarcodeScanner -> barcodeScannerHandler.close()

            is FrontendHandlerEvent.ConfigSent,
            is FrontendHandlerEvent.UnknownMessage,
            is FrontendHandlerEvent.EntityAddToActionsSent,
            -> {
                // No-op
            }
        }
    }

    /**
     * Forwards user-entered Wi-Fi credentials to the device on the handler's current
     * [io.homeassistant.companion.android.frontend.improv.ImprovUIState.ConfiguringDevice] —
     * no-ops if no Improv session is active or the BLE address hasn't been resolved yet.
     */
    fun onImprovConnectDevice(ssid: String, password: String) {
        viewModelScope.launch {
            improvHandler.onConnectDevice(scope = viewModelScope, ssid = ssid, password = password)
        }
    }

    /** Re-arms scanning after an Improv error — wired to the sheet's "Try again" button. */
    fun onImprovRestart() {
        viewModelScope.launch { improvHandler.onRestart() }
    }

    /** Closes the Improv bottom sheet and, if successful, navigates the frontend to the matching config flow. */
    fun onImprovSheetDismissed() {
        viewModelScope.launch { improvHandler.onDismissed(serverId = _viewState.value.serverId) }
    }

    /**
     * Hosts the discovered-device forwarder on the caller's coroutine — suspends until cancelled.
     * Intended to be invoked from `FrontendScreen` inside a `repeatOnLifecycle(RESUMED)` block so
     * the BLE scan's lifetime is bound to the route's visibility.
     */
    suspend fun processImprovScanRequests() = improvHandler.processImprovScanRequests()

    /**
     * Handles URL load results from the URL manager.
     *
     * @param result The URL load result to handle
     */
    private fun handleUrlResult(result: UrlLoadResult) {
        when (result) {
            is UrlLoadResult.Success -> {
                pendingMoreInfoEntityId = result.moreInfoEntityId
                _viewState.update {
                    FrontendViewState.Loading(
                        serverId = result.serverId,
                        url = result.url,
                        target = FrontendTarget.Default,
                    )
                }
            }

            is UrlLoadResult.ServerNotFound -> {
                onError(
                    FrontendConnectionError.Unreachable(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "Server not found",
                        rawErrorType = "ServerNotFound",
                    ),
                )
            }

            is UrlLoadResult.SessionNotConnected -> {
                onError(
                    FrontendConnectionError.AuthRevoked(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "Session not authenticated",
                        rawErrorType = "SessionNotConnected",
                    ),
                )
            }

            is UrlLoadResult.InsecureBlocked -> {
                _viewState.update {
                    FrontendViewState.Insecure(
                        serverId = result.serverId,
                        missingHomeSetup = result.missingHomeSetup,
                        missingLocation = result.missingLocation,
                    )
                }
            }

            is UrlLoadResult.SecurityLevelRequired -> {
                onShowSecurityLevelScreen()
            }

            is UrlLoadResult.NoUrlAvailable -> {
                onError(
                    FrontendConnectionError.Unreachable(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "No URL available",
                        rawErrorType = "NoUrlAvailable",
                    ),
                )
            }
        }
    }

    private suspend fun handleDownloadResult(result: DownloadResult) {
        when (result) {
            is DownloadResult.Forwarded -> {
                // No UI feedback needed — success notification is handled by
                // the system DownloadManager or DataUriDownloadManager
            }

            is DownloadResult.OpenWithSystem -> {
                _events.emit(FrontendEvent.OpenExternalLink(result.uri))
            }

            is DownloadResult.Error -> {
                _events.emit(FrontendEvent.ShowSnackbar(result.messageResId))
            }
        }
    }

    /**
     * Handles a URL the WebView is about to load.
     *
     * Custom schemes (`app://`, `intent:`) and URLs that do not match the current server origin are
     * routed to the host through [FrontendEvent]s, while same-origin URLs are left for the WebView.
     * The origin comparison uses scheme, host and port (see [hasSameOrigin]).
     *
     * @return `true` when the URL was intercepted (the host will handle it), `false` to let the WebView load it.
     */
    private fun onUrlIntercepted(uri: Uri): Boolean {
        val rawUrl = uri.toString()
        return when {
            rawUrl.startsWith(APP_PREFIX) -> {
                _events.tryEmit(FrontendEvent.LaunchApp(rawUrl.substringAfter(APP_PREFIX)))
                true
            }

            rawUrl.startsWith(INTENT_PREFIX) -> {
                _events.tryEmit(FrontendEvent.LaunchIntent(rawUrl))
                true
            }

            uri.hasSameOrigin(urlFlow.value) -> false

            else -> {
                _events.tryEmit(FrontendEvent.OpenExternalLink(uri))
                true
            }
        }
    }

    private fun onError(error: FrontendConnectionError) {
        // Resolve the connection type so the error screen can label the "Refresh" action, then
        // build the recovery actions for the screen to render.
        viewModelScope.launch {
            val serverId = _viewState.value.serverId
            val isInternal = resolveIsInternalConnection(serverId)
            val actions = errorActions(error, isInternalConnection = isInternal)
            _viewState.update { currentState ->
                FrontendViewState.Error(
                    serverId = currentState.serverId,
                    url = currentState.url,
                    error = error,
                    actions = actions,
                )
            }
        }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
    }

    private suspend fun resolveIsInternalConnection(serverId: Int): Boolean = try {
        // requiresUrl = true so we only report (and offer to force) "internal" when an internal URL
        // actually exists to load. Matches legacy WebViewActivity's isInternal() call.
        serverManager.connectionStateProvider(serverId).isInternal(requiresUrl = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Unable to resolve connection type for the error screen")
        false
    }

    /**
     * Warns the user via a snackbar when the connected server runs a version that predates the
     * security fixes (and the server still asks clients to notify). Ports the legacy
     * `checkSecurityVersion`; the working frontend is never blocked.
     */
    private suspend fun checkSecurityVersion(serverId: Int) {
        val shouldWarn = try {
            val integrationRepository = serverManager.integrationRepository(serverId)
            !integrationRepository.isHomeAssistantVersionAtLeast(2021, 1, 5) &&
                integrationRepository.shouldNotifySecurityWarning()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to check server security version")
            false
        }
        if (shouldWarn) {
            _events.emit(
                FrontendEvent.ShowSnackbar(
                    messageResId = commonR.string.security_vulnerably_message,
                    action = FrontendEvent.ShowSnackbar.Action(
                        labelResId = commonR.string.security_vulnerably_view,
                        event = FrontendEvent.OpenExternalLink(SECURITY_ALERT_URL.toUri()),
                    ),
                ),
            )
        }
    }

    /**
     * Called when a page finishes loading in the WebView.
     *
     * Cancels any previous zoom observer and starts a fresh collection of
     * [PrefsRepository.zoomSettingsFlow]. Because the flow emits the current values
     * on start, this immediately applies zoom against the loaded DOM (needed because
     * navigations can reset the viewport meta tag). The collection then stays active
     * to react to settings changes until the next page load restarts it.
     */
    private fun onPageFinished(url: String?) {
        // Open the more-info dialog for an older-server deep link now that the frontend has loaded.
        pendingMoreInfoEntityId?.let { entityId ->
            pendingMoreInfoEntityId = null
            viewModelScope.launch { _webViewActions.emit(WebViewAction.OpenMoreInfo(entityId)) }
        }

        zoomObserverJob?.cancel()
        zoomObserverJob = viewModelScope.launch {
            prefsRepository.zoomSettingsFlow().collect { settings ->
                _webViewActions.emit(
                    WebViewAction.ApplyZoom(
                        zoomLevel = settings.zoomLevel,
                        pinchToZoomEnabled = settings.pinchToZoomEnabled,
                    ),
                )
            }
        }
    }
}
