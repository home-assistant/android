package io.homeassistant.companion.android.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject

open class TLSWebViewClient @Inject constructor(private var keyChainRepository: KeyChainRepository) : WebViewClient() {

    var isTLSClientAuthNeeded = false
        private set

    var hasUserDeniedAccess = false
        private set

    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    private fun getActivity(context: Context?): Activity? {
        if (context == null) {
            return null
        } else if (context is ContextWrapper) {
            return if (context is Activity) {
                context
            } else {
                getActivity((context).baseContext)
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        // Let the WebViewActivity know the endpoint requires TLS Client Auth
        isTLSClientAuthNeeded = true

        // Aim to obtain the private key for the whole lifecycle of the WebViewActivity
        val activity = getActivity(view.context)
        if (activity == null) return

        runBlocking {
            launch {
                // If the key is available, process the request
                if (key != null && chain != null) {
                    request.proceed(key, chain)
                } else {
                    // Get the key and the chain from the repo (if the user previously chose)
                    key = keyChainRepository.getPrivateKey()
                    chain = keyChainRepository.getCertificateChain()

                    if (key != null && chain != null) {
                        request.proceed(key, chain)
                    } else {
                        // If no key is available, then the user must be prompt for a key
                        // The whole operation is wrapped in the selectPrivateKey method but caution as it must occurs outside of the main thread
                        // see: https://developer.android.com/reference/android/security/KeyChain#getPrivateKey(android.content.Context,%20java.lang.String)
                        selectClientCert(activity, request.principals, request)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun selectClientCert(activity: Activity, principals: Array<Principal>?, request: ClientCertRequest) {
        var kcac = KeyChainAliasCallback { alias ->
            if (alias != null) {
                runBlocking {
                    // Load the key and the chain
                    keyChainRepository.load(activity.applicationContext, alias!!)

                    key = keyChainRepository.getPrivateKey()
                    chain = keyChainRepository.getCertificateChain()

                    // If we got the key and the cert
                    if (key == null || chain == null) {
                        // Either the user didn't choose a key or no key was available
                        hasUserDeniedAccess = true
                    }
                }
            }

            request.proceed(key, chain)
        }

        // prompt the user for a key
        KeyChain.choosePrivateKeyAlias(activity, kcac, arrayOf<String>(), principals, null, null)
    }
}
