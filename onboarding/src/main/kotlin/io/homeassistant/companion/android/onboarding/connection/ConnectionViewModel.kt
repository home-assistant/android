package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.util.TLSWebViewClient
import javax.inject.Inject
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
     * Represents an error event during the connection process.
     *
     * @property resId The string resource ID for the error message.
     * @property formatArgs Optional arguments to be used for formatting the error message.
     */
    class Error(@StringRes val resId: Int, vararg val formatArgs: Any?) : ConnectionNavigationEvent

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
    private val keyChainRepository: KeyChainRepository,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        @NamedKeyChain
        keyChainRepository: KeyChainRepository,
    ) : this(savedStateHandle.toRoute<ConnectionRoute>().url, keyChainRepository)

    private val rawUri: Uri by lazy { rawUrl.toUri() }

    private val _navigationEventsFlow = MutableSharedFlow<ConnectionNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    private val _urlFlow = MutableStateFlow<String?>(null)
    val urlFlow = _urlFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    private val _isErrorFlow = MutableStateFlow(false)
    val isErrorFlow = _isErrorFlow.asStateFlow()

    val webViewClient: TLSWebViewClient = object : TLSWebViewClient(keyChainRepository) {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            _isLoadingFlow.update { false }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return request?.url?.let { interceptRedirectIfRequired(it) } ?: false
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            Timber.e("onReceivedError: Status Code: ${error?.errorCode} Description: ${error?.description}")

            if (request?.url?.toString() == urlFlow.value) {
                val error = when (error?.errorCode) {
                    ERROR_FAILED_SSL_HANDSHAKE -> ConnectionNavigationEvent.Error(
                        commonR.string.webview_error_FAILED_SSL_HANDSHAKE,
                    )

                    ERROR_AUTHENTICATION -> ConnectionNavigationEvent.Error(commonR.string.webview_error_AUTHENTICATION)
                    ERROR_PROXY_AUTHENTICATION -> ConnectionNavigationEvent.Error(
                        commonR.string.webview_error_PROXY_AUTHENTICATION,
                    )

                    ERROR_UNSUPPORTED_AUTH_SCHEME -> ConnectionNavigationEvent.Error(
                        commonR.string.webview_error_AUTH_SCHEME,
                    )

                    ERROR_HOST_LOOKUP -> ConnectionNavigationEvent.Error(commonR.string.webview_error_HOST_LOOKUP)
                    else -> ConnectionNavigationEvent.Error(
                        commonR.string.error_http_generic,
                        error?.errorCode,
                        error?.description?.takeIf { it.isNotEmpty() }
                            ?: view?.context?.getString(commonR.string.no_description),
                    )
                }

                onError(error)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request?.url?.toString() == urlFlow.value) {
                Timber.e(
                    "onReceivedHttpError: Status Code: ${errorResponse?.statusCode} Description: ${errorResponse?.reasonPhrase}",
                )

                val error = when {
                    isTLSClientAuthNeeded && !isCertificateChainValid -> ConnectionNavigationEvent.Error(
                        commonR.string.tls_cert_expired_message,
                    )

                    isTLSClientAuthNeeded && errorResponse?.statusCode == 400 -> ConnectionNavigationEvent.Error(
                        commonR.string.tls_cert_not_found_message,
                    )

                    else -> ConnectionNavigationEvent.Error(
                        commonR.string.error_http_generic,
                        errorResponse?.statusCode,
                        errorResponse?.reasonPhrase?.takeIf { it.isNotEmpty() }
                            ?: view?.context?.getString(commonR.string.no_description),
                    )
                }
                onError(error)
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
            onError(ConnectionNavigationEvent.Error(messageRes))
        }
    }

    init {
        viewModelScope.launch {
            buildAuthUrl(rawUrl)
        }
    }

    private suspend fun buildAuthUrl(base: String) {
        Timber.d("Building auth url based on $base")
        try {
            val url = base.toHttpUrl()
            val builder = if (url.host.endsWith("ui.nabu.casa", true)) {
                HttpUrl.Builder()
                    .scheme(url.scheme)
                    .host(url.host)
                    .port(url.port)
            } else {
                url.newBuilder()
            }
            _urlFlow.emit(
                builder
                    .addPathSegments("auth/authorize")
                    .addEncodedQueryParameter("response_type", "code")
                    .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                    .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                    .build()
                    .toString(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Unable to build authentication URL")
            onError(ConnectionNavigationEvent.Error(R.string.connection_screen_malformed_url))
        }
    }

    private fun interceptRedirectIfRequired(url: Uri): Boolean {
        val code = url.getQueryParameter("code")

        return if (url.scheme == AUTH_CALLBACK_SCHEME && url.host == AUTH_CALLBACK_HOST) {
            if (!code.isNullOrBlank()) {
                viewModelScope.launch {
                    _navigationEventsFlow.emit(
                        ConnectionNavigationEvent.Authenticated(
                            url = rawUrl,
                            authCode = code,
                            requiredMTLS = webViewClient.isTLSClientAuthNeeded,
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

    private fun onError(error: ConnectionNavigationEvent.Error) {
        viewModelScope.launch {
            _isErrorFlow.emit(true)
            _navigationEventsFlow.emit(error)
        }
    }
}
