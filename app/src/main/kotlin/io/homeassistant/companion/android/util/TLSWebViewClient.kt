package io.homeassistant.companion.android.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import java.lang.ref.WeakReference
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

open class TLSWebViewClient(private var keyChainRepository: KeyChainRepository) : WebViewClient() {
    var isTLSClientAuthNeeded = false
        private set

    var hasUserDeniedAccess = false
        private set

    var isCertificateChainValid = false
        private set

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
