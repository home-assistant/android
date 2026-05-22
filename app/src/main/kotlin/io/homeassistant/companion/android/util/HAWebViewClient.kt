package io.homeassistant.companion.android.util

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.util.compose.webview.BLANK_URL
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Factory for creating [HAWebViewClient] instances dedicated to loading Home Assistant frontend.
 *
 * The created clients handle Home Assistant-specific concerns such as TLS client authentication,
 * error mapping to [FrontendConnectionError], and JavaScript injection into the WebView.
 */
class HAWebViewClientFactory @Inject constructor(@NamedKeyChain private val keyChainRepository: KeyChainRepository) {
    /**
     * Creates a new [HAWebViewClient] with the specified configuration.
     *
     * @param currentUrlFlow StateFlow providing the current URL being loaded.
     *        Used to filter errors - only errors for this URL trigger [onFrontendError].
     * @param onFrontendError Callback when a WebView error is mapped to a [FrontendConnectionError].
     * @param onCrash Optional callback invoked after WebView crash recovery.
     * @param onUrlIntercepted Optional callback to intercept URL navigation.
     *        Receives the URI and whether TLS client auth was required.
     *        Return `true` to prevent WebView from loading the URL.
     * @param onPageFinished Optional callback when a page finishes loading.
     * @param onCanGoBackChanged Optional callback invoked whenever the WebView's
     *        back-stack state changes (page navigations, SPA history updates).
     *        Receives [WebView.canGoBack]. Callers can hoist this into a state
     *        holder to drive a back handler's enabled-state for Android 14+
     *        predictive-back support.
     * @param onReceivedHttpAuthRequest Optional callback when the server requests HTTP Basic Auth.
     *        Receives the handler, host, the resource URL that triggered the request, and the realm.
     */
    fun create(
        currentUrlFlow: StateFlow<String?>,
        onFrontendError: (FrontendConnectionError) -> Unit,
        onCrash: (() -> Unit)? = null,
        onUrlIntercepted: ((uri: Uri, isTLSClientAuthNeeded: Boolean) -> Boolean)? = null,
        onPageFinished: (() -> Unit)? = null,
        onCanGoBackChanged: ((Boolean) -> Unit)? = null,
        onReceivedHttpAuthRequest: (
            (
                handler: HttpAuthHandler,
                host: String,
                resource: String,
                realm: String,
            ) -> Unit
        )? = null,
    ): HAWebViewClient {
        return HAWebViewClient(
            keyChainRepository = keyChainRepository,
            currentUrlFlow = currentUrlFlow,
            onFrontendError = onFrontendError,
            onCrash = onCrash,
            onUrlIntercepted = onUrlIntercepted,
            onPageFinished = onPageFinished,
            onCanGoBackChanged = onCanGoBackChanged,
            onReceivedHttpAuthRequest = onReceivedHttpAuthRequest,
        )
    }
}

/**
 * WebViewClient dedicated to loading Home Assistant frontend.
 *
 * Handles error mapping to [FrontendConnectionError], TLS client authentication, and crash recovery.
 * Use [HAWebViewClientFactory] to create instances.
 */
