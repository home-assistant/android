package io.homeassistant.companion.android.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.webkit.ClientCertRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.util.sensitive
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/*
 * [TLSWebViewClient] is on the onboarding module for convenience, since we don't have yet
 * a place to share components between app modules. Common is shared with wear and
 * we don't want the webview code in the wear app.
 */

// The server is already reachable (WebView obtained the certificate), so the call is
// mostly a TLS handshake. 3 seconds gives enough margin for slow connections
// without blocking the WebView handler indefinitely.
private val tlsValidationTimeout = 3.seconds

private sealed interface TlsValidationResult {
    data object Trusted : TlsValidationResult
    data object Untrusted : TlsValidationResult
    data object NetworkError : TlsValidationResult
}

open class TLSWebViewClient(
    private var keyChainRepository: KeyChainRepository,
    private val validationScope: CoroutineScope,
    private val trustManager: X509TrustManager,
    /** Used as a fallback for API < 29, where [SslCertificate.x509Certificate] is unavailable. */
    okHttpClient: OkHttpClient,
    @VisibleForTesting internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WebViewClient() {
    var isTLSClientAuthNeeded = false
        @VisibleForTesting set

    var hasUserDeniedAccess = false
        private set

    var isCertificateChainValid = false
        @VisibleForTesting set

    // Used as fallback on API < 29 where SslCertificate.x509Certificate is unavailable
    private val tlsValidationClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(tlsValidationTimeout.toJavaDuration())
        .build()

    /**
     * Called when an SSL error has been rejected and the WebView will not load the resource.
     *
     * Subclasses can override this to surface the error to the user. Not called when the
     * server certificate is trusted and the connection proceeds.
     */
    open fun onSslErrorRejected(error: SslError?) {}

    /**
     * Called when TLS validation could not be completed due to a network error (e.g. timeout,
     * DNS failure) rather than a genuine certificate rejection.
     *
     * The default implementation delegates to [onSslErrorRejected]. Subclasses can override to
     * surface a connectivity error instead of an SSL error to the user.
     */
    open fun onTlsValidationNetworkError(error: SslError?) {
        onSslErrorRejected(error)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Timber.w("onReceivedSslError: primary error ${error?.primaryError} on ${sensitive { error?.url.orEmpty() }}")
        if (error == null) {
            handler?.cancel()
            onSslErrorRejected(null)
            return
        }
        if (error.primaryError == SslError.SSL_UNTRUSTED && error.url != null) {
            val url = error.url!!
            val handlerRef = handler?.let { WeakReference(it) }
            validationScope.launch {
                val result = isTlsTrusted(error.certificate, url)
                withContext(Dispatchers.Main) {
                    val handler = handlerRef?.get()
                    if (handler == null) {
                        Timber.w("SSL handler was collected before TLS validation completed")
                        return@withContext
                    }
                    when (result) {
                        TlsValidationResult.Trusted -> handler.proceed()
                        TlsValidationResult.Untrusted -> {
                            handler.cancel()
                            onSslErrorRejected(error)
                        }
                        TlsValidationResult.NetworkError -> {
                            handler.cancel()
                            onTlsValidationNetworkError(error)
                        }
                    }
                }
            }
        } else {
            handler?.cancel()
            onSslErrorRejected(error)
        }
    }

    private suspend fun isTlsTrusted(sslCertificate: SslCertificate?, url: String): TlsValidationResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cert = sslCertificate?.x509Certificate ?: return TlsValidationResult.Untrusted
            return withContext(ioDispatcher) {
                try {
                    // SslCertificate.x509Certificate only exposes the leaf certificate, not the full
                    // chain. Use "UNKNOWN" for the auth type since the cipher suite is unavailable.
                    trustManager.checkServerTrusted(arrayOf(cert), "UNKNOWN")
                    TlsValidationResult.Trusted
                } catch (e: CertificateException) {
                    // The leaf-only array is not enough when the server uses intermediate CAs.
                    // Fall through to OkHttp which performs a full TLS handshake and receives the
                    // complete chain from the server.
                    Timber.d(e, "Direct trust manager check failed, falling back to OkHttp for ${sensitive(url)}")
                    isTlsTrustedViaOkHttp(url)
                }
            }
        }
        return isTlsTrustedViaOkHttp(url)
    }

    private suspend fun isTlsTrustedViaOkHttp(url: String): TlsValidationResult = withContext(ioDispatcher) {
        try {
            val request = Request.Builder().url(url).head().build()
            tlsValidationClient.newCall(request).execute().use { }
            TlsValidationResult.Trusted
        } catch (e: SSLException) {
            Timber.w(e, "TLS validation failed for ${sensitive(url)}")
            TlsValidationResult.Untrusted
        } catch (e: IOException) {
            Timber.w(e, "Connection failed during TLS validation for ${sensitive(url)}")
            TlsValidationResult.NetworkError
        }
    }

    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    private fun getActivity(context: Context?): Activity? {
        if (context == null) {
            return null
        } else if (context is ContextWrapper) {
            return context as? Activity ?: getActivity(context.baseContext)
        }
        return null
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        Timber.d("onReceivedClientCertRequest invoked looking for cert in local storage or ask the user for it")
        // Let the WebViewActivity know the endpoint requires TLS Client Auth
        isTLSClientAuthNeeded = true

        // Aim to obtain the private key for the whole lifecycle of the WebViewActivity
        val activity = getActivity(view.context)
        if (activity != null) {
            // If the key is available, process the request
            if (key != null && chain != null) {
                request.proceed(key, chain)
            } else {
                // Get the key and the chain from the repo (if the user previously chose)
                key = keyChainRepository.getPrivateKey()
                chain = keyChainRepository.getCertificateChain()

                if (key != null && chain != null) {
                    checkChainValidity()
                    request.proceed(key, chain)
                } else {
                    // If no key is available, then the user must be prompt for a key
                    // The whole operation is wrapped in the selectPrivateKey method but caution as it must occurs outside of the main thread
                    // see: https://developer.android.com/reference/android/security/KeyChain#getPrivateKey(android.content.Context,%20java.lang.String)
                    selectClientCert(activity, request)
                }
            }
        } else {
            request.ignore()
        }
    }

    private fun selectClientCert(activity: Activity, request: ClientCertRequest) {
        // prompt the user for a key
        try {
            KeyChain.choosePrivateKeyAlias(
                activity,
                SafeKeyChainAliasCallback(keyChainRepository, activity.applicationContext) { key, chain ->
                    if (key == null || chain == null) {
                        hasUserDeniedAccess = true
                        request.ignore()
                    } else {
                        checkChainValidity()
                        this.key = key
                        this.chain = chain
                        request.proceed(key, chain)
                    }
                },
                request.keyTypes,
                request.principals,
                request.host,
                request.port,
                null,
            )
        } catch (e: ActivityNotFoundException) {
            // some cut-down ROMs don't have a client TLS certificate chooser activity (com.android.keychain.CHOOSER)
            // cancel the request so the WebView proceeds without presenting a cert
            Timber.w(e, "Client certificate chooser activity not available, proceeding without cert")
            hasUserDeniedAccess = true
            request.ignore()
        }
    }

    private fun checkChainValidity() {
        if (chain != null) {
            // Ensure the whole certificate chain is valid
            isCertificateChainValid = true
            try {
                chain?.forEach { it.checkValidity() }
            } catch (ex: CertificateException) {
                isCertificateChainValid = false
            }
        }
    }
}

