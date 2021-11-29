package io.homeassistant.companion.android.onboarding.authentication

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.isStarted
import io.homeassistant.companion.android.common.R as commonR

class AuthenticationFragment(
    val presenter: AuthenticationPresenter,
    val themesManager: ThemesManager
) : Fragment(), AuthenticationView {

    companion object {
        private const val TAG = "AuthenticationFragment"
        private const val USER_AGENT_STRING = "HomeAssistant/Android"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        presenter.init(this)
        return inflater.inflate(R.layout.fragment_authentication, container, false).apply {
            webView = findViewById(R.id.webview)
            activity?.applicationContext?.let {
                themesManager.setThemeForWebView(
                    it,
                    webView.settings
                )
            }
            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    USER_AGENT_STRING + " ${Build.MODEL} ${BuildConfig.VERSION_NAME}"
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                        return presenter.onRedirectUrl(url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        showError(commonR.string.webview_error, null, error)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        super.onReceivedSslError(view, handler, error)
                        showError(commonR.string.error_ssl, error, null)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.onViewReady()
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun openWebview() {
        (activity as AuthenticationListener).onAuthenticationSuccess()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun showError(message: Int, sslError: SslError?, error: WebResourceError?) {
        if (!isStarted) {
            // Fragment is at least paused, can't display alert
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(commonR.string.error_connection_failed)
            .setMessage(
                when (sslError?.primaryError) {
                    SslError.SSL_DATE_INVALID -> commonR.string.webview_error_SSL_DATE_INVALID
                    SslError.SSL_EXPIRED -> commonR.string.webview_error_SSL_EXPIRED
                    SslError.SSL_IDMISMATCH -> commonR.string.webview_error_SSL_IDMISMATCH
                    SslError.SSL_INVALID -> commonR.string.webview_error_SSL_INVALID
                    SslError.SSL_NOTYETVALID -> commonR.string.webview_error_SSL_NOTYETVALID
                    SslError.SSL_UNTRUSTED -> commonR.string.webview_error_SSL_UNTRUSTED
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            when (error?.errorCode) {
                                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                                    commonR.string.webview_error_FAILED_SSL_HANDSHAKE
                                WebViewClient.ERROR_AUTHENTICATION -> commonR.string.webview_error_AUTHENTICATION
                                WebViewClient.ERROR_PROXY_AUTHENTICATION -> commonR.string.webview_error_PROXY_AUTHENTICATION
                                WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> commonR.string.webview_error_AUTH_SCHEME
                                WebViewClient.ERROR_HOST_LOOKUP -> commonR.string.webview_error_HOST_LOOKUP
                            }
                        }
                        message
                    }
                }
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
        parentFragmentManager.popBackStack()
    }
}
