package io.homeassistant.companion.android.onboarding.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject


class AuthenticationFragment : Fragment(), AuthenticationView {

    companion object {
        private const val TAG = "AuthenticationFragment"

        fun newInstance(): AuthenticationFragment {
            return AuthenticationFragment()
        }
    }

    @Inject lateinit var presenter: AuthenticationPresenter
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
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

    override fun openWebview() {
        (activity as AuthenticationListener).onAuthenticationSuccess()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}