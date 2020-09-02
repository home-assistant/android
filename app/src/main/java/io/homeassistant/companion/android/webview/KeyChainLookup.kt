package io.homeassistant.companion.android.webview

import android.content.Context
import android.os.AsyncTask
import android.security.KeyChain
import android.security.KeyChainException
import android.webkit.ClientCertRequest
import java.security.PrivateKey
import java.security.cert.X509Certificate

internal class KeyChainLookup(context: Context, handler: ClientCertRequest, alias: String) :
    AsyncTask<Void?, Void?, Void?>() {
    private val mContext: Context = context.applicationContext
    private val mHandler: ClientCertRequest = handler
    private val mAlias: String = alias

    override fun doInBackground(vararg params: Void?): Void? {
        val privateKey: PrivateKey?
        val certificateChain: Array<X509Certificate>?
        try {
            privateKey = KeyChain.getPrivateKey(mContext, mAlias)
            certificateChain = KeyChain.getCertificateChain(mContext, mAlias)
        } catch (e: InterruptedException) {
            mHandler.ignore()
            return null
        } catch (e: KeyChainException) {
            mHandler.ignore()
            return null
        }
        mHandler.proceed(privateKey, certificateChain)
        return null
    }
}
