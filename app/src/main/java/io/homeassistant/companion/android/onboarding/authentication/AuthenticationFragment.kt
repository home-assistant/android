package io.homeassistant.companion.android.onboarding.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R


class AuthenticationFragment : Fragment(), AuthenticationView {

    companion object {
        private const val TAG = "AuthenticationFragment"

        fun newInstance(): AuthenticationFragment {
            return AuthenticationFragment()
        }
    }

    private lateinit var presenter: AuthenticationPresenter
    private lateinit var webView: WebView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_authentication, container, false).apply {
            webView = findViewById(R.id.webview)

            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                        return presenter.onRedirectUrl(url)
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
}