/**
 * Addresses a potential memory leak with [KeyChain.choosePrivateKeyAlias].
 *
 * [KeyChain.choosePrivateKeyAlias] holds a strong reference to its callback even after
 * invocation. To prevent this callback from leaking its capturing context (e.g., a WebView),
 * this wrapper stores the actual result consumer ([onResult]) in a [WeakReference].
 *
 * If the consumer (e.g., WebView) is destroyed before the user selects a key,
 * the [WeakReference] will allow it to be garbage collected, and the result will not be
 * delivered to the (now-gone) consumer. The user's selection is intended to be
 * handled independently by [KeyChainRepository.load] within its coroutine scope,
 * ensuring the choice is persisted even if the initial UI component is gone.
 */
private class SafeKeyChainAliasCallback(
    private var keyChainRepository: KeyChainRepository,
    context: Context,
    onResult: (key: PrivateKey?, chain: Array<X509Certificate>?) -> Unit,
) : KeyChainAliasCallback {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val context = context.applicationContext
    private val onResult = WeakReference(onResult)

    override fun alias(alias: String?) {
        if (alias != null) {
            ioScope.launch {
                keyChainRepository.load(context, alias)
                val key = keyChainRepository.getPrivateKey()
                val chain = keyChainRepository.getCertificateChain()
                onResult.get()?.invoke(key, chain)
            }
        } else {
            onResult.get()?.invoke(null, null)
        }
    }
}
