package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Rational
import android.view.MenuInflater
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lokalise.sdk.LokaliseContextWrapper
import com.lokalise.sdk.menu_inflater.LokaliseMenuInflater
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.PermissionManager
import io.homeassistant.companion.android.util.isStarted
import javax.inject.Inject
import org.json.JSONObject

class WebViewActivity : AppCompatActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"

        private const val TAG = "WebviewActivity"
        private const val CAMERA_REQUEST_CODE = 8675309
        private const val AUDIO_REQUEST_CODE = 42

        fun newInstance(context: Context, path: String? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
            }
        }
    }

    @Inject
    lateinit var presenter: WebViewPresenter
    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private lateinit var myCustomView: View

    private var isConnected = false
    private var isShowingError = false
    private var alertDialog: AlertDialog? = null
    private var isVideoFullScreen = false
    private var videoHeight = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        val intent = Intent(this, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES
        sendBroadcast(intent)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        decor = window.decorView as FrameLayout

        webView = findViewById(R.id.webview)
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.e(TAG, "onReceivedHttpError: errorCode: $errorCode url:$failingUrl")
                    if (failingUrl == loadedUrl) {
                        showError()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    Log.e(TAG, "onReceivedHttpError: $errorResponse")
                    if (request?.url.toString() == loadedUrl) {
                        showError()
                    }
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView,
                    handler: HttpAuthHandler,
                    host: String,
                    realm: String
                ) {
                    authenticationDialog(handler)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    Log.e(TAG, "onReceivedHttpError: $error")
                    showError()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    request?.url?.let {
                        if (!webView.url.toString().contains(it.toString())) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, it)
                            startActivity(browserIntent)
                            return true
                        }
                    }
                    return false
                }
            }

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

                override fun onPermissionRequest(request: PermissionRequest?) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        request?.resources?.forEach {
                            if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                if (PermissionManager.hasPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    )
                                ) {
                                    request.grant(arrayOf(it))
                                } else {
                                    requestPermissions(
                                        arrayOf(android.Manifest.permission.CAMERA),
                                        CAMERA_REQUEST_CODE
                                    )
                                }
                            } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (PermissionManager.hasPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                ) {
                                    request.grant(arrayOf(it))
                                } else {
                                    requestPermissions(
                                        arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                        AUDIO_REQUEST_CODE
                                    )
                                }
                            }
                        }
                    } else {
                        // If we are before M we already have permission, just grant it.
                        request?.grant(request.resources)
                    }
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    myCustomView = view
                    decor.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                    hideSystemUI()
                    isVideoFullScreen = true
                }

                override fun onHideCustomView() {
                    decor.removeView(myCustomView)
                    if (!presenter.isFullScreen())
                        showSystemUI()
                    isVideoFullScreen = false
                    super.onHideCustomView()
                }
            }

            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun getExternalAuth(payload: String) {
                    JSONObject(payload).let {
                        presenter.onGetExternalAuth(
                            it.getString("callback"),
                            it.has("force") && it.getBoolean("force")
                        )
                    }
                }

                @JavascriptInterface
                fun revokeExternalAuth(callback: String) {
                    presenter.onRevokeExternalAuth(JSONObject(callback).get("callback") as String)
                    openOnBoarding()
                    finish()
                }

                @JavascriptInterface
                fun externalBus(message: String) {
                    Log.d(TAG, "External bus $message")
                    webView.post {
                        val json = JSONObject(message)
                        when (json.get("type")) {
                            "connection-status" -> {
                                isConnected = json.getJSONObject("payload")
                                    .getString("event") == "connected"
                                if (isConnected) {
                                    alertDialog?.cancel()
                                }
                            }
                            "config/get" -> {
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
                            "config_screen/show" -> startActivity(
                                SettingsActivity.newInstance(this@WebViewActivity)
                            )
                        }
                    }
                }
            }, "externalApp")
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                if (presenter.isFullScreen())
                    hideSystemUI()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            presenter.onViewReady(intent.getStringExtra(EXTRA_PATH))
            intent.removeExtra(EXTRA_PATH)
            if (presenter.isFullScreen())
                hideSystemUI()
            else
                showSystemUI()
        }
    }

    private fun hideSystemUI() {
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            decor.getWindowVisibleDisplayFrame(r)
            val height = r.bottom - decor.top

            if ((decor.height - height) > (decor.height / 5))
                decor.getChildAt(0).layoutParams.height = decor.height - (decor.height - height)
            else
                decor.getChildAt(0).layoutParams.height = decor.height

            decor.requestLayout()
        }

        if (isCutout())
            decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        else
            decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun showSystemUI() {
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (isInPictureInPictureMode) {
            (decor.getChildAt(3) as FrameLayout).layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            decor.requestLayout()
        } else {
            if (decor.getChildAt(3) != null) {
                (decor.getChildAt(3) as FrameLayout).layoutParams.height = videoHeight
                decor.requestLayout()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        videoHeight = decor.height
        var bounds = Rect(0, 0, 1920, 1080)
        if (isVideoFullScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()
                mPictureInPictureParamsBuilder.setAspectRatio(Rational(bounds.width(), bounds.height()))
                mPictureInPictureParamsBuilder.setSourceRectHint(bounds)
                enterPictureInPictureMode(mPictureInPictureParamsBuilder.build())
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }

    override fun getMenuInflater(): MenuInflater {
        return LokaliseMenuInflater(this)
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
        loadedUrl = url
        webView.loadUrl(url)
        waitForConnection()
    }

    override fun setStatusBarColor(color: Int) {
        window.statusBarColor = color
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

    override fun showError(isAuthenticationError: Boolean) {
        if (isShowingError || !isStarted)
            return
        isShowingError = true

        val alert = AlertDialog.Builder(this)
            .setTitle(R.string.error_connection_failed)
            .setOnDismissListener {
                isShowingError = false
                alertDialog = null
                waitForConnection()
            }

        if (isAuthenticationError) {
            alert.setMessage(R.string.error_auth_revoked)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.clearKnownUrls()
                openOnBoarding()
            }
        } else {
            alert.setMessage(R.string.webview_error)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                startActivity(SettingsActivity.newInstance(this))
            }
            alert.setNegativeButton(R.string.refresh) { _, _ ->
                webView.reload()
                waitForConnection()
            }
            alert.setNeutralButton(R.string.wait) { _, _ ->
                waitForConnection()
            }
        }
        alertDialog = alert.create()
        alertDialog?.show()
    }

    @SuppressLint("InflateParams")
    override fun authenticationDialog(handler: HttpAuthHandler) {
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_authentication, null)
        val username = dialogLayout.findViewById<EditText>(R.id.username)
        val password = dialogLayout.findViewById<EditText>(R.id.password)

        AlertDialog.Builder(this)
            .setTitle(R.string.auth_request)
            .setView(dialogLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                handler.proceed(username.text.toString(), password.text.toString())
            }
            .setNeutralButton(android.R.string.cancel) { _, _ ->
                Toast.makeText(applicationContext, R.string.auth_cancel, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_REQUEST_CODE || requestCode == AUDIO_REQUEST_CODE) {
            webView.reload()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun isCutout(): Boolean {
        var cutout = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window.decorView.rootWindowInsets.displayCutout != null)
            cutout = true
        return cutout
    }

    private fun waitForConnection() {
        Handler().postDelayed({
            if (!isConnected) {
                showError()
            }
        }, 5000)
    }
}
