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
import org.json.JSONObject


class WebviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebviewActivity"
        private const val EXTRA_URL = "extra_url"

        fun newInstance(context: Context, url: String): Intent {
            return Intent(context, WebviewActivity::class.java)
                .putExtra(EXTRA_URL, url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val webView = findViewById<WebView>(R.id.webview)
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
                                    "  \"access_token\": \"${Session.token?.accessToken}\",\n" +
                                    "  \"expires_in\": ${Session.token?.expiresIn}\n" +
                                    "});"
                            , null
                        )
                    }
                }
            }, "externalApp")
        }

        webView.loadUrl(
            Uri.parse(intent.extras?.getString(EXTRA_URL) ?: throw IllegalArgumentException("url should not be null"))
                .buildUpon()
                .appendQueryParameter("external_auth", "1")
                .toString()
        )
    }
}
