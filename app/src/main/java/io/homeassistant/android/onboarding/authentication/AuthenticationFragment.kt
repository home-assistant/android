package io.homeassistant.android.onboarding.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.homeassistant.android.R


class AuthenticationFragment : Fragment(), AuthenticationView {

    companion object {
        private const val TAG = "AuthenticationFragment"
        private const val EXTRA_URL = "extra_url"

        fun newInstance(url: String): AuthenticationFragment {
            return AuthenticationFragment().apply {
                this.arguments = Bundle().apply {
                    this.putString(EXTRA_URL, url)
                }
            }
        }
    }

    private lateinit var presenter: AuthenticationPresenter
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = AuthenticationPresenterImpl(this)
        presenter.initialize(arguments?.getString(EXTRA_URL) ?: throw IllegalArgumentException("Url should not be null"))
    }

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

    override fun openWebview(url: String) {
        (activity as AuthenticationListener).onAuthenticationSuccess(url)
    }

}