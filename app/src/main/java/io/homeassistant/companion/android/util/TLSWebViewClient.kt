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
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import kotlinx.coroutines.runBlocking
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

open class TLSWebViewClient @Inject constructor(private var keyChainRepository: KeyChainRepository) : WebViewClient() {

    var isTLSClientAuthNeeded = false
        private set

    var hasUserDeniedAccess = false
        private set

    class MyKeyChainCallbackAlias(var context: Context) : KeyChainAliasCallback {
        var alias: String? = null
        var ready: Boolean = false

        override fun alias(alias: String?) {
            this.alias = alias
            ready = true
        }
    }

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

        var key: PrivateKey?
        var chain: Array<X509Certificate>?

        // Get the key and the chain (if the user previously chose)
        runBlocking {
            key = keyChainRepository.getPrivateKey()
            chain = keyChainRepository.getCertificateChain()
        }

        // If the key is available, process the request
        if (key != null && chain != null) {
            request.proceed(key, chain)
        } else {
            // If not, then the user must be prompt for a key
            // The whole operation is wrapped in the selectPrivateKey method but caution as it must occurs outside of the main thread
            // see: https://developer.android.com/reference/android/security/KeyChain#getPrivateKey(android.content.Context,%20java.lang.String)
            // Also from now on displaying error message on the UI is more tricky (not on main thread)

            //Executors.newSingleThreadExecutor().execute {
                val alias = selectClientCert(activity!!, request.principals)
                // null if the user denied access to the key
                if (alias != null) {
                    // The key should be available now
                    runBlocking {
                        key = keyChainRepository.getPrivateKey()
                        chain = keyChainRepository.getCertificateChain()
                    }

                    // If we got the key and the chain, then proceed with the request
                    if (key != null && chain != null) {
                        request.proceed(key, chain)
                    }
                } else {
                    hasUserDeniedAccess = true
                }
            //}
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun selectClientCert(activity: Activity, principals: Array<Principal>?): String? {
        // prompt the user for a key
        val kcac = MyKeyChainCallbackAlias(activity.applicationContext)
        KeyChain.choosePrivateKeyAlias(activity, kcac, arrayOf<String>(), principals, null, null)

        // wait on the user to select a key
        while (!kcac.ready) {
            Thread.sleep(500)
        }

        // load the key and the cert chain
        runBlocking {
            if (kcac.alias != null) {
                keyChainRepository.load(activity.applicationContext, kcac.alias!!)
            }
        }

        return kcac.alias
    }
}