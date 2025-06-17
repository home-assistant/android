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
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.databinding.ActivityMyBinding
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject
import timber.log.Timber

private const val NAVIGATION_URL_PREFIX = "homeassistant://navigate/"
private const val MOBILE_PARAM = "mobile"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink()
    }

    private fun handleDeepLink() {
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { data ->
            val path = data.path.orEmpty()

            FailFast.failWhen(data.scheme != "https" || data.host != "my.home-assistant.io") {
                "Invalid deep link double check the host or the scheme: $data"
            }

            when {
                path.startsWith("/redirect/") -> handleNavigateDeepLink(data)
                path.startsWith("/invite") -> handleInviteDeepLink(data)
                else -> {
                    FailFast.fail { "Unknown or invalid deep link: $data" }
                    finish()
                }
            }
        }
    }

    private fun handleNavigateDeepLink(data: Uri) {
        if (!serverManager.isRegistered()) {
            Timber.w("No server registered, cannot handle deep link")
            finish()
            return
        }

        if (data.getQueryParameter(MOBILE_PARAM) == "1") {
            Timber.i("No idea why we do this?????")
            finish()
            return
        }

        val uri = data.buildUpon().appendQueryParameter(MOBILE_PARAM, "1").build()

        val binding = ActivityMyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.webview.configureWebView(uri.toString())
    }

    /**
     * We are expecting a deep link like this:
     * https://my.home-assistant.io/invite?url=http://homeassistant.local:8123
     *
     * This function will extract the URL and start the launch activity with it.
     */
    private fun handleInviteDeepLink(data: Uri) {
        val serverURL = data.fragment.orEmpty()
        // For instance: url=http://homeassistant.local:8123

        if (serverURL.isEmpty() || !serverURL.startsWith("url=")) {
            Timber.w("Deep link does not contains a valid URL to a server ($data)")
            finish()
            return
        }
        startActivity(LaunchActivity.newInstanceToSpecificServer(this, serverURL.removePrefix("url=")))
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureWebView(url: String) {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        settings.javaScriptEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val requestUrl = request?.url?.toString()
                if (requestUrl != null && requestUrl.startsWith(NAVIGATION_URL_PREFIX)) {
                    navigateTo(requestUrl.removePrefix(NAVIGATION_URL_PREFIX))
                    return true
                }
                return false
            }
        }
        loadUrl(url)
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
