package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.annotation.VisibleForTesting
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
import io.homeassistant.companion.android.frontend.filechooser.FileChooserManager
import io.homeassistant.companion.android.frontend.filechooser.FileChooserRequest
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.util.HAWebChromeClient
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    webViewClientFactory: HAWebViewClientFactory,
    private val connectivityCheckRepository: ConnectivityCheckRepository,
    private val fileChooserManager: FileChooserManager,
) : ViewModel(),
    FrontendConnectionErrorStateProvider {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        webViewClientFactory: HAWebViewClientFactory,
        connectivityCheckRepository: ConnectivityCheckRepository,
        fileChooserManager: FileChooserManager,
    ) : this(
        savedStateHandle.toRoute<ConnectionRoute>().url,
        webViewClientFactory,
        connectivityCheckRepository,
        fileChooserManager,
    )

    /**
     * The origin (scheme/host/port) of the URL used to open this screen.
     *
     * Used both to detect redirects to a new port/scheme and to tell apart links that belong to the
     * server (same host) from external links that should open in a browser.
     */
    private val rawHttpUrl: HttpUrl? = rawUrl.toHttpUrlOrNull()

    /**
     * The base URL the app should store for this server, normalized to its origin
     * (`scheme://host:port`, no path/query/fragment).
     *
     * Starts as the origin of the URL used to open the screen and is updated to a new origin when
     * the WebView is redirected to the same host on a different scheme/port (e.g. the landing page on
     * `:8123` handing over to the freshly installed core on `:80`). A redirect to a different host is
     * ignored, so the initial origin is kept. Falls back to the raw URL if it cannot be parsed.
     */
    private val effectiveUrl = MutableStateFlow(rawHttpUrl?.toBaseUrl() ?: rawUrl)

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
            connectivityCheckRepository.runChecks(effectiveUrl.value).collect { state ->
                _connectivityCheckState.value = state
            }
        }
    }

    val webViewClient: HAWebViewClient = webViewClientFactory.create(
        currentUrlFlow = urlFlow,
        onFrontendError = ::onError,
        onUrlIntercepted = ::interceptRedirectIfRequired,
        onPageFinished = { url ->
            _isLoadingFlow.update { false }
            updateEffectiveBaseUrl(url)
        },
    )

    /**
     * [WebChromeClient][android.webkit.WebChromeClient] used by the onboarding WebView.
     *
     * The Home Assistant onboarding flow could request a file selection (e.g. backup restore).
     */
    val webChromeClient: HAWebChromeClient = HAWebChromeClient(
        onShowFileChooser = { filePathCallback, fileChooserParams ->
            viewModelScope.launch {
                filePathCallback.onReceiveValue(fileChooserManager.pickFiles(fileChooserParams))
            }
            true
        },
    )

    /** The current pending file chooser request from the WebView, or `null` if none. */
    val pendingFileChooser: StateFlow<FileChooserRequest?> = fileChooserManager.pendingFileChooser

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
                FrontendConnectionError.Unreachable(
                    message = commonR.string.connection_screen_malformed_url,
                    errorDetails = e.localizedMessage ?: e.message,
                    rawErrorType = e::class.toString(),
                ),
            )
        }
    }

    /**
     * Tracks the origin the WebView ends up on after each page load during onboarding.
     *
     * The WebView's `onPageFinished` reports the final URL once any redirect chain (e.g. the landing page on
     * `:8123` handing over to the freshly installed core on `:80`) has resolved. When that origin
     * is on the same host as the initial URL but a different scheme or port, [effectiveUrl] is
     * updated so the correct URL is stored once authentication completes. Navigations to a different
     * host are ignored, keeping the initial URL.
     */
    private fun updateEffectiveBaseUrl(url: String?) {
        val navigated = url?.toHttpUrlOrNull() ?: return
        val initial = rawHttpUrl ?: return
        if (navigated.host != initial.host) return
        // Never downgrade a secure connection: if the screen was opened on https, ignore a redirect to
        // http and keep the original URL rather than silently storing a plaintext origin.
        if (initial.isHttps && !navigated.isHttps) {
            Timber.w("Ignoring an https to http downgrade during onboarding, keeping the original URL")
            return
        }

        val newOrigin = navigated.toBaseUrl()
        if (newOrigin != effectiveUrl.value) {
            Timber.d("Onboarding navigated to a new origin on the same host, updating stored URL")
            effectiveUrl.value = newOrigin
        }
    }

    private fun interceptRedirectIfRequired(url: Uri, isTLSClientAuthNeeded: Boolean): Boolean {
        return if (url.isOpaque) {
            false // Not intercepted: opaque is not handled by app
        } else if (url.scheme == AUTH_CALLBACK_SCHEME && url.host == AUTH_CALLBACK_HOST) {
            val code = url.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                viewModelScope.launch {
                    _navigationEventsFlow.emit(
                        ConnectionNavigationEvent.Authenticated(
                            url = effectiveUrl.value,
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
        } else if (url.host != rawHttpUrl?.host) {
            Timber.d("$url is not from the server, opening it in an external browser")
            viewModelScope.launch {
                _navigationEventsFlow.emit(ConnectionNavigationEvent.OpenExternalLink(url))
            }
            true // Intercepted: External link
        } else {
            false // Default: Not intercepted
        }
    }

    /**
     * Called when the system WebView fails to initialize.
     *
     * Transitions to an error state with a [FrontendConnectionError.Unrecoverable.WebViewCreationError]
     * so the error screen is displayed with guidance to update the system WebView.
     */
    fun onWebViewCreationFailed(throwable: Throwable) {
        onError(
            FrontendConnectionError.Unrecoverable.WebViewCreationError(throwable = throwable),
        )
    }

    private fun onError(error: FrontendConnectionError) {
        _errorFlow.update { error }
        // Automatically run connectivity checks when an error occurs
        runConnectivityChecks()
    }
}

/**
 * The origin (`scheme://host:port`) of this URL as a string, with path, query and fragment removed.
 *
 * Delegating to [HttpUrl] keeps the formatting correct: IPv6 literal hosts stay bracketed (e.g.
 * `http://[::1]:8123`) and the scheme's default port is dropped. The trailing slash that [HttpUrl]
 * always appends is removed so the result has the same shape as a bare base URL.
 */
private fun HttpUrl.toBaseUrl(): String = newBuilder()
    .encodedPath("/")
    .encodedQuery(null)
    .encodedFragment(null)
    .build()
    .toString()
    .removeSuffix("/")
