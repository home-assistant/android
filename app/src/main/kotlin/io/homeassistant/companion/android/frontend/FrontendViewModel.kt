package io.homeassistant.companion.android.frontend

import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.frontend.error.FrontendErrorStateProvider
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.UrlUtil
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Maximum time to wait for the frontend to load before showing a timeout error. */
@VisibleForTesting val CONNECTION_TIMEOUT = 20.seconds

/**
 * ViewModel for the new Compose-based frontend screen.
 *
 * Handles loading the Home Assistant WebView, authentication, and error handling.
 * Implements [FrontendErrorStateProvider] to enable use of the shared error screen.
 */
@HiltViewModel
internal class FrontendViewModel @VisibleForTesting constructor(
    initialServerId: Int,
    initialPath: String?,
    private val serverManager: ServerManager,
    private val keyChainRepository: KeyChainRepository,
) : ViewModel(),
    FrontendErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        serverManager: ServerManager,
        @NamedKeyChain keyChainRepository: KeyChainRepository,
    ) : this(
        initialServerId = savedStateHandle.toRoute<FrontendRoute>().serverId,
        initialPath = savedStateHandle.toRoute<FrontendRoute>().path,
        serverManager = serverManager,
        keyChainRepository = keyChainRepository,
    )

    private val _viewState: MutableStateFlow<FrontendViewState> = MutableStateFlow(
        FrontendViewState.LoadServer(
            serverId = initialServerId,
            path = initialPath,
        ),
    )
    val viewState: StateFlow<FrontendViewState> = _viewState.asStateFlow()

    private val _urlFlow = MutableStateFlow<String?>(null)
    override val urlFlow: StateFlow<String?> = _urlFlow.asStateFlow()

    private val _errorFlow = MutableStateFlow<FrontendError?>(null)
    override val errorFlow: StateFlow<FrontendError?> = _errorFlow.asStateFlow()

    /** Job tracking the urlFlow collection - cancelled when switching servers */
    private var urlFlowJob: Job? = null

    /**
     * Tracks whether the connection security level fragment has already been shown
     * during this ViewModel's lifecycle. Once shown for a specific server, the fragment
     * won't be shown again for that server.
     *
     * Key: server ID, Value: `true` if already shown
     */
    private val connectionSecurityLevelShown = hashMapOf<Int, Boolean>()

    init {
        // Timeout watcher - cancels automatically when state changes from Loading
        viewModelScope.launch {
            _viewState.collectLatest { state ->
                Timber.e("Last state is $state")
                if (state is FrontendViewState.Loading) {
                    delay(CONNECTION_TIMEOUT)
                    // Only trigger timeout if still in Loading state
                    if (_viewState.value is FrontendViewState.Loading) {
                        onError(
                            FrontendError.UnreachableError(
                                message = commonR.string.webview_error_TIMEOUT,
                                errorDetails = "",
                                rawErrorType = "ConnectionTimeout",
                            ),
                        )
                    }
                }
            }
        }
        loadServer()
    }

    /**
     * WebViewClient that handles TLS client authentication and error callbacks.
     */
    val webViewClient: TLSWebViewClient = object : TLSWebViewClient(keyChainRepository) {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Timber.v("Page finished loading ${if (BuildConfig.DEBUG) url else ""}")
            val currentState = _viewState.value
            if (currentState is FrontendViewState.Loading) {
                _viewState.update {
                    // TODO this should only be set from the external bus message
                    FrontendViewState.Content(
                        serverId = currentState.serverId,
                        url = currentState.url,
                    )
                }
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            if (request?.url?.toString() == urlFlow.value) {
                val errorDetails = formatErrorDetails(
                    view?.context,
                    error?.errorCode,
                    error?.description?.toString(),
                )
                Timber.e("onReceivedError: $errorDetails")

                val frontendError = when (error?.errorCode) {
                    ERROR_FAILED_SSL_HANDSHAKE -> FrontendError.AuthenticationError(
                        message = commonR.string.webview_error_FAILED_SSL_HANDSHAKE,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_AUTHENTICATION -> FrontendError.AuthenticationError(
                        message = commonR.string.webview_error_AUTHENTICATION,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_PROXY_AUTHENTICATION -> FrontendError.AuthenticationError(
                        message = commonR.string.webview_error_PROXY_AUTHENTICATION,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_UNSUPPORTED_AUTH_SCHEME -> FrontendError.AuthenticationError(
                        message = commonR.string.webview_error_AUTH_SCHEME,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_HOST_LOOKUP -> FrontendError.UnreachableError(
                        message = commonR.string.webview_error_HOST_LOOKUP,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_TIMEOUT -> FrontendError.UnreachableError(
                        message = commonR.string.webview_error_TIMEOUT,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    ERROR_CONNECT -> FrontendError.UnreachableError(
                        message = commonR.string.webview_error_CONNECT,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                    else -> FrontendError.UnknownError(
                        message = commonR.string.connection_error_unknown_error,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceError::class.toString(),
                    )
                }
                onError(frontendError)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request?.url?.toString() == urlFlow.value) {
                val errorDetails = formatErrorDetails(
                    view?.context,
                    errorResponse?.statusCode,
                    errorResponse?.reasonPhrase,
                )
                Timber.e("onReceivedHttpError: $errorDetails")

                val frontendError = when {
                    isTLSClientAuthNeeded && !isCertificateChainValid -> FrontendError.AuthenticationError(
                        message = commonR.string.tls_cert_expired_message,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceResponse::class.toString(),
                    )
                    isTLSClientAuthNeeded && errorResponse?.statusCode == 400 -> FrontendError.AuthenticationError(
                        message = commonR.string.tls_cert_not_found_message,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceResponse::class.toString(),
                    )
                    else -> FrontendError.UnknownError(
                        message = commonR.string.connection_error_unknown_error,
                        errorDetails = errorDetails,
                        rawErrorType = WebResourceResponse::class.toString(),
                    )
                }
                onError(frontendError)
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            super.onReceivedSslError(view, handler, error)
            Timber.e("onReceivedSslError: $error")

            val messageRes = when (error?.primaryError) {
                SslError.SSL_DATE_INVALID -> commonR.string.webview_error_SSL_DATE_INVALID
                SslError.SSL_EXPIRED -> commonR.string.webview_error_SSL_EXPIRED
                SslError.SSL_IDMISMATCH -> commonR.string.webview_error_SSL_IDMISMATCH
                SslError.SSL_INVALID -> commonR.string.webview_error_SSL_INVALID
                SslError.SSL_NOTYETVALID -> commonR.string.webview_error_SSL_NOTYETVALID
                SslError.SSL_UNTRUSTED -> commonR.string.webview_error_SSL_UNTRUSTED
                else -> commonR.string.error_ssl
            }
            onError(
                FrontendError.AuthenticationError(
                    message = messageRes,
                    errorDetails = error.toString(),
                    rawErrorType = SslError::class.toString(),
                ),
            )
        }

        private fun formatErrorDetails(context: Context?, code: Int?, description: String?): String {
            return context?.getString(
                commonR.string.connection_error_more_details_description_content,
                code.toString(),
                description?.takeIf { it.isNotEmpty() }
                    ?: context.getString(commonR.string.no_description),
            ) ?: ""
        }
    }

    private fun loadServer() {
        urlFlowJob?.cancel()

        urlFlowJob = viewModelScope.launch {
            val currentState = _viewState.value
            val requestedServerId = currentState.serverId
            val server = serverManager.getServer(requestedServerId)

            if (server == null) {
                Timber.e("Server not found for id: $requestedServerId")
                onError(
                    FrontendError.UnreachableError(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "Server not found",
                        rawErrorType = "ServerNotFound",
                    ),
                )
                return@launch
            }

            // Use actual server ID (resolves SERVER_ID_ACTIVE to real ID)
            val serverId = server.id

            // Verify the session is authenticated
            if (!isSessionConnected(serverId)) {
                Timber.w("Session not connected for server: $serverId")
                onError(
                    FrontendError.AuthenticationError(
                        message = commonR.string.error_connection_failed,
                        errorDetails = "Session not authenticated",
                        rawErrorType = "SessionNotConnected",
                    ),
                )
                return@launch
            }

            serverManager.connectionStateProvider(serverId).urlFlow().collect { urlState ->
                // Read path from current state - it gets consumed (set to null) after first use
                val currentState = _viewState.value
                val path = when (currentState) {
                    is FrontendViewState.LoadServer -> currentState.path
                    is FrontendViewState.Loading -> currentState.path
                    else -> null
                }
                handleUrlState(
                    serverId = serverId,
                    urlState = urlState,
                    path = path,
                )
            }
        }
    }

    /**
     * Checks if the server has an authenticated session.
     *
     * @return `true` if the session is connected, `false` if anonymous or unavailable
     */
    private suspend fun isSessionConnected(serverId: Int): Boolean {
        return try {
            serverManager.authenticationRepository(serverId).getSessionState() != SessionState.ANONYMOUS
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Unable to get server session state")
            false
        }
    }

    private suspend fun handleUrlState(serverId: Int, urlState: UrlState, path: String?) {
        when (urlState) {
            is UrlState.HasUrl -> {
                val baseUrl = urlState.url
                if (baseUrl == null) {
                    Timber.e("No URL available for server: $serverId")
                    onError(
                        FrontendError.UnreachableError(
                            message = commonR.string.error_connection_failed,
                            errorDetails = "No URL available",
                            rawErrorType = "NoUrlAvailable",
                        ),
                    )
                    return
                }

                // Check if security level needs to be configured before loading
                val shouldShowSecurityLevel = shouldSetSecurityLevel(serverId) &&
                    !connectionSecurityLevelShown.getOrPut(serverId) { false }

                if (shouldShowSecurityLevel) {
                    Timber.d("Security level not set for server $serverId, showing SecurityLevelRequired")
                    _viewState.update {
                        FrontendViewState.SecurityLevelRequired(serverId = serverId)
                    }
                    return
                }

                // Build URL with path (skip path handling if it starts with "entityId:")
                val urlToLoad = if (path != null && !path.startsWith("entityId:")) {
                    UrlUtil.handle(baseUrl, path)
                } else {
                    baseUrl
                }

                if (urlToLoad == null) {
                    Timber.e("Failed to build URL for server: $serverId")
                    onError(
                        FrontendError.UnreachableError(
                            message = commonR.string.error_connection_failed,
                            errorDetails = "Failed to build URL",
                            rawErrorType = "UrlBuildFailed",
                        ),
                    )
                    return
                }

                // Add external_auth=1 query parameter for authentication
                val urlWithAuth = urlToLoad.toString().toUri()
                    .buildUpon()
                    .appendQueryParameter("external_auth", "1")
                    .build()
                    .toString()

                Timber.d("Loading server URL: $urlWithAuth")
                _urlFlow.update { urlWithAuth }
                _viewState.update {
                    FrontendViewState.Loading(
                        serverId = serverId,
                        url = urlWithAuth,
                        path = null, // Path consumed after first URL
                    )
                }
            }

            UrlState.InsecureState -> {
                Timber.w("Insecure connection blocked for server: $serverId")
                _viewState.update {
                    FrontendViewState.Insecure(serverId = serverId)
                }
            }
        }
    }

    /**
     * Checks whether the user needs to configure their insecure connection preference.
     *
     * @return `true` if the server uses a plain text (HTTP) URL and the user has not yet set their
     * preference for allowing insecure connections, `false` otherwise
     */
    private suspend fun shouldSetSecurityLevel(serverId: Int): Boolean {
        val connection = serverManager.getServer(serverId)?.connection ?: return false
        if (!connection.hasPlainTextUrl) {
            return false
        }
        return connection.allowInsecureConnection == null
    }

    private fun onError(error: FrontendError) {
        _errorFlow.update { error }
        val currentState = _viewState.value
        _viewState.update {
            FrontendViewState.Error(
                serverId = currentState.serverId,
                url = currentState.url,
                error = error,
            )
        }
    }

    /**
     * Retry loading the current server after an error.
     */
    fun onRetry() {
        _errorFlow.update { null }
        val currentState = _viewState.value
        _viewState.update {
            FrontendViewState.LoadServer(serverId = currentState.serverId)
        }
        loadServer()
    }

    /**
     * Switch to a different server.
     *
     * @param serverId The server ID to switch to
     */
    fun switchServer(serverId: Int) {
        _errorFlow.update { null }
        _viewState.update {
            FrontendViewState.LoadServer(serverId = serverId)
        }
        loadServer()
    }

    /**
     * Switch to the next server in the list.
     */
    fun nextServer() {
        viewModelScope.launch {
            val servers = serverManager.servers()
            if (servers.size <= 1) return@launch

            val currentIndex = servers.indexOfFirst { it.id == _viewState.value.serverId }
            val nextIndex = (currentIndex + 1) % servers.size
            switchServer(servers[nextIndex].id)
        }
    }

    /**
     * Switch to the previous server in the list.
     */
    fun previousServer() {
        viewModelScope.launch {
            val servers = serverManager.servers()
            if (servers.size <= 1) return@launch

            val currentIndex = servers.indexOfFirst { it.id == _viewState.value.serverId }
            val previousIndex = if (currentIndex <= 0) servers.size - 1 else currentIndex - 1
            switchServer(servers[previousIndex].id)
        }
    }
}
