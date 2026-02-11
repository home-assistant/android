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
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.frontend.externalbus.WebViewScript
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import io.homeassistant.companion.android.frontend.handler.FrontendMessageHandler
import io.homeassistant.companion.android.frontend.navigation.FrontendNavigationEvent
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.url.FrontendUrlManager
import io.homeassistant.companion.android.frontend.url.UrlLoadResult
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Maximum time to wait for the frontend to load before showing a timeout error. */
@VisibleForTesting
val CONNECTION_TIMEOUT = 20.seconds

/** Delay before stopping shared flows after the last subscriber disconnects. */
private val SUBSCRIPTION_STOP_DELAY = 500.milliseconds

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
    private val frontendMessageHandler: FrontendMessageHandler,
    private val urlManager: FrontendUrlManager,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        frontendMessageHandler: FrontendMessageHandler,
        urlManager: FrontendUrlManager,
        connectivityCheckRepository: ConnectivityCheckRepository,
    ) : this(
        initialServerId = savedStateHandle.toRoute<FrontendRoute>().serverId,
        initialPath = savedStateHandle.toRoute<FrontendRoute>().path,
        webViewClientFactory = webViewClientFactory,
        frontendMessageHandler = frontendMessageHandler,
        urlManager = urlManager,
        connectivityCheckRepository = connectivityCheckRepository,
    )

    private val _viewState: MutableStateFlow<FrontendViewState> = MutableStateFlow(
        FrontendViewState.LoadServer(
            serverId = initialServerId,
            path = initialPath,
        ),
    )
    val viewState: StateFlow<FrontendViewState> = _viewState.asStateFlow()

    private val _connectivityCheckState = MutableStateFlow(ConnectivityCheckState())
    override val connectivityCheckState: StateFlow<ConnectivityCheckState> = _connectivityCheckState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<FrontendNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<FrontendNavigationEvent> = _navigationEvents.asSharedFlow()

    override val urlFlow: StateFlow<String?> =
        _viewState.map { it.url }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_DELAY), null)

    override val errorFlow: StateFlow<FrontendConnectionError?> =
        _viewState.map { state -> (state as? FrontendViewState.Error)?.error }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_DELAY), null)

    /** Flow of scripts to be evaluated in the WebView. */
    val scriptsToEvaluate: Flow<WebViewScript> = frontendMessageHandler.scriptsToEvaluate()

    /**
     * JavaScript bridge for communication between the WebView and native code.
     *
     * Must be attached to the WebView via [FrontendJsBridge.attachToWebView].
     */
    val frontendJsCallback: FrontendJsCallback = FrontendJsBridge(
        handler = frontendMessageHandler,
        serverIdProvider = { viewState.value.serverId },
        scope = viewModelScope,
    )

    /**
     * WebViewClient that handles TLS client authentication and error callbacks.
     */
    val webViewClient: HAWebViewClient = webViewClientFactory.create(
        currentUrlFlow = urlFlow,
        onFrontendError = ::onError,
        frontendJsCallback = frontendJsCallback,
        onCrash = ::onRetry,
    )

    private var connectivityCheckJob: Job? = null

    /** Job tracking the urlFlow collection - cancelled when switching servers. */
    private var urlFlowJob: Job? = null

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
            frontendMessageHandler.messageResults().collect { result ->
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

    /** Retry loading the current server after an error. */
    fun onRetry() {
        _viewState.update {
            FrontendViewState.LoadServer(serverId = it.serverId)
        }
        loadServer()
    }

    /**
     * Show the security level configuration screen.
     */
    fun onShowSecurityLevelScreen() {
        _viewState.update {
            FrontendViewState.SecurityLevelRequired(serverId = it.serverId)
        }
    }

    /**
     * Switch to a different server.
     */
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

    /**
     * Handles results from the external bus message handler.
     */
    private fun handleMessageResult(result: FrontendHandlerEvent) {
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
            }

            is FrontendHandlerEvent.Disconnected -> {
                // Disconnection handling not yet implemented
            }

            is FrontendHandlerEvent.ConfigSent -> {
                // Config already sent by handler, no action needed
            }

            is FrontendHandlerEvent.OpenSettings -> {
                _navigationEvents.tryEmit(FrontendNavigationEvent.NavigateToSettings)
            }

            is FrontendHandlerEvent.ThemeUpdated -> {
                // Theme update handling not yet implemented
            }

            is FrontendHandlerEvent.UnknownMessage -> {
                // Already logged by handler, no action needed
            }

            is FrontendHandlerEvent.AuthError -> {
                onError(result.error)
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

    private fun onError(error: FrontendConnectionError) {
        val currentState = _viewState.value
        _viewState.update {
            FrontendViewState.Error(
                serverId = currentState.serverId,
                url = currentState.url,
                error = error,
            )
        }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
    }
}
