package io.homeassistant.companion.android.frontend

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.download.DownloadResult
import io.homeassistant.companion.android.frontend.download.FrontendDownloadManager
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage
import io.homeassistant.companion.android.frontend.filechooser.FileChooserManager
import io.homeassistant.companion.android.frontend.filechooser.FileChooserRequest
import io.homeassistant.companion.android.frontend.gesture.FrontendGestureHandler
import io.homeassistant.companion.android.frontend.gesture.GestureResult
import io.homeassistant.companion.android.frontend.handler.FrontendBusObserver
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import io.homeassistant.companion.android.frontend.js.BridgeState
import io.homeassistant.companion.android.frontend.js.FrontendJsBridgeFactory
import io.homeassistant.companion.android.frontend.js.FrontendJsCallback
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.frontend.url.FrontendUrlManager
import io.homeassistant.companion.android.frontend.url.UrlLoadResult
import io.homeassistant.companion.android.util.HAWebChromeClient
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import javax.inject.Inject
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Maximum time to wait for the frontend to load before showing a timeout error. */
@VisibleForTesting
val CONNECTION_TIMEOUT = 10.seconds

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
    initialPath: String?,
    webViewClientFactory: HAWebViewClientFactory,
    private val frontendBusObserver: FrontendBusObserver,
    private val externalBusRepository: FrontendExternalBusRepository,
    private val urlManager: FrontendUrlManager,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
    private val permissionManager: PermissionManager,
    private val frontendJsBridgeFactory: FrontendJsBridgeFactory,
    private val downloadManager: FrontendDownloadManager,
    private val gestureHandler: FrontendGestureHandler,
    private val prefsRepository: PrefsRepository,
    private val dialogManager: FrontendDialogManager,
    private val fileChooserManager: FileChooserManager,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        frontendBusObserver: FrontendBusObserver,
        externalBusRepository: FrontendExternalBusRepository,
        urlManager: FrontendUrlManager,
        connectivityCheckRepository: ConnectivityCheckRepository,
        permissionManager: PermissionManager,
        frontendJsBridgeFactory: FrontendJsBridgeFactory,
        downloadManager: FrontendDownloadManager,
        gestureHandler: FrontendGestureHandler,
        prefsRepository: PrefsRepository,
        dialogManager: FrontendDialogManager,
        fileChooserManager: FileChooserManager,
    ) : this(
        initialServerId = savedStateHandle.toRoute<FrontendRoute>().serverId,
        initialPath = savedStateHandle.toRoute<FrontendRoute>().path,
        webViewClientFactory = webViewClientFactory,
        frontendBusObserver = frontendBusObserver,
        externalBusRepository = externalBusRepository,
        urlManager = urlManager,
        connectivityCheckRepository = connectivityCheckRepository,
        permissionManager = permissionManager,
        frontendJsBridgeFactory = frontendJsBridgeFactory,
        downloadManager = downloadManager,
        gestureHandler = gestureHandler,
        prefsRepository = prefsRepository,
        dialogManager = dialogManager,
        fileChooserManager = fileChooserManager,
    )

    /**
     * Manages the frontend view state with protection against transitions out of unrecoverable states.
     *
     * Once a [FrontendConnectionError.UnrecoverableError] is set, the current state is
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
         * If the current state is an [FrontendConnectionError] with an [FrontendConnectionError.UnrecoverableError],
         * the update is silently ignored because the state cannot be recovered.
         */
        fun update(transform: (FrontendViewState) -> FrontendViewState) {
            _state.update { currentState ->
                if (currentState is FrontendViewState.Error &&
                    currentState.error is FrontendConnectionError.UnrecoverableError
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
            path = initialPath,
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
    )

    /** The current pending file chooser request from the WebView, or null if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = fileChooserManager.pendingFileChooser

    val webChromeClient: HAWebChromeClient = HAWebChromeClient(
        onPermissionRequest = { request ->
            viewModelScope.launch {
                permissionManager.onWebViewPermissionRequest(request)
            }
        },
        onJsConfirm = { message, jsResult ->
            viewModelScope.launch {
                if (dialogManager.showJsConfirm(message)) jsResult.confirm() else jsResult.cancel()
            }
            true
        },
        onShowFileChooser = { filePathCallback, fileChooserParams ->
            viewModelScope.launch {
                filePathCallback.onReceiveValue(fileChooserManager.pickFiles(fileChooserParams))
            }
            true
        },
    )

    /** The current pending permission request that needs user approval, or null if none. */
    val pendingPermissionRequest = permissionManager.pendingPermissionRequest

    /** The current pending dialog over the WebView, or null if none. */
    val pendingDialog = dialogManager.pendingDialog

    private var connectivityCheckJob: Job? = null

    /** Job tracking the urlFlow collection - cancelled when switching servers. */
    private var urlFlowJob: Job? = null

    /** Job tracking the zoom settings flow collection - restarted on each page load. */
    private var zoomObserverJob: Job? = null

    init {
        // Timeout watcher - cancels automatically when state changes from Loading
        viewModelScope.launch {
            _viewState.collectLatest { state ->
                if (state is FrontendViewState.Loading) {
                    delay(CONNECTION_TIMEOUT)
                    // Only trigger timeout if still in Loading state
                    if (_viewState.value is FrontendViewState.Loading) {
                        onError(
                            FrontendConnectionError.UnreachableError(
                                message = commonR.string.webview_error_TIMEOUT,
                                errorDetails = "",
                                rawErrorType = "ConnectionTimeout",
                            ),
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            frontendBusObserver.messageResults().collect { result ->
                handleMessageResult(result)
            }
        }

        loadServer()
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

    fun onRetry() {
        _viewState.update {
            FrontendViewState.LoadServer(serverId = it.serverId)
        }
        loadServer()
    }

    /**
     * Called when the system WebView fails to initialize.
     *
     * Transitions to [FrontendViewState.Error] with a [FrontendConnectionError.UnrecoverableError.WebViewCreationError]
     * so the error screen is displayed with guidance to update the system WebView.
     */
    fun onWebViewCreationFailed(throwable: Throwable) {
        onError(
            FrontendConnectionError.UnrecoverableError.WebViewCreationError(
                message = commonR.string.webview_creation_failed,
                throwable = throwable,
            ),
        )
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
            externalBusRepository.send(ResultMessage.success(messageId))
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
            val result = gestureHandler.handleGesture(
                serverId = _viewState.value.serverId,
                direction = direction,
                pointerCount = pointerCount,
            )
            handleGestureResult(result)
        }
    }

    private suspend fun handleGestureResult(result: GestureResult) {
        when (result) {
            is GestureResult.Navigate -> _events.emit(result.event)
            is GestureResult.PerformWebViewAction -> _webViewActions.emit(result.action)
            is GestureResult.PerformWebViewActionThen<*> -> {
                _webViewActions.emit(result.action)
                result.action.result.await()
                handleGestureResult(result.then())
            }
            is GestureResult.SwitchServer -> switchServer(result.serverId)
            is GestureResult.Forwarded, is GestureResult.Ignored -> { /* no-op */ }
        }
    }
    private fun loadServer() {
        urlFlowJob?.cancel()
        urlFlowJob = viewModelScope.launch {
            val currentState = _viewState.value
            val path = when (currentState) {
                is FrontendViewState.LoadServer -> currentState.path
                is FrontendViewState.Loading -> currentState.path
                else -> null
            }
            urlManager.serverUrlFlow(
                serverId = currentState.serverId,
                path = path,
            ).collect { result ->
                handleUrlResult(result)
            }
        }
    }

    private suspend fun handleMessageResult(result: FrontendHandlerEvent) {
        when (result) {
            is FrontendHandlerEvent.Connected -> {
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

            is FrontendHandlerEvent.ConfigSent,
            is FrontendHandlerEvent.UnknownMessage,
            -> {
                // No-op
            }
        }
    }

    /**
     * Handles URL load results from the URL manager.
     *
     * @param result The URL load result to handle
     */
    private fun handleUrlResult(result: UrlLoadResult) {
        when (result) {
            is UrlLoadResult.Success -> {
                _viewState.update {
                    FrontendViewState.Loading(
                        serverId = result.serverId,
                        url = result.url,
                        path = null,
                    )
                }
            }

            is UrlLoadResult.ServerNotFound -> {
                onError(
                    FrontendConnectionError.UnreachableError(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "Server not found",
                        rawErrorType = "ServerNotFound",
                    ),
                )
            }

            is UrlLoadResult.SessionNotConnected -> {
                onError(
                    FrontendConnectionError.AuthenticationError(
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
                    FrontendConnectionError.UnreachableError(
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

    private fun onError(error: FrontendConnectionError) {
        _viewState.update { currentState ->
            FrontendViewState.Error(
                serverId = currentState.serverId,
                url = currentState.url,
                error = error,
            )
        }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
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
    private fun onPageFinished() {
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
