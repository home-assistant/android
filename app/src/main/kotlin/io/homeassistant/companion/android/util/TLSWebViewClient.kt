package io.homeassistant.companion.android.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import java.lang.ref.WeakReference
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/*
 * [TLSWebViewClient] is on the onboarding module for convenience, since we don't have yet
 * a place to share components between app modules. Common is shared with wear and
 * we don't want the webview code in the wear app.
 */

open class TLSWebViewClient(private var keyChainRepository: KeyChainRepository) : WebViewClient() {
    var isTLSClientAuthNeeded = false
        @VisibleForTesting set

    var hasUserDeniedAccess = false
        private set

    var isCertificateChainValid = false
        @VisibleForTesting set

    /**
     * Host of the Home Assistant server the WebView is anchored to (e.g. `ha.local`,
     * `home.example.com`). Used to distinguish user-initiated external link taps from
     * in-flight authentication flows on third-party domains.
     *
     * When set, any main-frame http(s) navigation that happens while the WebView is
     * currently on a host other than [serverHost] is treated as part of an ongoing
     * authentication handshake (for example a Cloudflare Access login page redirecting
     * to Google Workspace or an Authelia instance redirecting to a WebAuthn challenge)
     * and is kept inside the WebView. Once the flow returns to [serverHost] the
     * original external-link behaviour resumes.
     *
     * When null, only server-initiated redirects are kept in the WebView.
     */
    var serverHost: String? = null

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

    /**
     * Allows the WebView to follow authentication-provider navigation on the main frame
     * instead of handing it off to the system browser.
     *
     * This enables authentication proxies placed in front of Home Assistant (for example
     * Cloudflare Access, Authelia, Authentik, or any other OIDC / OAuth 2.0 compliant
     * reverse proxy) to complete their login handshake inside the app WebView. Two forms
     * of auth-provider navigation are kept in-WebView:
     *
     * 1. Server-initiated redirects on the main frame (the initial auth challenge from
     *    the HA origin to the proxy, or the proxy's return redirect back to HA).
     * 2. Any main-frame http(s) navigation that happens while the WebView is currently
     *    sitting on a host other than [serverHost]. This covers the common case where
     *    the auth proxy page requires the user to tap a federated login button (e.g.
     *    "Sign in with Google Workspace") that would otherwise be classed as user-
     *    initiated and ejected to the system browser.
     *
     * User-initiated external links from the HA frontend are not affected: when the
     * WebView is currently on [serverHost] and the user taps a link to an external
     * resource, the call falls through to the existing deprecated override and the
     * link still opens in the system browser.
     *
     * On Android versions below API 24 this override is never invoked; the deprecated
     * overload is called directly by the framework, preserving existing behaviour. The
     * [SuppressLint] below reflects that framework contract — the method body safely
     * accesses API 24+ members (`WebResourceRequest.isRedirect`, `isForMainFrame`)
     * because the framework does not dispatch this overload on older platforms.
     */
    @SuppressLint("NewApi")
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (isAuthProviderNavigation(view, request)) {
            Timber.d(
                "Allowing main-frame navigation to ${sensitive(
                    request?.url?.host.orEmpty(),
                )} to load in WebView (auth flow)",
            )
            return false
        }
        @Suppress("DEPRECATION")
        return shouldOverrideUrlLoading(view, request?.url?.toString())
    }

    // Only called from [shouldOverrideUrlLoading] above (which the framework only
    // dispatches on API 24+) and from unit tests (which run on the JVM and mock the
    // API 24+ members directly). The SuppressLint covers both call paths.
    @SuppressLint("NewApi")
    @VisibleForTesting
    internal fun isAuthProviderNavigation(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request == null) return false
        if (!request.isForMainFrame) return false
        val scheme = request.url?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        if (request.isRedirect) return true

        val anchor = serverHost ?: return false
        val currentHost = view?.url?.toHttpUrlOrNull()?.host ?: return false
        return !currentHost.equals(anchor, ignoreCase = true)
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
