package io.homeassistant.android.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.android.BuildConfig
import io.homeassistant.android.R
import io.homeassistant.android.api.Session
import org.json.JSONObject


class WebviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebviewActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, WebviewActivity::class.java)
        }
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView = findViewById(R.id.webview)
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()

            addJavascriptInterface(object : Any() {

                @JavascriptInterface
                fun getExternalAuth(callback: String) {
                    webView.post {
                        webView.evaluateJavascript(
                            "${JSONObject(callback).get("callback")}(true, {\n" +
                                    "  \"access_token\": \"${Session.getInstance().token?.accessToken}\",\n" +
                                    "  \"expires_in\": ${Session.getInstance().token?.expiresIn}\n" +
                                    "});"
                            , null
                        )
                    }
                }
            }, "externalApp")
        }

        webView.loadUrl(
            Uri.parse(Session.getInstance().url ?: throw IllegalArgumentException("url should not be null"))
                .buildUpon()
                .appendQueryParameter("external_auth", "1")
                .toString()
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
