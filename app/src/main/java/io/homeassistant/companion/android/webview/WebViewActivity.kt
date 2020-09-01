package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Rational
import android.view.View
import android.webkit.CookieManager
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import eightbitlab.com.blurview.RenderScriptBlur
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.isStarted
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_webview.*
import kotlinx.android.synthetic.main.exo_playback_control_view.*
import org.json.JSONObject

class WebViewActivity : AppCompatActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"

        private const val TAG = "WebviewActivity"
        private const val CAMERA_REQUEST_CODE = 8675309
        private const val AUDIO_REQUEST_CODE = 42
        private const val NFC_COMPLETE = 1

        fun newInstance(context: Context, path: String? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
            }
        }

        private const val CONNECTION_DELAY = 10000L
    }

    @Inject
    lateinit var presenter: WebViewPresenter

    @Inject
    lateinit var themesManager: ThemesManager

    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private lateinit var myCustomView: View
    private lateinit var authenticator: Authenticator
    private lateinit var exoPlayerView: PlayerView

    private var isConnected = false
    private var isShowingError = false
    private var alertDialog: AlertDialog? = null
    private var isVideoFullScreen = false
    private var videoHeight = 0
    private var firstAuthTime: Long = 0
    private var resourceURL: String = ""
    private var unlocked = false
    private var exoPlayer: SimpleExoPlayer? = null
    private var isExoFullScreen = false
    private var exoTop: Int = 0 // These margins are from the DOM and scaled to screen
    private var exoLeft: Int = 0
    private var exoRight: Int = 0
    private var exoBottom: Int = 0
    private var exoMute: Boolean = true

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

        // Start the sensor worker if they start the app. The only other place we start this ia Boot BroadcastReceiver
        SensorWorker.start(this)

        val intent = Intent(this, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_REQUEST_LOCATION_UPDATES
        sendBroadcast(intent)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        blurView.setupWith(root)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(5f)
            .setHasFixedTransformationMatrix(false)

        exoPlayerView = findViewById(R.id.exoplayerView)
        exoPlayerView.visibility = View.GONE
        exoPlayerView.setBackgroundColor(Color.BLACK)
        exoPlayerView.alpha = 1f
        exoPlayerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        exoPlayerView.controllerHideOnTouch = true
        exoPlayerView.controllerShowTimeoutMs = 2000
        exo_fullscreen_icon.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                isExoFullScreen = !isExoFullScreen
                exoResizeLayout()
            }
        })
        exo_mute_icon.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                exoToggleMute()
            }
        })
        if (!presenter.isLockEnabled())
            blurView.setBlurEnabled(false)

        authenticator = Authenticator(this, this, ::authenticationResult)

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
                    var authError = false
                    if (System.currentTimeMillis() <= (firstAuthTime + 500))
                        authError = true
                    authenticationDialog(handler, host, realm, authError)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    Log.e(TAG, "onReceivedHttpError: $error")
                    showError()
                }

                override fun onLoadResource(
                    view: WebView?,
                    url: String?
                ) {
                    resourceURL = url!!
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
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    request.grant(arrayOf(it))
                                } else {
                                    requestPermissions(
                                        arrayOf(android.Manifest.permission.CAMERA),
                                        CAMERA_REQUEST_CODE
                                    )
                                }
                            } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
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
                    decor.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
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
                                    setupPanelShortcuts()
                                }
                            }
                            "config/get" -> {
                                val script = "externalBus(" +
                                        "${JSONObject(
                                            mapOf(
                                                "id" to JSONObject(message).get("id"),
                                                "type" to "result",
                                                "success" to true,
                                                "result" to JSONObject(
                                                    mapOf(
                                                        "hasSettingsScreen" to true,
                                                        "canWriteTag" to true,
                                                        "hasExoPlayer" to true
                                                    )
                                                )
                                            )
                                        )}" +
                                        ");"
                                Log.d(TAG, script)
                                webView.evaluateJavascript(script) {
                                    Log.d(TAG, "Callback $it")
                                }
                            }
                            "config_screen/show" ->
                                startActivity(
                                    SettingsActivity.newInstance(this@WebViewActivity)
                                )
                            "tag/write" ->
                                startActivityForResult(
                                    NfcSetupActivity.newInstance(
                                        this@WebViewActivity,
                                        json.getJSONObject("payload").getString("tag"),
                                        JSONObject(message).getInt("id")
                                    ),
                                    NFC_COMPLETE
                                )
                            "exoplayer/play_hls" -> exoPlayHls(json)
                            "exoplayer/stop" -> exoStopHls()
                            "exoplayer/resize" -> exoResizeHls(json)
                        }
                    }
                }
            }, "externalApp")
        }

        themesManager.setThemeForWebView(this, webView.settings)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                if (presenter.isFullScreen())
                    hideSystemUI()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NFC_COMPLETE && resultCode != -1) {
            val message = mapOf(
                "id" to resultCode,
                "type" to "result",
                "success" to true,
                "result" to mapOf<String, String>()
            )
            webView.evaluateJavascript("externalBus(${JSONObject(message)})") {
                Log.d(TAG, "NFC Write Complete $it")
            }
        }
    }

    fun exoPlayHls(json: JSONObject) {
        val uri = Uri.parse(json.getString("payload"))
        val dataSourceFactory = DefaultHttpDataSourceFactory(
            Util.getUserAgent(
                applicationContext,
                getString(R.string.app_name)
            )
        )
        val hlsMediaSource =
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
            2500,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            2500,
            2500
        ).createDefaultLoadControl()
        runOnUiThread {
            exoPlayer =
                SimpleExoPlayer.Builder(applicationContext).setLoadControl(loadControl)
                    .build()
            exoPlayer?.prepare(hlsMediaSource)
            exoPlayer?.playWhenReady = true
            exoMute = !exoMute
            exoToggleMute()
            exoPlayerView.setPlayer(exoPlayer)
            exoPlayerView.visibility = View.VISIBLE
        }
        val script = "externalBus(" + "${
            JSONObject(
                mapOf(
                    "id" to json.get("id"),
                    "type" to "result",
                    "success" to true,
                    "result" to null
                )
            )
        }" + ");"
        Log.d(TAG, script)
        webView.evaluateJavascript(script) { Log.d(TAG, "Callback $it") }
    }

    fun exoStopHls() {
        runOnUiThread {
            exoPlayerView.visibility = View.GONE
            exoPlayerView.setPlayer(null)
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    fun exoResizeHls(json: JSONObject) {
        val rect = json.getJSONObject("payload")
        val displayMetrics = applicationContext.resources.displayMetrics
        exoLeft = (rect.getInt("left") * displayMetrics.density).toInt()
        exoTop = (rect.getInt("top") * displayMetrics.density).toInt()
        exoRight = (rect.getInt("right") * displayMetrics.density).toInt()
        exoBottom = (rect.getInt("bottom") * displayMetrics.density).toInt()
        runOnUiThread {
            exoResizeLayout()
        }
    }

    fun exoToggleMute() {
        exoMute = !exoMute
        if (exoMute) {
            exoPlayer?.volume = 0f
            exo_mute_icon.setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_volume_off_24
                )
            )
        } else {
            exoPlayer?.volume = 1f
            exo_mute_icon.setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_volume_up_24
                )
            )
        }
    }

    fun exoResizeLayout() {
        val exoLayoutParams = exoPlayerView.layoutParams as FrameLayout.LayoutParams
        if (isExoFullScreen) {
            exoLayoutParams.setMargins(0, 0, 0, 0)
            exoPlayerView.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            exoPlayerView.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            exo_fullscreen_icon.setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_fullscreen_exit_24
                )
            )
            hideSystemUI()
        } else {
            exoPlayerView.layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
            exoPlayerView.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            val screenWidth: Int = resources.displayMetrics.widthPixels
            val screenHeight: Int = resources.displayMetrics.heightPixels
            exoLayoutParams.setMargins(
                exoLeft,
                exoTop,
                maxOf(screenWidth - exoRight, 0),
                maxOf(screenHeight - exoBottom, 0)
            )
            exo_fullscreen_icon.setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_fullscreen_24
                )
            )
            showSystemUI()
        }
        exoPlayerView.requestLayout()
    }

    private fun authenticationResult(result: Int) {
        if (result == Authenticator.SUCCESS) {
            unlocked = true
            blurView.setBlurEnabled(false)
        } else finishAffinity()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (presenter.isLockEnabled() && !unlocked)
                if ((System.currentTimeMillis() > presenter.getSessionExpireMillis())) {
                    blurView.setBlurEnabled(true)
                    authenticator.authenticate()
                } else blurView.setBlurEnabled(false)

            presenter.onViewReady(intent.getStringExtra(EXTRA_PATH))
            intent.removeExtra(EXTRA_PATH)

            if (presenter.isFullScreen())
                hideSystemUI()
            else
                showSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (isCutout())
            decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        else {
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
            decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun showSystemUI() {
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (exoPlayerView.visibility != View.VISIBLE) {
            if (isInPictureInPictureMode) {
                (decor.getChildAt(3) as FrameLayout).layoutParams.height =
                    FrameLayout.LayoutParams.MATCH_PARENT
                decor.requestLayout()
            } else {
                if (decor.getChildAt(3) != null) {
                    (decor.getChildAt(3) as FrameLayout).layoutParams.height = videoHeight
                    decor.requestLayout()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        presenter.setSessionExpireMillis((System.currentTimeMillis() + (presenter.sessionTimeOut() * 1000)))
        unlocked = false
        videoHeight = decor.height
        val bounds = Rect(0, 0, 1920, 1080)
        if (isVideoFullScreen or isExoFullScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()
                mPictureInPictureParamsBuilder.setAspectRatio(
                    Rational(
                        bounds.width(),
                        bounds.height()
                    )
                )
                mPictureInPictureParamsBuilder.setSourceRectHint(bounds)
                enterPictureInPictureMode(mPictureInPictureParamsBuilder.build())
            }
        }
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

    override fun setStatusBarAndNavigationBarColor(color: Int) {
        var flags = window.decorView.systemUiVisibility
        flags = if (ColorUtils.calculateLuminance(color) < 0.5) { // If color is dark...
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() // Remove light flag
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR // Add light flag
        }
        window.statusBarColor = color
        window.navigationBarColor = color
        window.decorView.systemUiVisibility = flags
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

    override fun showError(isAuthenticationError: Boolean, error: SslError?, description: String?) {
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
        } else if (error != null || description != null) {
            if (description != null)
                alert.setMessage(getString(R.string.webview_error_description) + " " + description)
            else if (error!!.primaryError == SslError.SSL_DATE_INVALID)
                alert.setMessage(R.string.webview_error_SSL_DATE_INVALID)
            else if (error.primaryError == SslError.SSL_EXPIRED)
                alert.setMessage(R.string.webview_error_SSL_EXPIRED)
            else if (error.primaryError == SslError.SSL_IDMISMATCH)
                alert.setMessage(R.string.webview_error_SSL_IDMISMATCH)
            else if (error.primaryError == SslError.SSL_INVALID)
                alert.setMessage(R.string.webview_error_SSL_INVALID)
            else if (error.primaryError == SslError.SSL_NOTYETVALID)
                alert.setMessage(R.string.webview_error_SSL_NOTYETVALID)
            else if (error.primaryError == SslError.SSL_UNTRUSTED)
                alert.setMessage(R.string.webview_error_SSL_UNTRUSTED)
            alert.setPositiveButton(R.string.settings) { _, _ ->
                startActivity(SettingsActivity.newInstance(this))
            }
            alert.setNeutralButton(R.string.exit) { _, _ ->
                finishAffinity()
            }
        } else {
            alert.setMessage(R.string.webview_error)
            alert.setPositiveButton(R.string.settings) { _, _ ->
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
    fun authenticationDialog(
        handler: HttpAuthHandler,
        host: String,
        realm: String,
        authError: Boolean
    ) {
        val authenticationDao = AppDatabase.getInstance(applicationContext).authenticationDao()
        val httpAuth = authenticationDao.get((resourceURL + realm))

        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_authentication, null)
        val username = dialogLayout.findViewById<EditText>(R.id.username)
        val password = dialogLayout.findViewById<EditText>(R.id.password)
        val remember = dialogLayout.findViewById<CheckBox>(R.id.checkBox)
        val viewPassword = dialogLayout.findViewById<ImageView>(R.id.viewPassword)
        var autoAuth = false

        viewPassword.setOnClickListener() {
            if (password.transformationMethod == PasswordTransformationMethod.getInstance()) {
                password.transformationMethod = HideReturnsTransformationMethod.getInstance()
                viewPassword.setImageResource(R.drawable.ic_visibility_off)
                password.setSelection(password.text.length)
            } else {
                password.transformationMethod = PasswordTransformationMethod.getInstance()
                viewPassword.setImageResource(R.drawable.ic_visibility)
                password.setSelection(password.text.length)
            }
        }

        if (!httpAuth?.host.isNullOrBlank()) {
            if (!authError) {
                handler.proceed(httpAuth?.username, httpAuth?.password)
                autoAuth = true
                firstAuthTime = System.currentTimeMillis()
            }
        }

        var message = host + " " + getString(R.string.required_fields)
        if (resourceURL.subSequence(0, 5).toString() == "http:")
            message = "http://" + message + " " + getString(R.string.not_private)
        else
            message = "https://" + message

        if (!autoAuth || authError) {
            AlertDialog.Builder(this, R.style.Authentication_Dialog)
                .setTitle(R.string.auth_request)
                .setMessage(message)
                .setView(dialogLayout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (username.text.toString() != "" && password.text.toString() != "") {
                        if (remember.isChecked) {
                            if (authError)
                                authenticationDao.update(
                                    Authentication(
                                        (resourceURL + realm),
                                        username.text.toString(),
                                        password.text.toString()
                                    )
                                )
                            else
                                authenticationDao.insert(
                                    Authentication(
                                        (resourceURL + realm),
                                        username.text.toString(),
                                        password.text.toString()
                                    )
                                )
                        }
                        handler.proceed(username.text.toString(), password.text.toString())
                    } else AlertDialog.Builder(this)
                        .setTitle(R.string.auth_cancel)
                        .setMessage(R.string.auth_error_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            authenticationDialog(handler, host, realm, authError)
                        }
                        .show()
                }
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    Toast.makeText(applicationContext, R.string.auth_cancel, Toast.LENGTH_SHORT)
                        .show()
                }
                .show()
        }
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
        }, CONNECTION_DELAY)
    }

    private fun setupPanelShortcuts() {
        if (Build.VERSION.SDK_INT >= 25) {
            val panels = presenter.getPanels()

            val shortcutManager = getSystemService(ShortcutManager::class.java)
            shortcutManager!!.dynamicShortcuts = panels
                .filter { panel -> !panel.title.isNullOrEmpty() && panel.component_name.contains("lovelace") }
                .take(5)
                .map { panel ->
                    ShortcutInfo.Builder(
                        this,
                        panel.component_name
                    )
                        .setShortLabel(panel.title!!)
                        .setLongLabel(panel.title!!)
                        .setIcon(Icon.createWithResource(this, R.drawable.app_icon))
                        .setIntent(
                            newInstance(this, panel.url_path).apply {
                                this.action = Intent.ACTION_VIEW
                            }
                        )
                        .build()
                }
        }
    }
}
