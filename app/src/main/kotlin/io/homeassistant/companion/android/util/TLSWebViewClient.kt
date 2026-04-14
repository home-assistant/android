package io.homeassistant.companion.android.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    /**
     * Pre-initializes [isTLSClientAuthNeeded] by verifying whether the currently loaded
     * certificate chain covers [targetHost], to handle TLS session resumption.
     *
     * Normally [isTLSClientAuthNeeded] is set when [onReceivedClientCertRequest] fires during
     * a full TLS handshake. However, when TLS session resumption occurs (the WebView reuses an
     * existing session from the same process), the server does not issue a new
     * `CertificateRequest`, so [onReceivedClientCertRequest] is never called — even if the
     * server requires a client certificate.
     *
     * This is the root cause of the Wear OS onboarding mTLS failure: the main app WebView
     * establishes a TLS session while the user is connected; the onboarding WebView immediately
     * resumes it, bypassing the callback that would reveal the mTLS requirement to the
     * navigation layer.
     *
     * The fix inspects the in-memory certificate chain (if any) and checks whether it covers
     * [targetHost] via its Subject Alternative Names (SANs), or its Common Name (CN) as a
     * fallback. This avoids a false positive when the user has multiple servers where only one
     * requires mTLS: the loaded cert will not match the non-mTLS server's hostname.
     *
     * If the app was force-stopped first (clearing in-memory state) no TLS session can be
     * resumed either, so [onReceivedClientCertRequest] will fire naturally on the fresh handshake.
     *
     * Must be called **before** the WebView starts loading (i.e. before the URL is emitted).
     * Idempotent: if the flag is already `true` (set by a real handshake) this is a no-op.
     *
     * @param targetHost the hostname of the server being connected to (e.g. "myha.example.com")
     */
    fun preInitializeTLSClientAuthState(targetHost: String) {
        if (isTLSClientAuthNeeded) return
        val cert = keyChainRepository.getCertificateChain()?.firstOrNull() ?: return
        isTLSClientAuthNeeded = certCoversHost(cert, targetHost)
    }

    /**
     * Returns `true` if [cert] is valid for [host].
     *
     * Checks Subject Alternative Names (SANs) first — both DNS names (with wildcard support)
     * and IP addresses. Falls back to the Common Name (CN) in the Subject DN if no SANs are
     * present, matching the behaviour of legacy TLS stacks.
     */
    @VisibleForTesting
    internal fun certCoversHost(cert: X509Certificate, host: String): Boolean {
        val sans: Collection<List<*>>? = try {
            cert.subjectAlternativeNames
        } catch (_: Exception) {
            null
        }

        return if (!sans.isNullOrEmpty()) {
            sans.any { san ->
                val type = san[0] as? Int ?: return@any false
                when (type) {
                    2 -> { // dNSName — returned as String
                        val value = san[1] as? String ?: return@any false
                        hostMatchesSan(host, value)
                    }
                    7 -> {
                        // iPAddress — the standard Java X.509 API returns this as a String
                        // (dotted-quad or colon-hex), but some providers (e.g. BouncyCastle)
                        // return a ByteArray; handle both defensively.
                        // Compare via InetAddress equality so that different textual forms of
                        // the same address are treated as equal (e.g. "::1" vs "0:0:0:0:0:0:0:1").
                        val sanAddress: InetAddress = try {
                            when (val ipEntry = san[1]) {
                                is ByteArray -> InetAddress.getByAddress(ipEntry)
                                is String -> InetAddress.getByName(ipEntry)
                                else -> return@any false
                            }
                        } catch (_: UnknownHostException) {
                            return@any false
                        }
                        val hostAddress: InetAddress = try {
                            InetAddress.getByName(host)
                        } catch (_: UnknownHostException) {
                            return@any false
                        }
                        hostAddress == sanAddress
                    }
                    else -> false
                }
            }
        } else {
            // Fallback: extract CN from the Subject DN.
            // getName(RFC2253) uses comma as AVA separator; commas inside values are escaped
            // as \, which we don't need to handle because hostnames never contain commas.
            val dn = cert.subjectX500Principal.getName(X500Principal.RFC2253)
            val cn = dn.splitToSequence(",")
                .map { it.trim() }
                .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
                // Use substring-after-'=' so the extraction is case-insensitive (matches the
                // startsWith check above) rather than a case-sensitive removePrefix("CN=").
                ?.let { it.substring(it.indexOf('=') + 1).trim() }
            cn != null && hostMatchesSan(host, cn)
        }
    }

    /**
     * Matches [host] against a SAN value that may contain a leading wildcard.
     *
     * A wildcard (`*.example.com`) covers any single label: `foo.example.com` matches but
     * `foo.bar.example.com` and `example.com` do not (per RFC 2818 §3.1).
     */
    private fun hostMatchesSan(host: String, san: String): Boolean {
        if (!san.startsWith("*.")) return host.equals(san, ignoreCase = true)
        val suffix = san.substring(1) // ".example.com"
        if (!host.endsWith(suffix, ignoreCase = true)) return false
        val wildcardLabel = host.substring(0, host.length - suffix.length)
        return wildcardLabel.isNotEmpty() && !wildcardLabel.contains('.')
    }

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
