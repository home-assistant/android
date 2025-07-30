package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChainRepository
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

internal sealed interface ConnectionNavigationEvent {
    class Error(@StringRes val resId: Int, vararg val formatArgs: Any?) : ConnectionNavigationEvent

    data object URLMalformed : ConnectionNavigationEvent
    data class Authenticated(val authCode: String) : ConnectionNavigationEvent
}

private const val AUTH_CALLBACK_SCHEME = "homeassistant"
private const val AUTH_CALLBACK_HOST = "auth-callback"
private const val AUTH_CALLBACK = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST"

@HiltViewModel
internal class ConnectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @NamedKeyChainRepository
    val keyChainRepository: KeyChainRepository,
) : ViewModel() {
    private val route: ConnectionRoute = savedStateHandle.toRoute()
    private val _navigationEventsFlow = MutableSharedFlow<ConnectionNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    // TODO we could wrap the 3 flows into a State object and have only one flow
    private val _urlFlow = MutableStateFlow<String?>(null)
    val urlFlow = _urlFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    val webViewClient: WebViewClient = object : TLSWebViewClient(keyChainRepository) {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            _isLoadingFlow.update { false }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return request?.url?.let { shouldRedirect(it) } ?: false
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            Timber.e("onReceivedError: Status Code: ${error?.errorCode} Description: ${error?.description}")

            if (request?.url?.toString() == urlFlow.value) {
                val event = when (error?.errorCode) {
                    ERROR_FAILED_SSL_HANDSHAKE -> ConnectionNavigationEvent.Error(
                        R.string.webview_error_FAILED_SSL_HANDSHAKE,
                    )
                    ERROR_AUTHENTICATION -> ConnectionNavigationEvent.Error(R.string.webview_error_AUTHENTICATION)
                    ERROR_PROXY_AUTHENTICATION -> ConnectionNavigationEvent.Error(
                        R.string.webview_error_PROXY_AUTHENTICATION,
                    )
                    ERROR_UNSUPPORTED_AUTH_SCHEME -> ConnectionNavigationEvent.Error(R.string.webview_error_AUTH_SCHEME)
                    ERROR_HOST_LOOKUP -> ConnectionNavigationEvent.Error(R.string.webview_error_HOST_LOOKUP)
                    else -> ConnectionNavigationEvent.Error(
                        R.string.error_http_generic,
                        error?.errorCode,
                        if (error?.description.isNullOrBlank()) {
                            R.string.no_description
                        } else {
                            error.description
                        },
                    )
                }

                viewModelScope.launch {
                    _navigationEventsFlow.emit(event)
                }
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

                val resId = when {
                    isTLSClientAuthNeeded && !isCertificateChainValid -> R.string.tls_cert_expired_message
                    isTLSClientAuthNeeded && errorResponse?.statusCode == 400 -> R.string.tls_cert_not_found_message
                    else -> R.string.error_http_generic
                }
                viewModelScope.launch {
                    _navigationEventsFlow.emit(ConnectionNavigationEvent.Error(resId))
                }
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            super.onReceivedSslError(view, handler, error)
            Timber.e("onReceivedSslError: $error")
            when (error?.primaryError) {
                SslError.SSL_DATE_INVALID -> R.string.webview_error_SSL_DATE_INVALID
                SslError.SSL_EXPIRED -> R.string.webview_error_SSL_EXPIRED
                SslError.SSL_IDMISMATCH -> R.string.webview_error_SSL_IDMISMATCH
                SslError.SSL_INVALID -> R.string.webview_error_SSL_INVALID
                SslError.SSL_NOTYETVALID -> R.string.webview_error_SSL_NOTYETVALID
                SslError.SSL_UNTRUSTED -> R.string.webview_error_SSL_UNTRUSTED
                else -> R.string.error_ssl
            }
        }
    }

    init {
        viewModelScope.launch {
            buildAuthUrl(route.url)
        }
    }

    private suspend fun buildAuthUrl(base: String) {
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
            _navigationEventsFlow.emit(ConnectionNavigationEvent.URLMalformed)
        }
    }

    private fun shouldRedirect(url: Uri): Boolean {
        val code = url.getQueryParameter("code")

        return if (url.scheme == AUTH_CALLBACK_SCHEME && url.host == AUTH_CALLBACK_HOST && !code.isNullOrBlank()) {
            viewModelScope.launch {
                _navigationEventsFlow.emit(ConnectionNavigationEvent.Authenticated(code))
            }
            true
        } else {
            false
        }
    }
}
