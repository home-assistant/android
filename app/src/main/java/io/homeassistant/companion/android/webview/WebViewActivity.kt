package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.webkit.WebViewCompat
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.cronet.CronetDataSource
import com.google.android.exoplayer2.ext.cronet.CronetEngineWrapper
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.material.textfield.TextInputEditText
import eightbitlab.com.blurview.RenderScriptBlur
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.language.LanguagesManager
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.util.isStarted
import kotlinx.android.synthetic.main.activity_webview.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import kotlinx.android.synthetic.main.exo_player_view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors
import javax.inject.Inject

class WebViewActivity : BaseActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"

        private const val TAG = "WebviewActivity"
        private const val CAMERA_REQUEST_CODE = 8675309
        private const val AUDIO_REQUEST_CODE = 42
        private const val NFC_COMPLETE = 1
        private const val FILE_CHOOSER_RESULT_CODE = 15
        private const val APP_PREFIX = "app://"
        private const val INTENT_PREFIX = "intent://"
        private const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="

        fun newInstance(context: Context, path: String? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
            }
        }

        private const val CONNECTION_DELAY = 10000L
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Inject
    lateinit var presenter: WebViewPresenter

    @Inject
    lateinit var themesManager: ThemesManager

    @Inject
    lateinit var languagesManager: LanguagesManager

    @Inject
    lateinit var urlRepository: UrlRepository

    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private lateinit var myCustomView: View
    private lateinit var authenticator: Authenticator
    private lateinit var exoPlayerView: PlayerView
    private lateinit var currentLang: String

    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
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
    private var failedConnection = "external"
    private var moreInfoEntity = ""

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

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        blurView.setupWith(root)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(5f)
            .setHasFixedTransformationMatrix(false)

        exoPlayerView = findViewById<PlayerView>(R.id.exoplayerView)
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
        if (!presenter.isLockEnabled()) {
            blurView.setBlurEnabled(false)
            unlocked = true
        }

        authenticator = Authenticator(this, this, ::authenticationResult)

        decor = window.decorView as FrameLayout

        webView = findViewById(R.id.webview)
        webView.apply {
            setOnTouchListener { _, motionEvent ->
                if (motionEvent.pointerCount == 3 && motionEvent.action == MotionEvent.ACTION_POINTER_3_DOWN) {
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
                }
                return@setOnTouchListener !unlocked
            }

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

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (moreInfoEntity != "" && view?.progress == 100 && isConnected) {
                        ioScope.launch {
                            delay(2000L)
                            Log.d(TAG, "More info entity: $moreInfoEntity")
                            webView.evaluateJavascript(
                                "document.querySelector(\"home-assistant\").dispatchEvent(new CustomEvent(\"hass-more-info\", { detail: { entityId: \"$moreInfoEntity\" }}))",
                                null
                            )
                            moreInfoEntity = ""
                        }
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
                        try {
                            if (it.toString().startsWith(APP_PREFIX)) {
                                Log.d(TAG, "Launching the app")
                                val intent = packageManager.getLaunchIntentForPackage(
                                    it.toString().substringAfter(APP_PREFIX)
                                )
                                if (intent != null) {
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    startActivity(intent)
                                } else {
                                    Log.w(TAG, "No intent to launch app found, opening app store")
                                    val marketIntent = Intent(Intent.ACTION_VIEW)
                                    marketIntent.data = Uri.parse(
                                        MARKET_PREFIX + it.toString().substringAfter(APP_PREFIX)
                                    )
                                    startActivity(marketIntent)
                                }
                                return true
                            } else if (it.toString().startsWith(INTENT_PREFIX)) {
                                Log.d(TAG, "Launching the intent")
                                val intent =
                                    Intent.parseUri(it.toString(), Intent.URI_INTENT_SCHEME)
                                val intentPackage = intent.`package`?.let { it1 ->
                                    packageManager.getLaunchIntentForPackage(
                                        it1
                                    )
                                }
                                if (intentPackage != null)
                                    startActivity(intent)
                                else {
                                    Log.w(TAG, "No app found for intent prefix, opening app store")
                                    val marketIntent = Intent(Intent.ACTION_VIEW)
                                    marketIntent.data =
                                        Uri.parse(MARKET_PREFIX + intent.`package`.toString())
                                    startActivity(marketIntent)
                                }
                                return true
                            } else if (!webView.url.toString().contains(it.toString())) {
                                val browserIntent = Intent(Intent.ACTION_VIEW, it)
                                startActivity(browserIntent)
                                return true
                            } else
                                Log.d(TAG, "No unique cases found to override")
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to override the URL", e)
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

                override fun onShowFileChooser(
                    view: WebView,
                    uploadMsg: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    mFilePathCallback = uploadMsg
                    val i = fileChooserParams.createIntent()
                    i.type = "*/*"
                    startActivityForResult(i, FILE_CHOOSER_RESULT_CODE)
                    return true
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

            addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    fun onHomeAssistantSetTheme() {
                        // We need to launch the getAndSetStatusBarNavigationBarColors in another thread, because otherwise the evaluateJavascript inside the method
                        // will not trigger it's callback method :/
                        GlobalScope.launch(Dispatchers.Main) {
                            getAndSetStatusBarNavigationBarColors()
                        }
                    }

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
                                        presenter.checkSecurityVersion()
                                    }
                                }
                                "config/get" -> {
                                    val pm: PackageManager = context.packageManager
                                    val hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
                                    val script = "externalBus(" +
                                        "${JSONObject(
                                            mapOf(
                                                "id" to JSONObject(message).get("id"),
                                                "type" to "result",
                                                "success" to true,
                                                "result" to JSONObject(
                                                    mapOf(
                                                        "hasSettingsScreen" to true,
                                                        "canWriteTag" to hasNfc,
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

                                    getAndSetStatusBarNavigationBarColors()

                                    // Set event lister for HA theme change
                                    webView.evaluateJavascript(
                                        "document.addEventListener('settheme', function ()" +
                                            "{" +
                                            "window.externalApp.onHomeAssistantSetTheme();" +
                                            "});",
                                        null
                                    )
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
                                "haptic" -> processHaptic(json.getJSONObject("payload").getString("hapticType"))
                            }
                        }
                    }
                },
                "externalApp"
            )
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

        currentLang = languagesManager.getCurrentLang()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val webviewPackage = WebViewCompat.getCurrentWebViewPackage(this)
            Log.d(TAG, "Current webview package ${webviewPackage?.packageName} and version ${webviewPackage?.versionName}")
        }
    }

    private fun getAndSetStatusBarNavigationBarColors() {
        if (themesManager.getCurrentTheme() == "system") { // Only change colors, if following the colors of home assistant (system)
            webView.evaluateJavascript("document.getElementsByTagName('html')[0].computedStyleMap().get('--app-header-background-color')[0];") { webViewcolor ->
                GlobalScope.launch {
                    var statusBarNavBarcolor = presenter.getStatusBarAndNavigationBarColor(webViewcolor)
                    withContext(Dispatchers.Main) {
                        setStatusBarAndNavigationBarColor(statusBarNavBarcolor)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentLang != languagesManager.getCurrentLang())
            recreate()
        if ((!unlocked && !presenter.isLockEnabled()) ||
            (!unlocked && presenter.isLockEnabled() && System.currentTimeMillis() < presenter.getSessionExpireMillis())
        ) {
            unlocked = true
            blurView.setBlurEnabled(false)
        }

        SensorWorker.start(this)
        checkAndWarnForDisabledLocation()
    }

    override fun onPause() {
        super.onPause()
        SensorWorker.start(this)
    }

    private fun checkAndWarnForDisabledLocation() {
        var showLocationDisabledWarning = false
        var settingsWithLocationPermissions = mutableListOf<String>()
        if (!DisabledLocationHandler.isLocationEnabled(this) && presenter.isSsidUsed()) {
            showLocationDisabledWarning = true
            settingsWithLocationPermissions.add(getString(R.string.pref_connection_wifi))
        }
        for (manager in SensorReceiver.MANAGERS) {
            for (basicSensor in manager.getAvailableSensors(this)) {
                if (manager.isEnabled(this, basicSensor.id)) {
                    var permissions = manager.requiredPermissions(basicSensor.id)

                    val fineLocation = DisabledLocationHandler.containsLocationPermission(permissions, true)
                    val coarseLocation = DisabledLocationHandler.containsLocationPermission(permissions, false)

                    if ((fineLocation || coarseLocation)) {
                        if (!DisabledLocationHandler.isLocationEnabled(this)) showLocationDisabledWarning = true
                        settingsWithLocationPermissions.add(getString(basicSensor.name))
                    }
                }
            }
        }

        if (showLocationDisabledWarning) {
            DisabledLocationHandler.showLocationDisabledWarnDialog(this@WebViewActivity, settingsWithLocationPermissions.toTypedArray(), true)
        } else {
            DisabledLocationHandler.removeLocationDisabledWarning(this@WebViewActivity)
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
        } else if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            mFilePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            mFilePathCallback = null
        }
    }

    fun exoPlayHls(json: JSONObject) {
        val payload = json.getJSONObject("payload")
        val uri = Uri.parse(payload.getString("url"))
        exoMute = payload.optBoolean("muted")
        runOnUiThread {
            exoPlayer = SimpleExoPlayer.Builder(applicationContext).setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    CronetDataSource.Factory(
                        CronetEngineWrapper(
                            applicationContext
                        ),
                        Executors.newSingleThreadExecutor()
                    )
                )
            ).build()
            exoPlayer?.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer?.playWhenReady = true
            exoPlayer?.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    exoBottom =
                        exoTop + ((exoRight - exoLeft) * videoSize.height / videoSize.width)
                    runOnUiThread {
                        exoResizeLayout()
                    }
                }
            })
            exoPlayer?.prepare()
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
        if ((exoPlayer == null) || (exoPlayer!!.videoFormat == null)) {
            // only set exoBottom if we can't calculate it from the video
            exoBottom = (rect.getInt("bottom") * displayMetrics.density).toInt()
        } else {
            exoBottom = exoTop + (exoRight - exoLeft) * exoPlayer!!.videoFormat!!.height / exoPlayer!!.videoFormat!!.width
        }
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
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                exo_content_frame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            } else {
                exo_content_frame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
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
            exo_content_frame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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

    fun processHaptic(hapticType: String) {
        val vm = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        Log.d(TAG, "Processing haptic tag for $hapticType")
        when (hapticType) {
            "success" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    webView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                else
                    vm.vibrate(500)
            }
            "warning" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    vm.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.EFFECT_HEAVY_CLICK))
                else
                    vm.vibrate(1500)
            }
            "failure" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    webView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                else
                    vm.vibrate(1000)
            }
            "light" -> {
                webView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            "medium" -> {
                webView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            "heavy" -> {
                webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            "selection" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    webView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                else
                    vm.vibrate(50)
            }
        }
    }
    private fun authenticationResult(result: Int) {
        when (result) {
            Authenticator.SUCCESS -> {
                Log.d(TAG, "Authentication successful, unlocking app")
                unlocked = true
                blurView.setBlurEnabled(false)
            }
            Authenticator.CANCELED -> {
                Log.d(TAG, "Authentication canceled by user, closing activity")
                finishAffinity()
            }
            else -> Log.d(TAG, "Authentication failed, retry attempts allowed")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (presenter.isLockEnabled() && !unlocked)
                if ((System.currentTimeMillis() > presenter.getSessionExpireMillis())) {
                    blurView.setBlurEnabled(true)
                    authenticator.authenticate(getString(R.string.biometric_title))
                } else {
                    blurView.setBlurEnabled(false)
                }

            val path = intent.getStringExtra(EXTRA_PATH)
            presenter.onViewReady(path)
            if (path?.startsWith("entityId:") == true)
                moreInfoEntity = path.substringAfter("entityId:")
            intent.removeExtra(EXTRA_PATH)

            if (presenter.isFullScreen())
                hideSystemUI()
            else
                showSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (isCutout())
            decor.systemUiVisibility = decor.systemUiVisibility or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
            decor.systemUiVisibility = decor.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun showSystemUI() {
        if (isCutout()) {
            decor.systemUiVisibility = decor.systemUiVisibility and
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv() and
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        } else {
            decor.systemUiVisibility = decor.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_STABLE.inv() and
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION.inv() and
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN.inv() and
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv() and
                View.SYSTEM_UI_FLAG_FULLSCREEN.inv() and
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        }
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

    private fun useWindowInsets(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Suppress("DEPRECATION")
    override fun setStatusBarAndNavigationBarColor(color: Int) {
        if (color != 0) {
            val darkThemeColorUsed = isColorDark(color)
            window.statusBarColor = color
            window.navigationBarColor = color

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (useWindowInsets()) {
                    var appFlags = if (darkThemeColorUsed) {
                        // Theme color is dark, so the status bar background should be also dark
                        // Then remove the light flag which, indicates that the status bar background is light
                        0 // Remove light flag
                    } else {
                        // Theme color is light, so the status bar background should be also light
                        // Then add the light flag, which indicates that the status bar background is light
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS // Add light flag
                    }
                    window.insetsController?.setSystemBarsAppearance(appFlags, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                } else {
                    var flags = window.decorView.systemUiVisibility

                    flags = if (darkThemeColorUsed) {
                        // Theme color is dark, so the status bar background should be also dark
                        // Then remove the light flag which, indicates that the status bar background is light
                        flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() // Remove light flag
                    } else {
                        // Theme color is light, so the status bar background should be also light
                        // Then add the light flag, which indicates that the status bar background is light
                        flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR // Add light flag
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = if (darkThemeColorUsed) {
                            // Theme color is dark, so the navigation bar background should be also dark
                            // Then remove the light flag which, indicates that the navigation background is light
                            flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() // Remove light flag
                        } else {
                            // Theme color is light, so the navigation background should be also light
                            // Then add the light flag, which indicates that the navigation background is light
                            flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR // Add light flag
                        }
                    }
                    window.decorView.systemUiVisibility = flags
                }
            }
        } else {
            Log.e(TAG, "Cannot set status bar/navigation bar color $color. Skipping coloring...")
        }
    }

    private fun isColorDark(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) < 0.5
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

    override fun showError(
        errorType: io.homeassistant.companion.android.webview.WebView.ErrorType,
        error: SslError?,
        description: String?
    ) {
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

        if (errorType == io.homeassistant.companion.android.webview.WebView.ErrorType.AUTHENTICATION) {
            alert.setMessage(R.string.error_auth_revoked)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.clearKnownUrls()
                openOnBoarding()
            }
        } else if (errorType == io.homeassistant.companion.android.webview.WebView.ErrorType.SSL) {
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
        } else if (errorType == io.homeassistant.companion.android.webview.WebView.ErrorType.SECURITY_WARNING) {
            alert.setTitle(R.string.security_vulnerably_title)
            alert.setMessage(R.string.security_vulnerably_message)
            alert.setPositiveButton(R.string.security_vulnerably_view) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("https://www.home-assistant.io/latest-security-alert/"))
                startActivity(intent)
            }
            alert.setNegativeButton(R.string.security_vulnerably_understand) { _, _ ->
                // Noop
            }
        } else {
            alert.setMessage(R.string.webview_error)
            alert.setPositiveButton(R.string.settings) { _, _ ->
                startActivity(SettingsActivity.newInstance(this))
            }
            val isInternal = runBlocking {
                urlRepository.isInternal()
            }
            alert.setNegativeButton(
                if (failedConnection == "external" && isInternal)
                    R.string.refresh_internal
                else
                    R.string.refresh_external
            ) { _, _ ->
                runBlocking {
                    failedConnection = if (failedConnection == "external") {
                        webView.loadUrl(urlRepository.getUrl(true).toString())
                        "internal"
                    } else {
                        webView.loadUrl(urlRepository.getUrl(false).toString())
                        "external"
                    }
                }
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
        val username = dialogLayout.findViewById<TextInputEditText>(R.id.username)
        val password = dialogLayout.findViewById<TextInputEditText>(R.id.password)
        val remember = dialogLayout.findViewById<CheckBox>(R.id.checkBox)
        val viewPassword = dialogLayout.findViewById<ImageView>(R.id.viewPassword)
        var autoAuth = false

        viewPassword.setOnClickListener() {
            if (password.transformationMethod == PasswordTransformationMethod.getInstance()) {
                password.transformationMethod = HideReturnsTransformationMethod.getInstance()
                viewPassword.setImageResource(R.drawable.ic_visibility_off)
                password.text?.let { it1 -> password.setSelection(it1.length) }
            } else {
                password.transformationMethod = PasswordTransformationMethod.getInstance()
                viewPassword.setImageResource(R.drawable.ic_visibility)
                password.text?.let { it1 -> password.setSelection(it1.length) }
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
        if (resourceURL.length >= 5) {
            message = if (resourceURL.subSequence(0, 5).toString() == "http:")
                "http://" + message + " " + getString(R.string.not_private)
            else
                "https://$message"
        }
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
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (!isConnected) {
                    showError()
                }
            },
            CONNECTION_DELAY
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Temporary workaround to sideload on Android TV and use a remote for basic navigation in WebView
        if (event?.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}
