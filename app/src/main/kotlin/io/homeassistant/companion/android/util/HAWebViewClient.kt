package io.homeassistant.companion.android.util

import android.content.Context
import android.net.Uri
import android.net.http.SslError
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
import io.homeassistant.companion.android.frontend.FrontendJsCallback
import io.homeassistant.companion.android.frontend.error.FrontendError
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Factory for creating [HAWebViewClient] instances dedicated to loading Home Assistant frontend.
 *
 * The created clients handle Home Assistant-specific concerns such as TLS client authentication,
 * error mapping to [FrontendError], and JavaScript injection into the WebView.
 */
class HAWebViewClientFactory @Inject constructor(@NamedKeyChain private val keyChainRepository: KeyChainRepository) {
    /**
     * Creates a new [HAWebViewClient] with the specified configuration.
     *
     * @param currentUrlFlow StateFlow providing the current URL being loaded.
     *        Used to filter errors - only errors for this URL trigger [onFrontendError].
     * @param onFrontendError Callback when a WebView error is mapped to a [FrontendError].
     * @param frontendJsCallback Optional JS interface to attach to the WebView.
     *        If will be re-attached after WebView crash recovery.
     * @param onCrash Optional callback invoked after WebView crash recovery.
     *        Called after the JS bridge is re-attached (if present).
     * @param onUrlIntercepted Optional callback to intercept URL navigation.
     *        Receives the URI and whether TLS client auth was required.
     *        Return `true` to prevent WebView from loading the URL.
     * @param onPageFinished Optional callback when a page finishes loading.
     */
    fun create(
        currentUrlFlow: StateFlow<String?>,
        onFrontendError: (FrontendError) -> Unit,
        frontendJsCallback: FrontendJsCallback? = null,
        onCrash: (() -> Unit)? = null,
        onUrlIntercepted: ((uri: Uri, isTLSClientAuthNeeded: Boolean) -> Boolean)? = null,
        onPageFinished: (() -> Unit)? = null,
    ): HAWebViewClient {
        return HAWebViewClient(
            keyChainRepository = keyChainRepository,
            currentUrlFlow = currentUrlFlow,
            onFrontendError = onFrontendError,
            frontendJsCallback = frontendJsCallback,
            onCrash = onCrash,
            onUrlIntercepted = onUrlIntercepted,
            onPageFinished = onPageFinished,
        )
    }
}

/**
 * WebViewClient dedicated to loading Home Assistant frontend.
 *
 * Handles error mapping to [FrontendError], TLS client authentication, and crash recovery.
 * Use [HAWebViewClientFactory] to create instances.
 */
class HAWebViewClient internal constructor(
    keyChainRepository: KeyChainRepository,
    private val currentUrlFlow: StateFlow<String?>,
    private val onFrontendError: (FrontendError) -> Unit,
    private val frontendJsCallback: FrontendJsCallback?,
    private val onCrash: (() -> Unit)?,
    private val onUrlIntercepted: ((uri: Uri, isTLSClientAuthNeeded: Boolean) -> Boolean)?,
    private val onPageFinished: (() -> Unit)?,
) : TLSWebViewClient(keyChainRepository) {

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished?.invoke()
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
        onFrontendError(frontendError)
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
        onFrontendError(frontendError)
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
            FrontendError.AuthenticationError(
                message = messageRes,
                errorDetails = error.toString(),
                rawErrorType = SslError::class.toString(),
            ),
        )
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Timber.e("onRenderProcessGone: webView crashed")
        view?.let { webView ->
            frontendJsCallback?.attachToWebView(webView)
            onCrash?.invoke()
        }
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
