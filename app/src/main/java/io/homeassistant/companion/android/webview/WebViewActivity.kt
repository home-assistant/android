package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.barcode.BarcodeScannerActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.databinding.ActivityWebviewBinding
import io.homeassistant.companion.android.databinding.DialogAuthenticationBinding
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.nfc.WriteNfcTag
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.DataUriDownloadManager
import io.homeassistant.companion.android.util.LifecycleHandler
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.isStarted
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.homeassistant.companion.android.webview.WebView.ErrorType
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.chromium.net.CronetEngine
import org.json.JSONObject

@OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class WebViewActivity : BaseActivity(), io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_SERVER = "server"
        const val EXTRA_SHOW_WHEN_LOCKED = "show_when_locked"

        private const val TAG = "WebviewActivity"
        private const val APP_PREFIX = "app://"
        private const val INTENT_PREFIX = "intent:"
        private const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="

        fun newInstance(context: Context, path: String? = null, serverId: Int? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_SERVER, serverId)
            }
        }

        private const val CONNECTION_DELAY = 10000L
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.any { result -> result.value }) {
                webView.reload()
            }
        }
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                downloadFile(downloadFileUrl, downloadFileContentDisposition, downloadFileMimetype)
            }
        }
    private val writeNfcTag = registerForActivityResult(WriteNfcTag()) { messageId ->
        sendExternalBusMessage(
            ExternalBusMessage(
                id = messageId,
                type = "result",
                success = true,
                result = emptyMap<String, String>(),
                callback = {
                    Log.d(TAG, "NFC Write Complete $it")
                }
            )
        )
    }
    private val showWebFileChooser = registerForActivityResult(ShowWebFileChooser()) { result ->
        mFilePathCallback?.onReceiveValue(result)
        mFilePathCallback = null
    }
    private val commissionMatterDevice = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        presenter.onMatterThreadIntentResult(this, result)
    }

    @Inject
    lateinit var presenter: WebViewPresenter

    @Inject
    lateinit var themesManager: ThemesManager

    @Inject
    lateinit var changeLog: ChangeLog

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var authenticationDao: AuthenticationDao

    @Inject
    @Named("keyChainRepository")
    lateinit var keyChainRepository: KeyChainRepository

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private lateinit var myCustomView: View
    private lateinit var authenticator: Authenticator
    private lateinit var exoPlayerView: PlayerView
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var isConnected = false
    private var isShowingError = false
    private var isRelaunching = false
    private var alertDialog: AlertDialog? = null
    private var isVideoFullScreen = false
    private var videoHeight = 0
    private var firstAuthTime: Long = 0
    private var resourceURL: String = ""
    private var appLocked = true
    private var unlockingApp = false
    private var exoPlayer: ExoPlayer? = null
    private var isExoFullScreen = false
    private var exoTop = 0 // These margins are from the DOM and scaled to screen
    private var exoLeft = 0
    private var exoRight = 0
    private var exoBottom = 0
    private var exoMute = true
    private var failedConnection = "external"
    private var clearHistory = false
    private var moreInfoEntity = ""
    private val moreInfoMutex = Mutex()
    private var currentAutoplay: Boolean = false
    private var downloadFileUrl = ""
    private var downloadFileContentDisposition = ""
    private var downloadFileMimetype = ""
    private val javascriptInterface = "externalApp"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (
            intent.extras?.containsKey(EXTRA_SHOW_WHEN_LOCKED) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        ) {
            // Allow showing this on the lock screen when using device controls panel
            setShowWhenLocked(intent.extras?.getBoolean(EXTRA_SHOW_WHEN_LOCKED) ?: false)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.extras?.containsKey(EXTRA_SERVER) == true) {
            intent.extras?.getInt(EXTRA_SERVER)?.let {
                presenter.setActiveServer(it)
                intent.removeExtra(EXTRA_SERVER)
            }
        }

        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Initially set status and navigation bar color to colorLaunchScreenBackground to match the launch screen until the web frontend is loaded
        val colorLaunchScreenBackground = ResourcesCompat.getColor(resources, commonR.color.colorLaunchScreenBackground, theme)
        setStatusBarAndNavigationBarColor(colorLaunchScreenBackground, colorLaunchScreenBackground)

        binding.blurView.setupWith(binding.root)
            .setBlurRadius(8f)

        exoPlayerView = binding.exoplayerView
        exoPlayerView.visibility = View.GONE
        exoPlayerView.setBackgroundColor(Color.BLACK)
        exoPlayerView.alpha = 1f
        exoPlayerView.controllerShowTimeoutMs = 2000

        appLocked = presenter.isAppLocked()
        binding.blurView.setBlurEnabled(appLocked)

        authenticator = Authenticator(this, this, ::authenticationResult)

        decor = window.decorView as FrameLayout

        webView = binding.webview

        val onBackPressed = object : OnBackPressedCallback(webView.canGoBack()) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressed)

        webView.apply {
            setOnTouchListener(object : OnSwipeListener(this@WebViewActivity) {
                override fun onSwipe(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocity: Float,
                    direction: SwipeDirection,
                    pointerCount: Int
                ): Boolean {
                    if (pointerCount == 3 && velocity >= 75) {
                        when (direction) {
                            SwipeDirection.LEFT -> presenter.nextServer()
                            SwipeDirection.RIGHT -> presenter.previousServer()
                            SwipeDirection.UP -> {
                                val serverChooser = ServerChooserFragment()
                                supportFragmentManager.setFragmentResultListener(ServerChooserFragment.RESULT_KEY, this@WebViewActivity) { _, bundle ->
                                    if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                                        presenter.switchActiveServer(bundle.getInt(ServerChooserFragment.RESULT_SERVER))
                                    }
                                    supportFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
                                }
                                serverChooser.show(supportFragmentManager, ServerChooserFragment.TAG)
                            }
                            SwipeDirection.DOWN -> {
                                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
                            }
                        }
                    }
                    return appLocked
                }

                override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean {
                    return appLocked
                }
            })

            settings.minimumFontSize = 5
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = !presenter.isAutoPlayVideoEnabled()
            settings.userAgentString = settings.userAgentString + " ${HomeAssistantApis.USER_AGENT_STRING}"
            webViewClient = object : TLSWebViewClient(keyChainRepository) {
                @Deprecated("Deprecated in Java for SDK >= 23")
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
                    if (clearHistory) {
                        webView.clearHistory()
                        clearHistory = false
                    }
                    setWebViewZoom()
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
                    if (System.currentTimeMillis() <= (firstAuthTime + 500)) {
                        authError = true
                    }
                    authenticationDialog(handler, host, realm, authError)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    Log.e(TAG, "onReceivedSslError: $error")
                    showError(
                        ErrorType.SSL,
                        error,
                        null
                    )
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    handler: RenderProcessGoneDetail?
                ): Boolean {
                    Log.e(TAG, "onRenderProcessGone: webView crashed")
                    view?.let {
                        reload()
                        webViewAddJavascriptInterface()
                    }

                    return true
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
                                if (intentPackage == null && !intent.`package`.isNullOrEmpty()) {
                                    Log.w(TAG, "No app found for intent prefix, opening app store")
                                    val marketIntent = Intent(Intent.ACTION_VIEW)
                                    marketIntent.data =
                                        Uri.parse(MARKET_PREFIX + intent.`package`.toString())
                                    startActivity(marketIntent)
                                } else {
                                    startActivity(intent)
                                }
                                return true
                            } else if (!webView.url.toString().contains(it.toString())) {
                                Log.d(TAG, "Launching browser")
                                val browserIntent = Intent(Intent.ACTION_VIEW, it)
                                startActivity(browserIntent)
                                return true
                            } else {
                                // Do nothing.
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to override the URL", e)
                        }
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onBackPressed.isEnabled = canGoBack()
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
                        val alreadyGranted = ArrayList<String>()
                        val toBeGranted = ArrayList<String>()
                        request?.resources?.forEach {
                            if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    alreadyGranted.add(it)
                                } else {
                                    toBeGranted.add(android.Manifest.permission.CAMERA)
                                }
                            } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    alreadyGranted.add(it)
                                } else {
                                    toBeGranted.add(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        if (alreadyGranted.size > 0) {
                            request?.grant(alreadyGranted.toTypedArray())
                        }
                        if (toBeGranted.size > 0) {
                            requestPermissions.launch(
                                toBeGranted.toTypedArray()
                            )
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
                    if (!presenter.isFullScreen()) {
                        showSystemUI()
                    }
                    isVideoFullScreen = false
                    super.onHideCustomView()
                }
            }

            webViewAddJavascriptInterface()
        }

        // Set WebView background color to transparent, so that the theme of the android activity has control over it.
        // This enables the ability to have the launch screen behind the WebView until the web frontend gets rendered
        binding.webview.setBackgroundColor(Color.TRANSPARENT)

        themesManager.setThemeForWebView(this, webView.settings)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                if (presenter.isFullScreen()) {
                    hideSystemUI()
                }
            }
        }

        if (presenter.isKeepScreenOnEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        currentAutoplay = presenter.isAutoPlayVideoEnabled()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val webviewPackage = WebViewCompat.getCurrentWebViewPackage(this)
            Log.d(TAG, "Current webview package ${webviewPackage?.packageName} and version ${webviewPackage?.versionName}")
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                presenter.getMatterThreadStepFlow().collect {
                    Log.d(TAG, "Matter/Thread step changed to $it")
                    when (it) {
                        MatterThreadStep.THREAD_EXPORT_TO_SERVER_MATTER,
                        MatterThreadStep.THREAD_EXPORT_TO_SERVER_ONLY,
                        MatterThreadStep.MATTER_IN_PROGRESS -> {
                            presenter.getMatterThreadIntent()?.let { intentSender ->
                                commissionMatterDevice.launch(IntentSenderRequest.Builder(intentSender).build())
                            }
                        }
                        MatterThreadStep.THREAD_NONE -> {
                            alertDialog?.cancel()
                            AlertDialog.Builder(this@WebViewActivity)
                                .setMessage(commonR.string.thread_export_none)
                                .setPositiveButton(commonR.string.ok, null)
                                .show()
                            presenter.finishMatterThreadFlow()
                        }
                        MatterThreadStep.THREAD_SENT -> {
                            Toast.makeText(this@WebViewActivity, commonR.string.thread_export_success, Toast.LENGTH_SHORT).show()
                            alertDialog?.cancel()
                            presenter.finishMatterThreadFlow()
                        }
                        MatterThreadStep.ERROR_MATTER -> {
                            Toast.makeText(this@WebViewActivity, commonR.string.matter_commissioning_unavailable, Toast.LENGTH_SHORT).show()
                            presenter.finishMatterThreadFlow()
                        }
                        MatterThreadStep.ERROR_THREAD_LOCAL_NETWORK -> {
                            alertDialog?.cancel()
                            AlertDialog.Builder(this@WebViewActivity)
                                .setMessage(commonR.string.thread_export_not_connected)
                                .setPositiveButton(commonR.string.ok, null)
                                .show()
                            presenter.finishMatterThreadFlow()
                        }
                        MatterThreadStep.ERROR_THREAD_OTHER -> {
                            Toast.makeText(this@WebViewActivity, commonR.string.thread_export_unavailable, Toast.LENGTH_SHORT).show()
                            alertDialog?.cancel()
                            presenter.finishMatterThreadFlow()
                        }
                        else -> { } // Do nothing
                    }
                }
            }
        }
    }

    private fun webViewAddJavascriptInterface() {
        webView.removeJavascriptInterface(javascriptInterface)
        webView.apply {
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
                        isRelaunching = true // Prevent auth errors from showing
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
                                    val canCommissionMatter = presenter.appCanCommissionMatterDevice()
                                    val canExportThread = presenter.appCanExportThreadCredentials()
                                    val hasBarCodeScanner =
                                        if (
                                            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                                            !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                                        ) {
                                            1
                                        } else {
                                            0
                                        }
                                    sendExternalBusMessage(
                                        ExternalBusMessage(
                                            id = JSONObject(message).get("id"),
                                            type = "result",
                                            success = true,
                                            result = JSONObject(
                                                mapOf(
                                                    "hasSettingsScreen" to true,
                                                    "canWriteTag" to hasNfc,
                                                    "hasExoPlayer" to true,
                                                    "canCommissionMatter" to canCommissionMatter,
                                                    "canImportThreadCredentials" to canExportThread,
                                                    "hasAssist" to true,
                                                    "hasBarCodeScanner" to hasBarCodeScanner
                                                )
                                            ),
                                            callback = {
                                                Log.d(TAG, "Callback $it")
                                            }
                                        )
                                    )

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
                                "assist/show" -> {
                                    val payload = if (json.has("payload")) json.getJSONObject("payload") else null
                                    startActivity(
                                        AssistActivity.newInstance(
                                            this@WebViewActivity,
                                            serverId = presenter.getActiveServer(),
                                            pipelineId = if (payload?.has("pipeline_id") == true) payload.getString("pipeline_id") else null,
                                            startListening = if (payload?.has("start_listening") == true) payload.getBoolean("start_listening") else true
                                        )
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
                                "matter/commission" -> presenter.startCommissioningMatterDevice(this@WebViewActivity)
                                "thread/import_credentials" -> {
                                    presenter.exportThreadCredentials(this@WebViewActivity)

                                    alertDialog = AlertDialog.Builder(this@WebViewActivity)
                                        .setMessage(commonR.string.thread_debug_active)
                                        .create()
                                    alertDialog?.show()
                                }
                                "bar_code/scan" -> {
                                    val payload = if (json.has("payload")) json.getJSONObject("payload") else null
                                    if (payload?.has("title") != true || !payload.has("description")) return@post
                                    startActivity(
                                        BarcodeScannerActivity.newInstance(
                                            this@WebViewActivity,
                                            messageId = json.getInt("id"),
                                            title = payload.getString("title"),
                                            subtitle = payload.getString("description"),
                                            action = if (payload.has("alternative_option_label")) payload.getString("alternative_option_label").ifBlank { null } else null
                                        )
                                    )
                                }
                                "exoplayer/play_hls" -> exoPlayHls(json)
                                "exoplayer/stop" -> exoStopHls()
                                "exoplayer/resize" -> exoResizeHls(json)
                                "haptic" -> processHaptic(json.getJSONObject("payload").getString("hapticType"))
                                "theme-update" -> getAndSetStatusBarNavigationBarColors()
                                else -> presenter.onExternalBusMessage(json)
                            }
                        }
                    }
                },
                javascriptInterface
            )
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

    override fun onStart() {
        super.onStart()
        presenter.onStart(this)
    }

    override fun onResume() {
        super.onResume()
        if (currentAutoplay != presenter.isAutoPlayVideoEnabled()) {
            recreate()
        }

        presenter.updateActiveServer()

        appLocked = presenter.isAppLocked()
        binding.blurView.setBlurEnabled(appLocked)

        setWebViewZoom()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || presenter.isWebViewDebugEnabled())

        requestedOrientation = when (presenter.getScreenOrientation()) {
            getString(commonR.string.screen_orientation_option_value_portrait) -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            getString(commonR.string.screen_orientation_option_value_landscape) -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (presenter.isKeepScreenOnEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        SensorWorker.start(this)
        WebsocketManager.start(this)
        lifecycleScope.launch {
            checkAndWarnForDisabledLocation()
        }
        changeLog.showChangeLog(this, false)

        if (::loadedUrl.isInitialized) {
            waitForConnection()
        }
    }

    override fun onStop() {
        super.onStop()
        openFirstViewOnDashboardIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        presenter.setAppActive(false)
        if (!isFinishing && !isRelaunching) SensorReceiver.updateAllSensors(this)
    }

    private suspend fun checkAndWarnForDisabledLocation() {
        var showLocationDisabledWarning = false
        val settingsWithLocationPermissions = mutableListOf<String>()
        if (!DisabledLocationHandler.isLocationEnabled(this) && presenter.isSsidUsed()) {
            showLocationDisabledWarning = true
            settingsWithLocationPermissions.add(getString(commonR.string.pref_connection_wifi))
        }
        for (manager in SensorReceiver.MANAGERS) {
            for (basicSensor in manager.getAvailableSensors(this)) {
                if (manager.isEnabled(this, basicSensor)) {
                    val permissions = manager.requiredPermissions(basicSensor.id)

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
            exoPlayer = ExoPlayer.Builder(applicationContext).setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    CronetDataSource.Factory(
                        CronetEngine.Builder(applicationContext).enableQuic(true).build(),
                        Executors.newSingleThreadExecutor()
                    )
                ).setLiveMaxSpeed(8.0f)
            ).setLoadControl(
                DefaultLoadControl.Builder().setBufferDurationsMs(
                    0,
                    30000,
                    0,
                    0
                ).build()
            ).build()
            exoPlayer?.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer?.playWhenReady = true
            exoPlayer?.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    if (videoSize.height == 0 || videoSize.width == 0) return
                    exoBottom =
                        exoTop + ((exoRight - exoLeft) * videoSize.height / videoSize.width)
                    runOnUiThread {
                        exoResizeLayout()
                    }
                }
            })
            exoPlayer?.prepare()
            exoMute = !exoMute // Invert because exoToggleMute() will invert again
            exoToggleMute()
            exoPlayerView.setFullscreenButtonClickListener { isFullScreen ->
                isExoFullScreen = isFullScreen
                exoResizeLayout()
            }
            exoPlayerView.player = exoPlayer
            exoPlayerView.visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.exo_ha_mute)?.setOnClickListener { exoToggleMute() }
        }
        sendExternalBusMessage(
            ExternalBusMessage(
                id = json.get("id"),
                type = "result",
                success = true,
                callback = {
                    Log.d(TAG, "Callback $it")
                }
            )
        )
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
        exoBottom = if ((exoPlayer == null) || (exoPlayer!!.videoFormat == null)) {
            // only set exoBottom if we can't calculate it from the video
            (rect.getInt("bottom") * displayMetrics.density).toInt()
        } else {
            exoTop + (exoRight - exoLeft) * exoPlayer!!.videoFormat!!.height /
                exoPlayer!!.videoFormat!!.width
        }
        runOnUiThread {
            exoResizeLayout()
        }
    }

    private fun exoToggleMute() {
        exoMute = !exoMute
        if (exoMute) {
            exoPlayer?.volume = 0f
            findViewById<ImageButton>(R.id.exo_ha_mute)?.setImageResource(R.drawable.ic_baseline_volume_off_24)
        } else {
            exoPlayer?.volume = 1f
            findViewById<ImageButton>(R.id.exo_ha_mute)?.setImageResource(R.drawable.ic_baseline_volume_up_24)
        }
    }

    fun exoResizeLayout() {
        val exoLayoutParams = exoPlayerView.layoutParams as FrameLayout.LayoutParams
        if (isExoFullScreen) {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            } else {
                exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
            exoLayoutParams.setMargins(0, 0, 0, 0)
            exoPlayerView.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            exoPlayerView.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            hideSystemUI()
        } else {
            exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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
            showSystemUI()
        }
        exoPlayerView.requestLayout()
    }

    fun processHaptic(hapticType: String) {
        val vm = getSystemService<Vibrator>()

        Log.d(TAG, "Processing haptic tag for $hapticType")
        when (hapticType) {
            "success" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    webView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(500)
                }
            }
            "warning" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vm?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.EFFECT_HEAVY_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(1500)
                }
            }
            "failure" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    webView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(1000)
                }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    webView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(50)
                }
            }
        }
    }
    private fun authenticationResult(result: Int) {
        when (result) {
            Authenticator.SUCCESS -> {
                Log.d(TAG, "Authentication successful, unlocking app")
                appLocked = false
                presenter.setAppActive(true)
                binding.blurView.setBlurEnabled(false)
            }
            Authenticator.CANCELED -> {
                Log.d(TAG, "Authentication canceled by user, closing activity")
                finishAffinity()
            }
            else -> Log.d(TAG, "Authentication failed, retry attempts allowed")
        }
        unlockingApp = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            unlockAppIfNeeded()
            val path = intent.getStringExtra(EXTRA_PATH)
            presenter.onViewReady(path)
            if (path?.startsWith("entityId:") == true) {
                // Get the entity ID from a string formatted "entityId:domain.entity"
                // https://github.com/home-assistant/core/blob/dev/homeassistant/core.py#L159
                val pattern = "(?<=^entityId:)((?!.+__)(?!_)[\\da-z_]+(?<!_)\\.(?!_)[\\da-z_]+(?<!_)$)".toRegex()
                moreInfoEntity = pattern.find(path)?.value ?: ""
            }
            intent.removeExtra(EXTRA_PATH)

            if (presenter.isFullScreen() || isVideoFullScreen) {
                hideSystemUI()
            } else {
                showSystemUI()
            }
        }
    }

    override fun unlockAppIfNeeded() {
        appLocked = presenter.isAppLocked()
        if (appLocked) {
            binding.blurView.setBlurEnabled(true)
            if (!unlockingApp) {
                authenticator.authenticate(getString(commonR.string.biometric_title))
            }
            unlockingApp = true
        } else {
            binding.blurView.setBlurEnabled(false)
        }
    }

    private fun hideSystemUI() {
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUI() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
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
        presenter.setAppActive(false)
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
        isRelaunching = true
        startActivity(Intent(this, LaunchActivity::class.java))
        finish()
    }

    override fun loadUrl(url: String, keepHistory: Boolean, openInApp: Boolean) {
        if (openInApp) {
            loadedUrl = url
            clearHistory = !keepHistory
            webView.loadUrl(url)
            waitForConnection()
        } else {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to view url", e)
            }
        }
    }

    override fun setStatusBarAndNavigationBarColor(statusBarColor: Int, navigationBarColor: Int) {
        // window.statusBarColor and window.navigationBarColor must both be set before
        // windowInsetsController sets the foreground colors.

        // Set background colors
        if (statusBarColor != 0) {
            window.statusBarColor = statusBarColor
        } else {
            Log.e(TAG, "Cannot set status bar color. Skipping coloring...")
        }
        if (navigationBarColor != 0) {
            window.navigationBarColor = navigationBarColor
        } else {
            Log.e(TAG, "Cannot set navigation bar color. Skipping coloring...")
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
        if (isShowingError || !isStarted || isRelaunching) {
            return
        }
        isShowingError = true

        val serverName = if (serverManager.defaultServers.size > 1) presenter.getActiveServerName() else null
        val alert = AlertDialog.Builder(this)
            .setTitle(
                getString(
                    commonR.string.error_connection_failed_to,
                    serverName ?: getString(commonR.string.app_name)
                )
            )
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
            (errorType == ErrorType.TIMEOUT_GENERAL || errorType == ErrorType.TIMEOUT_EXTERNAL_BUS) &&
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
                serverManager.getServer(presenter.getActiveServer())?.let {
                    ioScope.launch {
                        serverManager.removeServer(it.id)
                        withContext(Dispatchers.Main) { relaunchApp() }
                    }
                }
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
                serverManager.getServer(presenter.getActiveServer())?.let {
                    ioScope.launch {
                        serverManager.removeServer(it.id)
                        withContext(Dispatchers.Main) { relaunchApp() }
                    }
                }
            }
        } else if (errorType == ErrorType.SSL) {
            if (description != null) {
                alert.setMessage(getString(commonR.string.webview_error_description) + " " + description)
            } else if (error!!.primaryError == SslError.SSL_DATE_INVALID) {
                alert.setMessage(commonR.string.webview_error_SSL_DATE_INVALID)
            } else if (error.primaryError == SslError.SSL_EXPIRED) {
                alert.setMessage(commonR.string.webview_error_SSL_EXPIRED)
            } else if (error.primaryError == SslError.SSL_IDMISMATCH) {
                alert.setMessage(commonR.string.webview_error_SSL_IDMISMATCH)
            } else if (error.primaryError == SslError.SSL_INVALID) {
                alert.setMessage(commonR.string.webview_error_SSL_INVALID)
            } else if (error.primaryError == SslError.SSL_NOTYETVALID) {
                alert.setMessage(commonR.string.webview_error_SSL_NOTYETVALID)
            } else if (error.primaryError == SslError.SSL_UNTRUSTED) {
                alert.setMessage(commonR.string.webview_error_SSL_UNTRUSTED)
            }
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
                intent.data = Uri.parse("https://www.home-assistant.io/latest-security-alert/")
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
            val isInternal = serverManager.getServer(presenter.getActiveServer())?.connection?.isInternal() == true
            val buttonRefreshesInternal = failedConnection == "external" && isInternal
            alert.setNegativeButton(
                if (buttonRefreshesInternal) {
                    commonR.string.refresh_internal
                } else {
                    commonR.string.refresh_external
                }
            ) { _, _ ->
                val url = serverManager.getServer(presenter.getActiveServer())?.let {
                    val base = it.connection.getUrl(buttonRefreshesInternal) ?: return@let null
                    Uri.parse(base.toString())
                        .buildUpon()
                        .appendQueryParameter("external_auth", "1")
                        .build()
                        .toString()
                }
                failedConnection = if (buttonRefreshesInternal) "internal" else "external"
                if (url != null) {
                    loadUrl(url = url, keepHistory = true, openInApp = true)
                } else {
                    waitForConnection()
                }
            }
            if (errorType == ErrorType.TIMEOUT_EXTERNAL_BUS) {
                alert.setNeutralButton(commonR.string.wait) { _, _ ->
                    waitForConnection()
                }
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
            message = if (resourceURL.subSequence(0, 5).toString() == "http:") {
                "http://" + message + " " + getString(commonR.string.not_private)
            } else {
                "https://$message"
            }
        }
        if (!autoAuth || authError) {
            AlertDialog.Builder(this, R.style.Authentication_Dialog)
                .setTitle(commonR.string.auth_request)
                .setMessage(message)
                .setView(dialogLayout.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (username.text.toString() != "" && password.text.toString() != "") {
                        if (remember.isChecked) {
                            if (authError) {
                                authenticationDao.update(
                                    Authentication(
                                        (resourceURL + realm),
                                        username.text.toString(),
                                        password.text.toString()
                                    )
                                )
                            } else {
                                authenticationDao.insert(
                                    Authentication(
                                        (resourceURL + realm),
                                        username.text.toString(),
                                        password.text.toString()
                                    )
                                )
                            }
                        }
                        handler.proceed(username.text.toString(), password.text.toString())
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(commonR.string.auth_cancel)
                            .setMessage(commonR.string.auth_error_message)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                authenticationDialog(handler, host, realm, authError)
                            }
                            .show()
                    }
                }
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    Toast.makeText(applicationContext, commonR.string.auth_cancel, Toast.LENGTH_SHORT)
                        .show()
                }
                .show()
        }
    }

    private fun waitForConnection() {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (
                    !isConnected &&
                    !loadedUrl.toHttpUrl().pathSegments.first().contains("api") &&
                    !loadedUrl.toHttpUrl().pathSegments.first().contains("local")
                ) {
                    showError(errorType = ErrorType.TIMEOUT_EXTERNAL_BUS)
                }
            },
            CONNECTION_DELAY
        )
    }

    override fun sendExternalBusMessage(message: ExternalBusMessage) {
        val map = mutableMapOf(
            "id" to message.id,
            "type" to message.type
        )
        message.command?.let { map["command"] = it }
        message.success?.let { map["success"] = it }
        message.result?.let { map["result"] = it }
        message.error?.let { map["error"] = it }
        message.payload?.let { map["payload"] = it }

        val json = JSONObject(map.toMap())
        val script = "externalBus($json);"

        Log.d(TAG, script)

        webView.evaluateJavascript(script, message.callback)
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
                val server = serverManager.getServer(presenter.getActiveServer())
                if (url.startsWith(server?.connection?.getUrl(true).toString()) ||
                    url.startsWith(server?.connection?.getUrl(false).toString())
                ) {
                    request.addRequestHeader("Authorization", presenter.getAuthorizationHeader())
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
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "Unable to forward download request to system", e)
                    Toast.makeText(this, commonR.string.failed_unsupported, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Workaround to sideload on Android TV and use a remote for basic navigation in WebView
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun setWebViewZoom() {
        // Set base zoom level (percentage must be scaled to device density/percentage)
        webView.setInitialScale((resources.displayMetrics.density * presenter.getPageZoomLevel()).toInt())

        // Enable pinch to zoom
        webView.settings.builtInZoomControls = presenter.isPinchToZoomEnabled()
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

    private fun openFirstViewOnDashboardIfNeeded() {
        if (presenter.isAlwaysShowFirstViewOnAppStartEnabled() &&
            LifecycleHandler.isAppInBackground()
        ) {
            // Pattern matches urls which are NOT allowed to show the first view after app is started
            // This is
            // /config/* as these are the settings of HA but NOT /config/dashboard. This is just the overview of the HA settings
            // /hassio/* as these are the addons section of HA settings.
            if (webView.url?.matches(".*://.*/(config/(?!\\bdashboard\\b)|hassio)/*.*".toRegex()) == false) {
                Log.d(TAG, "Show first view of default dashboard.")
                webView.evaluateJavascript(
                    """
                    var anchor = 'a:nth-child(1)';
                    var defaultPanel = window.localStorage.getItem('defaultPanel')?.replaceAll('"',"");
                    if(defaultPanel) anchor = 'a[href="/' + defaultPanel + '"]';
                    document.querySelector('body > home-assistant').shadowRoot.querySelector('home-assistant-main')
                                                                   .shadowRoot.querySelector('ha-sidebar')
                                                                   .shadowRoot.querySelector('paper-listbox > ' + anchor).click();
                    window.scrollTo(0, 0);
                    """,
                    null
                )
            } else {
                Log.d(TAG, "User is in the Home Assistant config. Will not show first view of the default dashboard.")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.extras?.containsKey(EXTRA_SERVER) == true) {
            intent.extras?.getInt(EXTRA_SERVER)?.let {
                presenter.setActiveServer(it)
                intent.removeExtra(EXTRA_SERVER)
            }
        }
    }
}
