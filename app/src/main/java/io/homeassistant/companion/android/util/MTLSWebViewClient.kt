package io.homeassistant.companion.android.util

import android.content.Context
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.common.data.MTLSHelper
import java.security.cert.X509Certificate

open class MTLSWebViewClient : WebViewClient() {
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        try {
            if (MTLSHelper.clientPrivateKey != null) {
                val cert = MTLSHelper.clientPublicKey as X509Certificate
                val key = MTLSHelper.clientPrivateKey
                val certlist = arrayOf(cert)
                request.proceed(key, certlist)
            } else {
                super.onReceivedClientCertRequest(view, request)
                showMissingClientCertError(view.context)
            }
        } catch (e: Exception) {
            Log.e(
                "MtlsWebViewClient",
                "Error when getting CertificateChain or PrivateKey",
                e
            )
            super.onReceivedClientCertRequest(view, request)
        }
    }

    private fun showMissingClientCertError(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("TLS Client certificate not available")
            .setMessage(
                "Remote site asked for client Certificate\nPlace tls_client.key and tls_client.crt in android/data/io.homeassistant.companion.android/files and restart application"
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }
}