class HAWebViewClient internal constructor(
    keyChainRepository: KeyChainRepository,
    private val currentUrlFlow: StateFlow<String?>,
    private val onFrontendError: (FrontendConnectionError) -> Unit,
    private val onCrash: (() -> Unit)?,
    private val onUrlIntercepted: ((uri: Uri, isTLSClientAuthNeeded: Boolean) -> Boolean)?,
    private val onPageFinished: (() -> Unit)?,
    private val onCanGoBackChanged: ((Boolean) -> Unit)?,
    private val onReceivedHttpAuthRequest: (
        (handler: HttpAuthHandler, host: String, resource: String, realm: String) -> Unit
    )?,
) : TLSWebViewClient(keyChainRepository) {

    /** Last resource URL loaded by the WebView, used to identify the resource requesting auth. */
    private var lastResourceUrl: String? = null

    /**
     * Tracks the previously finished page URL so we can detect transitions out
     * of the [BLANK_URL] placeholder and drop it from the WebView's back-stack.
     */
    private var lastFinishedUrl: String? = null

    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)
        lastResourceUrl = url
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Clear the WebView back-stack on transitions to a new origin: out of the
        // about:blank placeholder (loading / error / security overlays), across
        // internal <-> external URL switches on the same server, and across server
        // switches. Without this, back would walk into a stale URL that is no
        // longer reachable on the current network. Same-origin in-frontend
        // navigation (full page loads between content URLs and SPA pushState,
        // which doesn't even fire onPageFinished) is unaffected. Transitions
        // INTO about:blank are skipped so the back-stack survives an error state
        // and remains usable after recovery.
        val previous = lastFinishedUrl
        if (previous != null && url != null && url != BLANK_URL && originOf(previous) != originOf(url)) {
            view?.clearHistory()
        }
        lastFinishedUrl = url
        onPageFinished?.invoke()
        notifyCanGoBack(view)
    }

    /**
     * Returns the `scheme://authority` prefix of [url], or the full string when the
     * URL is opaque (e.g. `about:blank`). String-based so it works in plain JUnit
     * tests without the Android framework or Robolectric.
     */
    private fun originOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return url
        val authorityStart = schemeEnd + 3
        val pathStart = url.indexOf('/', authorityStart)
        return if (pathStart == -1) url else url.substring(0, pathStart)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // SPA navigations (history.pushState) only surface here, not in onPageFinished.
        notifyCanGoBack(view)
    }

    private fun notifyCanGoBack(view: WebView?) {
        onCanGoBackChanged?.invoke(view?.canGoBack() == true)
    }

    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        val lastResourceUrl = lastResourceUrl
        if (handler != null &&
            host != null &&
            realm != null &&
            onReceivedHttpAuthRequest != null &&
            lastResourceUrl != null
        ) {
            onReceivedHttpAuthRequest.invoke(handler, host, lastResourceUrl, realm)
        } else {
            super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)

        // Only handle errors for the main URL being loaded
        if (request?.url?.toString() != currentUrlFlow.value) return

        val errorDetails = formatErrorDetails(
            view?.context,
            error?.errorCode,
            error?.description?.toString(),
        )
        Timber.e("onReceivedError: $errorDetails")

        val frontendConnectionError = when (error?.errorCode) {
            ERROR_FAILED_SSL_HANDSHAKE -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.webview_error_FAILED_SSL_HANDSHAKE,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_AUTHENTICATION -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.webview_error_AUTHENTICATION,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_PROXY_AUTHENTICATION -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.webview_error_PROXY_AUTHENTICATION,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_UNSUPPORTED_AUTH_SCHEME -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.webview_error_AUTH_SCHEME,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_HOST_LOOKUP -> FrontendConnectionError.UnreachableError(
                message = commonR.string.webview_error_HOST_LOOKUP,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_TIMEOUT -> FrontendConnectionError.UnreachableError(
                message = commonR.string.webview_error_TIMEOUT,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            ERROR_CONNECT -> FrontendConnectionError.UnreachableError(
                message = commonR.string.webview_error_CONNECT,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )

            else -> FrontendConnectionError.UnknownError(
                message = commonR.string.connection_error_unknown_error,
                errorDetails = errorDetails,
                rawErrorType = WebResourceError::class.toString(),
            )
        }
        onFrontendError(frontendConnectionError)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)

        // Only handle errors for the main URL being loaded
        if (request?.url?.toString() != currentUrlFlow.value) return

        val errorDetails = formatErrorDetails(
            view?.context,
            errorResponse?.statusCode,
            errorResponse?.reasonPhrase,
        )
        Timber.e("onReceivedHttpError: $errorDetails")

        val frontendConnectionError = when {
            isTLSClientAuthNeeded && !isCertificateChainValid -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.tls_cert_expired_message,
                errorDetails = errorDetails,
                rawErrorType = WebResourceResponse::class.toString(),
            )

            isTLSClientAuthNeeded && errorResponse?.statusCode == 400 -> FrontendConnectionError.AuthenticationError(
                message = commonR.string.tls_cert_not_found_message,
                errorDetails = errorDetails,
                rawErrorType = WebResourceResponse::class.toString(),
            )

            else -> FrontendConnectionError.UnknownError(
                message = commonR.string.connection_error_unknown_error,
                errorDetails = errorDetails,
                rawErrorType = WebResourceResponse::class.toString(),
            )
        }
        onFrontendError(frontendConnectionError)
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
        onFrontendError(
            FrontendConnectionError.AuthenticationError(
                message = messageRes,
                errorDetails = error.toString(),
                rawErrorType = SslError::class.toString(),
            ),
        )
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Timber.e("onRenderProcessGone: webView crashed")
        onCrash?.invoke()
        return true
    }

    // Override deprecated method for backward compatibility with API 23 and below.
    // The non-deprecated shouldOverrideUrlLoading(WebView, WebResourceRequest) is not invoked
    // on these older Android versions, so this method remains necessary.
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java for SDK >= 24", ReplaceWith("shouldOverrideUrlLoading(view, request)"))
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return url?.toUri()?.let { onUrlIntercepted?.invoke(it, isTLSClientAuthNeeded) } ?: false
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
