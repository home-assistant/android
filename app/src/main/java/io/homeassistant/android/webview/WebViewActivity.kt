package io.homeassistant.android.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.android.BuildConfig
import io.homeassistant.android.R
import io.homeassistant.android.api.Session
import io.homeassistant.android.io.homeassistant.android.api.Token
import org.json.JSONObject


class WebViewActivity : AppCompatActivity(), io.homeassistant.android.webview.WebView {

    companion object {
        private const val TAG = "WebviewActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, WebViewActivity::class.java)
        }
    }

    private lateinit var webView: WebView
    private lateinit var presenter: WebViewPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        presenter = WebViewPresenterImpl(this)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView = findViewById(R.id.webview)
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }

        presenter.onViewReady()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun setupJavascriptInterface(token: Token) {
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun getExternalAuth(callback: String) {
                webView.post {
                    webView.evaluateJavascript(
                        "${JSONObject(callback).get("callback")}(true, {\n" +
                                "  \"access_token\": \"${token.accessToken}\",\n" +
                                "  \"expires_in\": ${token.expiresIn()}\n" +
                                "});"
                        , null
                    )
                }
            }
        }, "externalApp")
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(
            Uri.parse(Session.getInstance().url ?: throw IllegalArgumentException("url should not be null"))
                .buildUpon()
                .appendQueryParameter("external_auth", "1")
                .toString()
        )
    }
}
