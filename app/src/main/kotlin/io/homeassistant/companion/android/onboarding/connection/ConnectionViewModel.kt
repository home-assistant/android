package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.FrontendConnectionErrorStateProvider
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

/**
 * Represents the navigation events that can occur during the connection process.
 */
internal sealed interface ConnectionNavigationEvent {
    /**
     * Emitted when authentication is successful and the code is available
     *
     * @property url The URL of the Home Assistant instance
     * @param authCode The authorization code returned by Home Assistant
     * @param requiredMTLS The authentication required the use of mTLS
     */
    data class Authenticated(val url: String, val authCode: String, val requiredMTLS: Boolean) :
        ConnectionNavigationEvent

    /**
     * Emitted when a link is not from the server that we are connecting to,
     * allowing it to be opened in an external browser.
     *
     * We don't want to open the URL within the application if it's not for the same host.
     * Otherwise the user might be able to leave the onboarding and not being able to come back.
     * A good exemple is clicking on the `help` or `forget password` button on the login page.
     *
     * @property url The [Uri] of the link to open.
     */
    data class OpenExternalLink(val url: Uri) : ConnectionNavigationEvent
}

private const val AUTH_CALLBACK_SCHEME = "homeassistant"
private const val AUTH_CALLBACK_HOST = "auth-callback"
private const val AUTH_CALLBACK = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST"

@HiltViewModel
internal class ConnectionViewModel @VisibleForTesting constructor(
    private val rawUrl: String,
    private val webViewClientFactory: HAWebViewClientFactory,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        connectivityCheckRepository: ConnectivityCheckRepository,
    ) : this(savedStateHandle.toRoute<ConnectionRoute>().url, webViewClientFactory, connectivityCheckRepository)

    private val rawUri: Uri by lazy { rawUrl.toUri() }

    private val _navigationEventsFlow = MutableSharedFlow<ConnectionNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    private val _urlFlow = MutableStateFlow<String?>(null)
    override val urlFlow = _urlFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val _errorFlow = MutableStateFlow<FrontendConnectionError?>(null)
    override val errorFlow = _errorFlow.asStateFlow()

    private val _connectivityCheckState = MutableStateFlow(ConnectivityCheckState())
    override val connectivityCheckState = _connectivityCheckState.asStateFlow()

    private var connectivityCheckJob: Job? = null

    /**
     * Runs connectivity checks against the server URL.
     * Results are emitted to [connectivityCheckState].
     */
    override fun runConnectivityChecks() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = viewModelScope.launch {
            connectivityCheckRepository.runChecks(rawUrl).collect { state ->
                _connectivityCheckState.value = state
            }
        }
    }

    val webViewClient: HAWebViewClient = webViewClientFactory.create(
        currentUrlFlow = urlFlow,
        onFrontendError = ::onError,
        onUrlIntercepted = ::interceptRedirectIfRequired,
        onPageFinished = { _isLoadingFlow.update { false } },
    )

    init {
        viewModelScope.launch {
            buildAuthUrl(rawUrl)
        }
    }

    private suspend fun buildAuthUrl(base: String) {
        Timber.d("Building auth url based on $base")
        try {
            val authUrl = with(base.toHttpUrl()) {
                HttpUrl.Builder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .addPathSegments("auth/authorize")
                    .addEncodedQueryParameter("response_type", "code")
                    .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                    .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                    .build()
                    .toString()
            }
            Timber.d("Auth url is: $authUrl")
            _urlFlow.emit(authUrl)
        } catch (e: Exception) {
            Timber.e(e, "Unable to build authentication URL")
            onError(
                FrontendConnectionError.UnreachableError(
                    message = commonR.string.connection_screen_malformed_url,
                    errorDetails = e.localizedMessage ?: e.message,
                    rawErrorType = e::class.toString(),
                ),
            )
        }
    }

    private fun interceptRedirectIfRequired(url: Uri, isTLSClientAuthNeeded: Boolean): Boolean {
        val code = url.getQueryParameter("code")

        return if (url.scheme == AUTH_CALLBACK_SCHEME && url.host == AUTH_CALLBACK_HOST) {
            if (!code.isNullOrBlank()) {
                viewModelScope.launch {
                    _navigationEventsFlow.emit(
                        ConnectionNavigationEvent.Authenticated(
                            url = rawUrl,
                            authCode = code,
                            requiredMTLS = isTLSClientAuthNeeded,
                        ),
                    )
                }
                true // Intercepted: Authentication successful
            } else {
                Timber.w("Auth code is missing from the auth callback")
                false // Not intercepted: Auth code missing
            }
        } else if (url.host != rawUri.host) {
            Timber.d("$url is not from the server, opening it on external browser.")
            viewModelScope.launch {
                _navigationEventsFlow.emit(ConnectionNavigationEvent.OpenExternalLink(url))
            }
            true // Intercepted: External link
        } else {
            false // Default: Not intercepted
        }
    }

    private fun onError(error: FrontendConnectionError) {
        _errorFlow.update { error }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
    }
}
