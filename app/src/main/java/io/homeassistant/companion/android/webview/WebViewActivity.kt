package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import co.lokalise.android.sdk.core.LokaliseContextWrapper
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject
import org.json.JSONObject

class WebViewActivity : AppCompatActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        private const val TAG = "WebviewActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, WebViewActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: WebViewPresenter
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView = findViewById(R.id.webview)
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onJsConfirm(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {
                    AlertDialog
                        .Builder(this@WebViewActivity)
                        .setTitle(R.string.app_name)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                        .setOnDismissListener { result.cancel() }
                        .create()
                        .show()
                    return true
                }
            }

            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun getExternalAuth(callback: String) {
                    presenter.onGetExternalAuth(JSONObject(callback).get("callback") as String)
                }

                @JavascriptInterface
                fun revokeExternalAuth(callback: String) {
                    presenter.onRevokeExternalAuth(JSONObject(callback).get("callback") as String)
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
                            JSONObject(message).get("type") == "config_screen/show" -> startActivity(
                                SettingsActivity.newInstance(this@WebViewActivity)
                            )
                        }
                    }
                }
            }, "externalApp")
        }

        presenter.onViewReady()
    }

    override fun openOnBoarding() {
        finish()
        startActivity(Intent(this, OnboardingActivity::class.java))
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

    override fun setExternalAuth(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }
}
