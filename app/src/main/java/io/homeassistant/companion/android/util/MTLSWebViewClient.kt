package io.homeassistant.companion.android.util

import android.content.Context
import android.util.Log
import android.webkit.ClientCertRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.common.data.MTLSHelper
import java.security.cert.X509Certificate
import io.homeassistant.companion.android.common.R as commonR

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
        showImportMessageIfNeeded(view.context)
    }

    private fun showMissingClientCertError(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(commonR.string.mtls_cert_not_found_title)
            .setMessage(commonR.string.mtls_cert_not_found_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
    }

    private fun showImportMessageIfNeeded(context: Context) {
        if (MTLSHelper.importErrorMsg != null) {
            AlertDialog.Builder(context)
                .setTitle(commonR.string.mtls_cert_importmsg_title)
                .setMessage(context.getString(commonR.string.mtls_cert_importmsg_message_err) + "\n" + MTLSHelper.importErrorMsg)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
            MTLSHelper.importErrorMsg = null
        }
        if (MTLSHelper.importMsg != null) {
            AlertDialog.Builder(context)
                .setTitle(commonR.string.mtls_cert_importmsg_title)
                .setMessage(MTLSHelper.importMsg)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
            MTLSHelper.importMsg = null
        }
    }
}
