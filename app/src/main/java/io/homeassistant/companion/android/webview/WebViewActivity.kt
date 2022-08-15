package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.DownloadManager
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
import android.os.Environment
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
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.cronet.CronetDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoSize
import dagger.hilt.android.AndroidEntryPoint
import eightbitlab.com.blurview.RenderScriptBlur
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.databinding.ActivityWebviewBinding
import io.homeassistant.companion.android.databinding.DialogAuthenticationBinding
import io.homeassistant.companion.android.databinding.ExoPlayerViewBinding
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.nfc.WriteNfcTag
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.language.LanguagesManager
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.DataUriDownloadManager
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.isStarted
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.homeassistant.companion.android.webview.WebView.ErrorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.json.JSONObject
import java.util.concurrent.Executors
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class WebViewActivity : BaseActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"

        private const val TAG = "WebviewActivity"
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
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            webView.reload()
        }
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                downloadFile(downloadFileUrl, downloadFileContentDisposition, downloadFileMimetype)
            }
        }
    private val writeNfcTag = registerForActivityResult(WriteNfcTag()) { messageId ->
        webView.externalBus(
            id = messageId,
            type = "result",
            success = true,
            result = emptyMap<String, String>()
        ) {
            Log.d(TAG, "NFC Write Complete $it")
        }
    }
    private val showWebFileChooser = registerForActivityResult(ShowWebFileChooser()) { result ->
        mFilePathCallback?.onReceiveValue(result)
        mFilePathCallback = null
    }

    @Inject
    lateinit var presenter: WebViewPresenter

    @Inject
    lateinit var themesManager: ThemesManager

    @Inject
    lateinit var changeLog: ChangeLog

    @Inject
    lateinit var languagesManager: LanguagesManager

    @Inject
    lateinit var urlRepository: UrlRepository

    @Inject
    lateinit var authenticationDao: AuthenticationDao

    @Inject
    lateinit var keyChainRepository: KeyChainRepository

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private lateinit var myCustomView: View
    private lateinit var authenticator: Authenticator
    private lateinit var exoPlayerView: PlayerView
    private lateinit var playerBinding: ExoPlayerViewBinding
    private lateinit var currentLang: String
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

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
    private val moreInfoMutex = Mutex()
    private var currentAutoplay: Boolean = false
    private var downloadFileUrl = ""
    private var downloadFileContentDisposition = ""
    private var downloadFileMimetype = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Initially set status and navigation bar color to colorLaunchScreenBackground to match the launch screen until the web frontend is loaded
        val colorLaunchScreenBackground = ResourcesCompat.getColor(resources, commonR.color.colorLaunchScreenBackground, theme)
        setStatusBarAndNavigationBarColor(colorLaunchScreenBackground, colorLaunchScreenBackground)

        binding.blurView.setupWith(binding.root)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(5f)
            .setHasFixedTransformationMatrix(false)

        exoPlayerView = binding.exoplayerView
        exoPlayerView.visibility = View.GONE
        exoPlayerView.setBackgroundColor(Color.BLACK)
        exoPlayerView.alpha = 1f
        exoPlayerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        exoPlayerView.controllerHideOnTouch = true
        exoPlayerView.controllerShowTimeoutMs = 2000

        playerBinding = ExoPlayerViewBinding.bind(exoPlayerView)

        if (!presenter.isLockEnabled()) {
            binding.blurView.setBlurEnabled(false)
            unlocked = true
        }

        authenticator = Authenticator(this, this, ::authenticationResult)

        decor = window.decorView as FrameLayout

        webView = binding.webview
        webView.apply {
            // TODO This quick bar workaround only works on Home Assistant core versions <2022.7
            // If not 'fixed' or officially supported: should be removed in Android 2023.1 (GitHub: #2690)
            setOnTouchListener(object : OnSwipeListener() {
                override fun onSwipe(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocity: Float,
                    direction: SwipeDirection,
                    pointerCount: Int
                ): Boolean {
                    if (pointerCount == 3 &&
                        direction == SwipeDirection.DOWN &&
                        velocity >= 150
                    ) {
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
                    }
                    return !unlocked
                }

                override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean {
                    return !unlocked
                }
            })

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = !presenter.isAutoPlayVideoEnabled()
            settings.userAgentString = settings.userAgentString + " ${HomeAssistantApis.USER_AGENT_STRING}"
            webViewClient = object : TLSWebViewClient(keyChainRepository) {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.e(TAG, "onReceivedError: errorCode: $errorCode url:$failingUrl")
                    if (failingUrl == loadedUrl) {
                        showError()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    enablePinchToZoom()
                    if (moreInfoEntity != "" && view?.progress == 100 && isConnected) {
                        ioScope.launch {
                            val owner = "onPageFinished:$moreInfoEntity"
                            if (moreInfoMutex.tryLock(owner)) {
                                delay(2000L)
                                Log.d(TAG, "More info entity: $moreInfoEntity")
                                webView.evaluateJavascript(
                                    "document.querySelector(\"home-assistant\").dispatchEvent(new CustomEvent(\"hass-more-info\", { detail: { entityId: \"$moreInfoEntity\" }}))"
                                ) {
                                    moreInfoMutex.unlock(owner)
                                    moreInfoEntity = ""
                                }
                            }
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    Log.e(TAG, "onReceivedHttpError: ${errorResponse?.statusCode} : ${errorResponse?.reasonPhrase} for: ${request?.url}")
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
                    Log.e(TAG, "onReceivedSslError: $error")
                    showError(
                        io.homeassistant.companion.android.webview.WebView.ErrorType.SSL,
                        error,
                        null
                    )
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
                                Log.d(TAG, "Launching browser")
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

            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                ) {
                    downloadFile(url, contentDisposition, mimetype)
                } else {
                    downloadFileUrl = url
                    downloadFileContentDisposition = contentDisposition
                    downloadFileMimetype = mimetype
                    requestStoragePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                        .setTitle(commonR.string.app_name)
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
                                    requestPermission.launch(android.Manifest.permission.CAMERA)
                                }
                            } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    request.grant(arrayOf(it))
                                } else {
                                    requestPermission.launch(
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    request.grant(arrayOf(it))
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
                    showWebFileChooser.launch(fileChooserParams)
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
                    // TODO This feature is deprecated and should be removed after 2022.6
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
                                this@WebViewActivity,
                                it.getString("callback"),
                                it.has("force") && it.getBoolean("force")
                            )
                        }
                    }

                    @JavascriptInterface
                    fun revokeExternalAuth(callback: String) {
                        presenter.onRevokeExternalAuth(JSONObject(callback).get("callback") as String)
                        relaunchApp()
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
                                    webView.externalBus(
                                        id = JSONObject(message).get("id"),
                                        type = "result",
                                        success = true,
                                        result = JSONObject(
                                            mapOf(
                                                "hasSettingsScreen" to true,
                                                "canWriteTag" to hasNfc,
                                                "hasExoPlayer" to true
                                            )
                                        )
                                    ) {
                                        Log.d(TAG, "Callback $it")
                                    }

                                    // TODO This feature is deprecated and should be removed after 2022.6
                                    getAndSetStatusBarNavigationBarColors()

                                    // TODO This feature is deprecated and should be removed after 2022.6
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
                                    writeNfcTag.launch(
                                        WriteNfcTag.Input(
                                            tagId = json.getJSONObject("payload").getString("tag"),
                                            messageId = JSONObject(message).getInt("id")
                                        )
                                    )
                                "exoplayer/play_hls" -> exoPlayHls(json)
                                "exoplayer/stop" -> exoStopHls()
                                "exoplayer/resize" -> exoResizeHls(json)
                                "haptic" -> processHaptic(json.getJSONObject("payload").getString("hapticType"))
                                "theme-update" -> getAndSetStatusBarNavigationBarColors()
                            }
                        }
                    }
                },
                "externalApp"
            )
        }

        // Set WebView background color to transparent, so that the theme of the android activity has control over it.
        // This enables the ability to have the launch screen behind the WebView until the web frontend gets rendered
        binding.webview.setBackgroundColor(Color.TRANSPARENT)

        themesManager.setThemeForWebView(this, webView.settings)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                if (presenter.isFullScreen())
                    hideSystemUI()
        }

        if (presenter.isKeepScreenOnEnabled())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        currentLang = languagesManager.getCurrentLang()
        currentAutoplay = presenter.isAutoPlayVideoEnabled()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val webviewPackage = WebViewCompat.getCurrentWebViewPackage(this)
            Log.d(TAG, "Current webview package ${webviewPackage?.packageName} and version ${webviewPackage?.versionName}")
        }
    }

    private fun getAndSetStatusBarNavigationBarColors() {
        val htmlArraySpacer = "-SPACER-"
        webView.evaluateJavascript(
            "[" +
                "document.getElementsByTagName('html')[0].computedStyleMap().get('--app-header-background-color')[0]," +
                "document.getElementsByTagName('html')[0].computedStyleMap().get('--primary-background-color')[0]" +
                "].join('" + htmlArraySpacer + "')"
        ) { webViewColors ->
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    var statusBarColor = 0
                    var navigationBarColor = 0

                    if (!webViewColors.isNullOrEmpty() && webViewColors != "null") {
                        val trimmedColorString = webViewColors.substring(1, webViewColors.length - 1).trim()
                        val colors = trimmedColorString.split(htmlArraySpacer)

                        for (color in colors) {
                            Log.d(TAG, "Color from webview is \"$trimmedColorString\"")
                        }

                        statusBarColor = presenter.parseWebViewColor(colors[0].trim())
                        navigationBarColor = presenter.parseWebViewColor(colors[1].trim())
                    }

                    setStatusBarAndNavigationBarColor(statusBarColor, navigationBarColor)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if ((currentLang != languagesManager.getCurrentLang()) || currentAutoplay != presenter.isAutoPlayVideoEnabled())
            recreate()
        if ((!unlocked && !presenter.isLockEnabled()) ||
            (!unlocked && presenter.isLockEnabled() && System.currentTimeMillis() < presenter.getSessionExpireMillis())
        ) {
            unlocked = true
            binding.blurView.setBlurEnabled(false)
        }

        enablePinchToZoom()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || presenter.isWebViewDebugEnabled())

        if (presenter.isKeepScreenOnEnabled())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        SensorWorker.start(this)
        WebsocketManager.start(this)
        checkAndWarnForDisabledLocation()
        changeLog.showChangeLog(this, false)
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
            settingsWithLocationPermissions.add(getString(commonR.string.pref_connection_wifi))
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

    fun exoPlayHls(json: JSONObject) {
        val payload = json.getJSONObject("payload")
        val uri = Uri.parse(payload.getString("url"))
        exoMute = payload.optBoolean("muted")
        runOnUiThread {
            exoPlayer = SimpleExoPlayer.Builder(applicationContext).setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    CronetDataSource.Factory(
                        CronetEngine.Builder(applicationContext).enableQuic(true).build(),
                        Executors.newSingleThreadExecutor()
                    )
                ).setLiveMaxSpeed(8.0f)
            ).setLoadControl(
                DefaultLoadControl.Builder().setBufferDurationsMs(
                    0, 30000, 0, 0
                ).build()
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

            findViewById<ImageView>(R.id.exo_fullscreen_icon).setOnClickListener {
                isExoFullScreen = !isExoFullScreen
                exoResizeLayout()
            }
            findViewById<ImageView>(R.id.exo_mute_icon).setOnClickListener { exoToggleMute() }
        }
        webView.externalBus(
            id = json.get("id"),
            type = "result",
            success = true,
            result = null
        ) {
            Log.d(TAG, "Callback $it")
        }
    }

    fun exoStopHls() {
        runOnUiThread {
            exoPlayerView.visibility = View.GONE
            exoPlayerView.player = null
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
            exoBottom = exoTop + (exoRight - exoLeft) * exoPlayer!!.videoFormat!!.height /
                exoPlayer!!.videoFormat!!.width
        }
        runOnUiThread {
            exoResizeLayout()
        }
    }

    fun exoToggleMute() {
        exoMute = !exoMute
        if (exoMute) {
            exoPlayer?.volume = 0f
            findViewById<ImageView>(R.id.exo_mute_icon).setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_volume_off_24
                )
            )
        } else {
            exoPlayer?.volume = 1f
            findViewById<ImageView>(R.id.exo_mute_icon).setImageDrawable(
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
                playerBinding.exoContentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            } else {
                playerBinding.exoContentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
            exoLayoutParams.setMargins(0, 0, 0, 0)
            exoPlayerView.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            exoPlayerView.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            findViewById<ImageView>(R.id.exo_fullscreen_icon).setImageDrawable(
                ContextCompat.getDrawable(
                    applicationContext,
                    R.drawable.ic_baseline_fullscreen_exit_24
                )
            )
            hideSystemUI()
        } else {
            playerBinding.exoContentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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
            findViewById<ImageView>(R.id.exo_fullscreen_icon).setImageDrawable(
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
        val vm = getSystemService<Vibrator>()

        Log.d(TAG, "Processing haptic tag for $hapticType")
        when (hapticType) {
            "success" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    webView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                else
                    vm?.vibrate(500)
            }
            "warning" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    vm?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.EFFECT_HEAVY_CLICK))
                else
                    vm?.vibrate(1500)
            }
            "failure" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    webView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                else
                    vm?.vibrate(1000)
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
                    vm?.vibrate(50)
            }
        }
    }
    private fun authenticationResult(result: Int) {
        when (result) {
            Authenticator.SUCCESS -> {
                Log.d(TAG, "Authentication successful, unlocking app")
                unlocked = true
                binding.blurView.setBlurEnabled(false)
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
                    binding.blurView.setBlurEnabled(true)
                    authenticator.authenticate(getString(commonR.string.biometric_title))
                } else {
                    binding.blurView.setBlurEnabled(false)
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

    override fun relaunchApp() {
        startActivity(Intent(this, LaunchActivity::class.java))
        finish()
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

    override fun setStatusBarAndNavigationBarColor(statusBarColor: Int, navigationBarColor: Int) {
        // window.statusBarColor and window.navigationBarColor must both be set before
        // windowInsetsController sets the foreground colors.

        // Set background colors
        if (statusBarColor != 0) {
            window.statusBarColor = statusBarColor
        } else {
            Log.e(TAG, "Cannot set status bar color $statusBarColor. Skipping coloring...")
        }
        if (navigationBarColor != 0) {
            window.navigationBarColor = navigationBarColor
        } else {
            Log.e(TAG, "Cannot set navigation bar color $navigationBarColor. Skipping coloring...")
        }

        // Set foreground colors
        if (statusBarColor != 0) {
            windowInsetsController.isAppearanceLightStatusBars = !isColorDark(statusBarColor)
        }
        if (navigationBarColor != 0) {
            windowInsetsController.isAppearanceLightNavigationBars = !isColorDark(navigationBarColor)
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
        errorType: ErrorType,
        error: SslError?,
        description: String?
    ) {
        if (isShowingError || !isStarted)
            return
        isShowingError = true

        val alert = AlertDialog.Builder(this)
            .setTitle(commonR.string.error_connection_failed)
            .setOnDismissListener {
                isShowingError = false
                alertDialog = null
                waitForConnection()
            }

        var tlsWebViewClient: TLSWebViewClient? = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
            tlsWebViewClient = WebViewCompat.getWebViewClient(webView) as TLSWebViewClient
        }

        if (tlsWebViewClient?.isTLSClientAuthNeeded == true &&
            errorType == ErrorType.TIMEOUT &&
            !tlsWebViewClient.hasUserDeniedAccess
        ) {
            // Ignore if a timeout occurs but the user has not denied access
            // It is likely due to the user not choosing a key yet
            return
        } else if (tlsWebViewClient?.isTLSClientAuthNeeded == true &&
            errorType == ErrorType.AUTHENTICATION &&
            tlsWebViewClient.hasUserDeniedAccess
        ) {
            // If no key is available to the app
            alert.setMessage(commonR.string.tls_cert_not_found_message)
            alert.setTitle(commonR.string.tls_cert_title)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.clearKnownUrls()
                relaunchApp()
            }
            alert.setNeutralButton(commonR.string.exit) { _, _ ->
                finishAffinity()
            }
        } else if (tlsWebViewClient?.isTLSClientAuthNeeded == true &&
            !tlsWebViewClient.isCertificateChainValid
        ) {
            // If the chain is no longer valid
            alert.setMessage(commonR.string.tls_cert_expired_message)
            alert.setTitle(commonR.string.tls_cert_title)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                ioScope.launch {
                    keyChainRepository.clear()
                }
                relaunchApp()
            }
        } else if (errorType == ErrorType.AUTHENTICATION) {
            alert.setMessage(commonR.string.error_auth_revoked)
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                presenter.clearKnownUrls()
                relaunchApp()
            }
        } else if (errorType == ErrorType.SSL) {
            if (description != null)
                alert.setMessage(getString(commonR.string.webview_error_description) + " " + description)
            else if (error!!.primaryError == SslError.SSL_DATE_INVALID)
                alert.setMessage(commonR.string.webview_error_SSL_DATE_INVALID)
            else if (error.primaryError == SslError.SSL_EXPIRED)
                alert.setMessage(commonR.string.webview_error_SSL_EXPIRED)
            else if (error.primaryError == SslError.SSL_IDMISMATCH)
                alert.setMessage(commonR.string.webview_error_SSL_IDMISMATCH)
            else if (error.primaryError == SslError.SSL_INVALID)
                alert.setMessage(commonR.string.webview_error_SSL_INVALID)
            else if (error.primaryError == SslError.SSL_NOTYETVALID)
                alert.setMessage(commonR.string.webview_error_SSL_NOTYETVALID)
            else if (error.primaryError == SslError.SSL_UNTRUSTED)
                alert.setMessage(commonR.string.webview_error_SSL_UNTRUSTED)
            alert.setPositiveButton(commonR.string.settings) { _, _ ->
                startActivity(SettingsActivity.newInstance(this))
            }
            alert.setNeutralButton(commonR.string.exit) { _, _ ->
                finishAffinity()
            }
        } else if (errorType == ErrorType.SECURITY_WARNING) {
            alert.setTitle(commonR.string.security_vulnerably_title)
            alert.setMessage(commonR.string.security_vulnerably_message)
            alert.setPositiveButton(commonR.string.security_vulnerably_view) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("https://www.home-assistant.io/latest-security-alert/"))
                startActivity(intent)
            }
            alert.setNegativeButton(commonR.string.security_vulnerably_understand) { _, _ ->
                // Noop
            }
        } else {
            alert.setMessage(commonR.string.webview_error)
            alert.setPositiveButton(commonR.string.settings) { _, _ ->
                startActivity(SettingsActivity.newInstance(this))
            }
            val isInternal = runBlocking {
                urlRepository.isInternal()
            }
            alert.setNegativeButton(
                if (failedConnection == "external" && isInternal)
                    commonR.string.refresh_internal
                else
                    commonR.string.refresh_external
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
            alert.setNeutralButton(commonR.string.wait) { _, _ ->
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
        val httpAuth = authenticationDao.get((resourceURL + realm))

        val dialogLayout = DialogAuthenticationBinding.inflate(layoutInflater)
        val username = dialogLayout.username
        val password = dialogLayout.password
        val remember = dialogLayout.checkBox
        val viewPassword = dialogLayout.viewPassword
        var autoAuth = false

        viewPassword.setOnClickListener {
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

        var message = host + " " + getString(commonR.string.required_fields)
        if (resourceURL.length >= 5) {
            message = if (resourceURL.subSequence(0, 5).toString() == "http:")
                "http://" + message + " " + getString(commonR.string.not_private)
            else
                "https://$message"
        }
        if (!autoAuth || authError) {
            AlertDialog.Builder(this, R.style.Authentication_Dialog)
                .setTitle(commonR.string.auth_request)
                .setMessage(message)
                .setView(dialogLayout.root)
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
                        .setTitle(commonR.string.auth_cancel)
                        .setMessage(commonR.string.auth_error_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            authenticationDialog(handler, host, realm, authError)
                        }
                        .show()
                }
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    Toast.makeText(applicationContext, commonR.string.auth_cancel, Toast.LENGTH_SHORT)
                        .show()
                }
                .show()
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

    private fun WebView.externalBus(
        id: Any,
        type: String,
        success: Boolean,
        result: Any?,
        callback: ValueCallback<String>?
    ) {
        val json = JSONObject(
            mapOf(
                "id" to id,
                "type" to type,
                "success" to success,
                "result" to result
            )
        )
        val script = "externalBus($json);"

        Log.d(TAG, script)

        this.evaluateJavascript(script, callback)
    }

    private fun downloadFile(url: String, contentDisposition: String, mimetype: String) {
        Log.d(TAG, "WebView requested download of $url")
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val request = DownloadManager.Request(uri)
                    .setMimeType(mimetype)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimetype)
                    )
                runBlocking {
                    if (url.startsWith(urlRepository.getUrl(true).toString()) ||
                        url.startsWith(urlRepository.getUrl(false).toString())
                    ) {
                        request.addRequestHeader("Authorization", presenter.getAuthorizationHeader())
                    }
                }
                try {
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                } catch (e: Exception) {
                    // Cannot get cookies, probably not relevant
                }

                getSystemService<DownloadManager>()?.enqueue(request) ?: Log.d(TAG, "Unable to start download, cannot get DownloadManager")
            }
            "data" -> {
                lifecycleScope.launch {
                    DataUriDownloadManager.saveDataUri(this@WebViewActivity, url, mimetype)
                }
            }
            else -> {
                Log.d(TAG, "Received download request for unsupported scheme, forwarding to system")
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(browserIntent)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Temporary workaround to sideload on Android TV and use a remote for basic navigation in WebView
        if (event?.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun enablePinchToZoom() {
        // Enable pinch to zoom
        webView.getSettings().setBuiltInZoomControls(presenter.isPinchToZoomEnabled())
        // Use idea from https://github.com/home-assistant/iOS/pull/1472 to filter viewport
        val pinchToZoom = if (presenter.isPinchToZoomEnabled()) "true" else "false"
        webView.evaluateJavascript(
            """
            if (typeof viewport === 'undefined') {
                var viewport = document.querySelector('meta[name="viewport"]');
                if (viewport != null && typeof original_elements === 'undefined') {
                    var original_elements = viewport['content'];
                }
            }
            if (viewport != null) {
                if ($pinchToZoom) {
                    const ignoredBits = ['user-scalable', 'minimum-scale', 'maximum-scale'];
                    let elements = viewport['content'].split(',').filter(contentItem => {
                        return ignoredBits.every(ignoredBit => !contentItem.includes(ignoredBit));
                    });
                    elements.push('user-scalable=yes');
                    viewport['content'] = elements.join(',');
                } else {
                    viewport['content'] = original_elements;
                }           
            }
            """
        ) {}
    }
}
