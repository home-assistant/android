package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.SettingsActivity
import org.json.JSONObject


class WebViewActivity : AppCompatActivity(), io.homeassistant.companion.android.webview.WebView {

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
                    presenter.onGetExternalAuth(JSONObject(callback).get("callback") as String)
                }

                @JavascriptInterface
                fun externalBus(message: String) {
                    Log.d(TAG, "External bus $message")
                    webView.post {
                        when {
                            JSONObject(message).get("type") == "config/get" -> {
                                val script = "externalBus(" +
                                        "${JSONObject(
                                            mapOf(
                                                "id" to JSONObject(message).get("id"),
                                                "type" to "result",
                                                "success" to true,
                                                "result" to JSONObject(mapOf("hasSettingsScreen" to true))
                                            )
                                        )}" +
                                        ");"
                                Log.d(TAG, script)
                                webView.evaluateJavascript(script) {
                                    Log.d(TAG, "Callback $it")
                                }
                            }
                            JSONObject(message).get("type") == "config_screen/show" -> startActivity(SettingsActivity.newInstance(this@WebViewActivity))
                        }
                    }
                }
            }, "externalApp")
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

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun setExternalAuth(callback: String, externalAuth: String) {
        webView.post {
            webView.evaluateJavascript("$callback(true, $externalAuth);", null)
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
