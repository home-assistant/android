package io.homeassistant.companion.android.launch.my

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.databinding.ActivityMyBinding
import io.homeassistant.companion.android.webview.WebViewActivity

class MyActivity : BaseActivity() {

    companion object {
        private const val EXTRA_URI = "EXTRA_URI"

        fun newInstance(context: Context, uri: Uri): Intent {
            return Intent(context, MyActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Intent.ACTION_VIEW == intent?.action && intent.data != null) {
            if (intent.data?.getQueryParameter("mobile")?.equals("1") == true) {
                finish()
                return
            }
            val newUri = intent.data!!.buildUpon().appendQueryParameter("mobile", "1").build()

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            binding.webview.apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url.toString()
                        if (url.startsWith("homeassistant://navigate/")) {
                            startActivity(WebViewActivity.newInstance(context, url.removePrefix("homeassistant://navigate/")))
                            finish()
                            return true
                        }
                        return false
                    }
                }
            }
            binding.webview.loadUrl(newUri.toString())
        }
    }
}
