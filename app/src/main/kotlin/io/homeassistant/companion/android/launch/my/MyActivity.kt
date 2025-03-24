package io.homeassistant.companion.android.launch.my

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.databinding.ActivityMyBinding
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

@AndroidEntryPoint
class MyActivity : BaseActivity() {

    companion object {
        private const val EXTRA_URI = "EXTRA_URI"

        fun newInstance(context: Context, uri: Uri): Intent {
            return Intent(context, MyActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
            }
        }
    }

    @Inject
    lateinit var serverManager: ServerManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!serverManager.isRegistered()) {
            finish()
            return
        }

        val binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            if (
                intent.data?.scheme != "https" ||
                intent.data?.host != "my.home-assistant.io" ||
                intent.data?.path?.startsWith("/redirect/") != true ||
                intent.data?.getQueryParameter("mobile")?.equals("1") == true
            ) {
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
                            navigateTo(url.removePrefix("homeassistant://navigate/"))
                            return true
                        }
                        return false
                    }
                }
            }
            binding.webview.loadUrl(newUri.toString())
        }
    }

    private fun navigateTo(path: String) {
        if (serverManager.defaultServers.size > 1) {
            supportFragmentManager.setFragmentResultListener(ServerChooserFragment.RESULT_KEY, this) { _, bundle ->
                if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                    startActivity(
                        WebViewActivity.newInstance(
                            context = this,
                            path = path,
                            serverId = bundle.getInt(ServerChooserFragment.RESULT_SERVER)
                        )
                    )
                    finish()
                }
                supportFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
            }
            ServerChooserFragment().show(supportFragmentManager, ServerChooserFragment.TAG)
        } else {
            startActivity(WebViewActivity.newInstance(context = this, path = path))
            finish()
        }
    }
}
