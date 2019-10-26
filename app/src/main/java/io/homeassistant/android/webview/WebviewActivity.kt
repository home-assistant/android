package io.homeassistant.android.webview

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.android.R


class WebviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebviewActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        findViewById<WebView>(R.id.webview).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://demo.home-assistant.io")
        }
    }
}
