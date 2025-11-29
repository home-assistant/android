package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.common.util.getBooleanOrElse
import io.homeassistant.companion.android.common.util.getBooleanOrNull
import io.homeassistant.companion.android.common.util.getIntOrElse
import io.homeassistant.companion.android.common.util.getIntOrNull
import io.homeassistant.companion.android.common.util.getStringOrElse
import io.homeassistant.companion.android.common.util.getStringOrNull
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.common.util.jsonObjectOrNull
import io.homeassistant.companion.android.common.util.toJsonObject
import io.homeassistant.companion.android.common.util.toJsonObjectOrNull
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.databinding.DialogAuthenticationBinding
import io.homeassistant.companion.android.improv.ui.ImprovPermissionDialog
import io.homeassistant.companion.android.improv.ui.ImprovSetupDialog
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.nfc.WriteNfcTag
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.themes.NightModeManager
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.DataUriDownloadManager
import io.homeassistant.companion.android.util.LifecycleHandler
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.compose.initializePlayer
import io.homeassistant.companion.android.util.isStarted
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.homeassistant.companion.android.webview.WebView.ErrorType
import io.homeassistant.companion.android.webview.addto.EntityAddToHandler
import io.homeassistant.companion.android.webview.externalbus.EntityAddToActionsResponse
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalConfigResponse
import io.homeassistant.companion.android.webview.externalbus.ExternalEntityAddToAction
import io.homeassistant.companion.android.webview.externalbus.NavigateTo
import io.homeassistant.companion.android.webview.externalbus.ShowSidebar
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

@AndroidEntryPoint
class WebViewActivity :
    BaseActivity(),
    io.homeassistant.companion.android.webview.WebView {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_SERVER = "server"
        const val EXTRA_SHOW_WHEN_LOCKED = "show_when_locked"

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
    private val requestImprovPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                presenter.startScanningForImprov()
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
                    Timber.d("NFC Write Complete $it")
                },
            ),
        )
    }
    private val showWebFileChooser = registerForActivityResult(ShowWebFileChooser()) { result ->
        mFilePathCallback?.onReceiveValue(result)
        mFilePathCallback = null
    }
    private val commissionMatterDevice =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            presenter.onMatterThreadIntentResult(this, result)
        }

    @Inject
    lateinit var presenter: WebViewPresenter

    @Inject
    lateinit var nightModeManager: NightModeManager

    @Inject
    lateinit var changeLog: ChangeLog

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var authenticationDao: AuthenticationDao

    @Inject
    @NamedKeyChain
    lateinit var keyChainRepository: KeyChainRepository

    @Inject
    lateinit var appVersionProvider: AppVersionProvider

    @Inject
    lateinit var entityAddToHandler: EntityAddToHandler

    private lateinit var webView: WebView
    private lateinit var loadedUrl: String
    private lateinit var decor: FrameLayout
    private var customViewFromWebView = mutableStateOf<View?>(null)
    private lateinit var authenticator: Authenticator

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
    private var appLocked = mutableStateOf(true)
    private var unlockingApp = false
    private var exoPlayer = mutableStateOf<ExoPlayer?>(null)
    private var isExoFullScreen = false
    private var playerSize = mutableStateOf<DpSize?>(null)
    private var playerTop = mutableStateOf(0.dp)
    private var playerLeft = mutableStateOf(0.dp)
    private var statusBarColor = mutableStateOf<Color?>(null)
    private var backgroundColor = mutableStateOf<Color?>(null)
    private var failedConnection = "external"
    private var clearHistory = false
    private var moreInfoEntity = ""
    private val moreInfoMutex = Mutex()
    private var currentAutoplay: Boolean? = null
    private var downloadFileUrl = ""
    private var downloadFileContentDisposition = ""
    private var downloadFileMimetype = ""
    private val javascriptInterface = "externalApp"

    private val snackbarHostState = SnackbarHostState()

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

        if (intent.extras?.containsKey(EXTRA_SERVER) == true) {
            intent.extras?.getInt(EXTRA_SERVER)?.let {
                lifecycleScope.launch {
                    presenter.setActiveServer(it)
                }
                intent.removeExtra(EXTRA_SERVER)
            }
        }

        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Initially set status and navigation bar color to colorLaunchScreenBackground to match the launch screen until the web frontend is loaded
        val colorLaunchScreenBackground = ResourcesCompat.getColor(
            resources,
            commonR.color.colorLaunchScreenBackground,
            theme,
        )
        setStatusBarAndBackgroundColor(colorLaunchScreenBackground, colorLaunchScreenBackground)

        webView = WebView(this)

        lifecycleScope.launch {
            appLocked.value = presenter.isAppLocked()
        }

        setContent {
            val player by remember { exoPlayer }
            val playerSize by remember { playerSize }
            val playerTop by remember { playerTop }
            val playerLeft by remember { playerLeft }
            val currentAppLocked by remember { appLocked }
            val customViewFromWebView by remember { customViewFromWebView }
            val statusBarColor by remember { statusBarColor }
            val backgroundColor by remember { backgroundColor }
            var nightModeTheme by remember { mutableStateOf<NightModeTheme?>(null) }
            val snackbarHostState = remember { snackbarHostState }

            LaunchedEffect(Unit) {
                nightModeTheme = nightModeManager.getCurrentNightMode()
            }

            WebViewContentScreen(
                webView,
                player,
                snackbarHostState = snackbarHostState,
                playerSize = playerSize,
                playerTop = playerTop,
                playerLeft = playerLeft,
                currentAppLocked,
                customViewFromWebView,
                nightModeTheme = nightModeTheme,
                statusBarColor = statusBarColor,
                backgroundColor = backgroundColor,
            ) { isFullScreen ->
                isExoFullScreen = isFullScreen
                if (isFullScreen) hideSystemUI() else showSystemUI()
            }
        }

        authenticator = Authenticator(this, this, ::authenticationResult)

        decor = window.decorView as FrameLayout

        val onBackPressed = object : OnBackPressedCallback(webView.canGoBack()) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressed)

        webView.apply {
            setOnTouchListener(
                object : OnSwipeListener(this@WebViewActivity) {
                    override fun onSwipe(
                        e1: MotionEvent,
                        e2: MotionEvent,
                        velocity: Float,
                        direction: GestureDirection,
                        pointerCount: Int,
                    ): Boolean {
                        if (pointerCount > 1 && velocity >= 75 && !appLocked.value) {
                            handleWebViewGesture(direction, pointerCount)
                        }
                        // Swipe as a gesture is handled async. Irregardless of the result, and to
                        // not block, we don't consume it and allow other views to respond to it.
                        // (Except if the app is locked, but that realistically shouldn't happen
                        // as the locked blur should prevent the WebView from getting swipes.)
                        return appLocked.value
                    }

                    override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean {
                        return appLocked.value
                    }
                },
            )

            lifecycleScope.launch {
                currentAutoplay = presenter.isAutoPlayVideoEnabled().apply {
                    settings.mediaPlaybackRequiresUserGesture = !this
                }
            }

            webViewClient = object : TLSWebViewClient(keyChainRepository) {
                @Deprecated("Deprecated in Java for SDK >= 23")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    Timber.e("onReceivedError: errorCode: $errorCode url:$failingUrl")
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
                                Timber.d("More info entity: $moreInfoEntity")
                                webView.evaluateJavascript(
                                    "document.querySelector(\"home-assistant\").dispatchEvent(new CustomEvent(\"hass-more-info\", { detail: { entityId: \"$moreInfoEntity\" }}))",
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
                    errorResponse: WebResourceResponse?,
                ) {
                    Timber.e(
                        "onReceivedHttpError: ${errorResponse?.statusCode} : ${errorResponse?.reasonPhrase} for: ${request?.url}",
                    )
                    if (request?.url.toString() == loadedUrl) {
                        showError()
                    }
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView,
                    handler: HttpAuthHandler,
                    host: String,
                    realm: String,
                ) {
                    var authError = false
                    if (System.currentTimeMillis() <= (firstAuthTime + 500)) {
                        authError = true
                    }
                    authenticationDialog(handler, host, resourceURL, realm, authError)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Timber.e("onReceivedSslError: $error")
                    showError(
                        ErrorType.SSL,
                        error,
                        null,
                    )
                }

                override fun onRenderProcessGone(view: WebView?, handler: RenderProcessGoneDetail?): Boolean {
                    Timber.e("onRenderProcessGone: webView crashed")
                    view?.let {
                        reload()
                        webViewAddJavascriptInterface()
                    }

                    return true
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    resourceURL = url!!
                }

                // Override deprecated method for backward compatibility with API 23 and below.
                // The non-deprecated shouldOverrideUrlLoading(WebView, WebResourceRequest) is not invoked
                // on these older Android versions, so this method remains necessary.
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        try {
                            if (it.startsWith(APP_PREFIX)) {
                                Timber.d("Launching the app")
                                val intent = packageManager.getLaunchIntentForPackage(
                                    it.substringAfter(APP_PREFIX),
                                )
                                if (intent != null) {
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    startActivity(intent)
                                } else {
                                    Timber.w("No intent to launch app found, opening app store")
                                    val marketIntent = Intent(Intent.ACTION_VIEW)
                                    marketIntent.data =
                                        (MARKET_PREFIX + it.substringAfter(APP_PREFIX)).toUri()
                                    startActivity(marketIntent)
                                }
                                return true
                            } else if (it.startsWith(INTENT_PREFIX)) {
                                Timber.d("Launching the intent")
                                val intent =
                                    Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                                val intentPackage = intent.`package`?.let { it1 ->
                                    packageManager.getLaunchIntentForPackage(
                                        it1,
                                    )
                                }
                                if (intentPackage == null && !intent.`package`.isNullOrEmpty()) {
                                    Timber.w("No app found for intent prefix, opening app store")
                                    val marketIntent = Intent(Intent.ACTION_VIEW)
                                    marketIntent.data =
                                        (MARKET_PREFIX + intent.`package`.toString()).toUri()
                                    startActivity(marketIntent)
                                } else {
                                    startActivity(intent)
                                }
                                return true
                            } else if (!webView.url.toString().contains(it)) {
                                Timber.d("Launching browser")
                                val browserIntent = Intent(Intent.ACTION_VIEW, it.toUri())
                                startActivity(browserIntent)
                                return true
                            } else {
                                // Do nothing.
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Unable to override the URL")
                        }
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onBackPressed.isEnabled = canGoBack()
                    presenter.stopScanningForImprov(false)
                }
            }

            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
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
                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
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
                    val alreadyGranted = ArrayList<String>()
                    val toBeGranted = ArrayList<String>()
                    request?.resources?.forEach {
                        if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                alreadyGranted.add(it)
                            } else {
                                toBeGranted.add(android.Manifest.permission.CAMERA)
                            }
                        } else if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                alreadyGranted.add(it)
                            } else {
                                toBeGranted.add(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    if (alreadyGranted.isNotEmpty()) {
                        request?.grant(alreadyGranted.toTypedArray())
                    }
                    if (toBeGranted.isNotEmpty()) {
                        requestPermissions.launch(
                            toBeGranted.toTypedArray(),
                        )
                    }
                }

                override fun onShowFileChooser(
                    view: WebView,
                    uploadMsg: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean {
                    mFilePathCallback = uploadMsg
                    showWebFileChooser.launch(fileChooserParams)
                    return true
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customViewFromWebView.value = view
                    hideSystemUI()
                    isVideoFullScreen = true
                }

                override fun onHideCustomView() {
                    customViewFromWebView.value = null
                    lifecycleScope.launch {
                        if (!presenter.isFullScreen()) {
                            showSystemUI()
                        }
                    }
                    isVideoFullScreen = false
                    super.onHideCustomView()
                }
            }

            webViewAddJavascriptInterface()
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                lifecycleScope.launch {
                    if (presenter.isFullScreen()) {
                        hideSystemUI()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val webviewPackage = WebViewCompat.getCurrentWebViewPackage(this)
            Timber.d(
                "Current webview package ${webviewPackage?.packageName} and version ${webviewPackage?.versionName}",
            )
        }

        lifecycleScope.launch {
            if (presenter.isKeepScreenOnEnabled()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                presenter.getMatterThreadStepFlow().collect {
                    Timber.d("Matter/Thread step changed to $it")
                    when (it) {
                        MatterThreadStep.THREAD_EXPORT_TO_SERVER_MATTER,
                        MatterThreadStep.THREAD_EXPORT_TO_SERVER_ONLY,
                        MatterThreadStep.MATTER_IN_PROGRESS,
                        -> {
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
                            Toast.makeText(
                                this@WebViewActivity,
                                commonR.string.thread_export_success,
                                Toast.LENGTH_SHORT,
                            ).show()
                            alertDialog?.cancel()
                            presenter.finishMatterThreadFlow()
                        }

                        MatterThreadStep.ERROR_MATTER -> {
                            Toast.makeText(
                                this@WebViewActivity,
                                commonR.string.matter_commissioning_unavailable,
                                Toast.LENGTH_SHORT,
                            ).show()
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
                            Toast.makeText(
                                this@WebViewActivity,
                                commonR.string.thread_export_unavailable,
                                Toast.LENGTH_SHORT,
                            ).show()
                            alertDialog?.cancel()
                            presenter.finishMatterThreadFlow()
                        }

                        else -> {} // Do nothing
                    }
                }
            }
        }
    }

    private fun webViewAddJavascriptInterface() {
        webView.apply {
            removeJavascriptInterface(javascriptInterface)
            addJavascriptInterface(
                object : Any() {
                    // TODO This feature is deprecated and should be removed after 2022.6
                    @JavascriptInterface
                    fun onHomeAssistantSetTheme() {
                        // We need to launch the getAndSetStatusBarNavigationBarColors in another thread, because otherwise the evaluateJavascript inside the method
                        // will not trigger it's callback method :/
                        lifecycleScope.launch(Dispatchers.Main) {
                            getAndSetStatusBarNavigationBarColors()
                        }
                    }

                    @JavascriptInterface
                    fun getExternalAuth(payload: String) {
                        payload.toJsonObjectOrNull().let {
                            presenter.onGetExternalAuth(
                                this@WebViewActivity,
                                it?.getStringOrNull("callback") ?: "",
                                it?.getBooleanOrNull("force") ?: false,
                            )
                        }
                    }

                    @JavascriptInterface
                    fun revokeExternalAuth(callback: String) {
                        presenter.onRevokeExternalAuth(callback.toJsonObjectOrNull()?.getStringOrNull("callback") ?: "")
                        isRelaunching = true // Prevent auth errors from showing
                    }

                    @JavascriptInterface
                    fun externalBus(message: String) {
                        Timber.d("External bus $message")
                        webView.post {
                            val json = message.toJsonObjectOrNull() ?: return@post
                            when (json.getStringOrNull("type")) {
                                "connection-status" -> {
                                    isConnected = json["payload"]?.jsonObjectOrNull()
                                        ?.getStringOrNull("event") == "connected"
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
                                            !isAutomotive()
                                        ) {
                                            1
                                        } else {
                                            0
                                        }
                                    sendExternalBusMessage(
                                        ExternalConfigResponse(
                                            id = json["id"],
                                            hasNfc = hasNfc,
                                            canCommissionMatter = canCommissionMatter,
                                            canExportThread = canExportThread,
                                            hasBarCodeScanner = hasBarCodeScanner,
                                            appVersion = appVersionProvider(),
                                        ),
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
                                        null,
                                    )
                                }

                                "assist/show" -> {
                                    val payload = json["payload"]?.jsonObjectOrNull()
                                    startActivity(
                                        AssistActivity.newInstance(
                                            this@WebViewActivity,
                                            serverId = presenter.getActiveServer(),
                                            pipelineId = payload?.getStringOrNull("pipeline_id"),
                                            startListening = payload?.getBooleanOrNull("start_listening") ?: true,
                                        ),
                                    )
                                }

                                "config_screen/show" ->
                                    startActivity(
                                        SettingsActivity.newInstance(this@WebViewActivity),
                                    )

                                "tag/write" ->
                                    writeNfcTag.launch(
                                        WriteNfcTag.Input(
                                            tagId = json["payload"]?.jsonObjectOrNull()?.getStringOrNull("tag"),
                                            messageId = json.getIntOrElse("id", -1),
                                        ),
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
                                    val payload = json["payload"]?.jsonObjectOrNull()
                                    if (payload?.containsKey("title") != true ||
                                        !payload.containsKey("description")
                                    ) {
                                        return@post
                                    }
                                    startActivity(
                                        BarcodeScannerActivity.newInstance(
                                            this@WebViewActivity,
                                            messageId = json.getIntOrElse("id", 0),
                                            title = payload.getStringOrElse("title", ""),
                                            subtitle = payload.getStringOrElse("description", ""),
                                            action = payload.getStringOrNull("alternative_option_label")?.ifBlank {
                                                null
                                            },
                                        ),
                                    )
                                }

                                "improv/scan" -> scanForImprov()
                                "improv/configure_device" -> {
                                    val payload = json["payload"]?.jsonObjectOrNull()
                                    val deviceName = payload?.getStringOrNull("name") ?: return@post
                                    configureImprovDevice(deviceName)
                                }

                                "exoplayer/play_hls" -> exoPlayHls(json)
                                "exoplayer/stop" -> exoStopHls()
                                "exoplayer/resize" -> exoResizeHls(json)
                                "haptic" -> processHaptic(
                                    json["payload"]?.jsonObjectOrNull()?.getStringOrNull("hapticType") ?: "",
                                )
                                "theme-update" -> getAndSetStatusBarNavigationBarColors()
                                "entity/add_to/get_actions" -> getActions(json)
                                "entity/add_to" -> addEntityTo(json)
                                else -> presenter.onExternalBusMessage(json)
                            }
                        }
                    }

                    @JavascriptInterface
                    fun handleBlob(data: String?, filename: String?) {
                        data?.let {
                            lifecycleScope.launch {
                                DataUriDownloadManager.saveDataUri(
                                    this@WebViewActivity,
                                    url = data,
                                    mimetype = "",
                                    filename = filename,
                                )
                            }
                        }
                    }
                },
                javascriptInterface,
            )
        }
    }

    private fun addEntityTo(json: JsonObject) {
        val payload = json["payload"]?.jsonObjectOrNull()
        val entityId = payload?.getStringOrNull("entity_id")
        val appPayload = payload?.getStringOrNull("app_payload")
        if (entityId != null && appPayload != null) {
            val action = ExternalEntityAddToAction.appPayloadToAction(appPayload)
            lifecycleScope.launch {
                entityAddToHandler.execute(this@WebViewActivity, action, entityId) { message, action ->
                    snackbarHostState.showSnackbar(
                        message,
                        action,
                        duration = SnackbarDuration.Short,
                    ) == SnackbarResult.ActionPerformed
                }
            }
        } else {
            FailFast.fail { "Missing entity_id or app_payload to addEntityTo" }
        }
    }

    private fun getActions(json: JsonObject) {
        val payload = json["payload"]?.jsonObjectOrNull()
        val entityId = payload?.getStringOrNull("entity_id")
        entityId?.let {
            lifecycleScope.launch {
                val actions = entityAddToHandler.actionsForEntity(this@WebViewActivity, entityId)
                sendExternalBusMessage(
                    EntityAddToActionsResponse(
                        id = json["id"],
                        actions = actions.map { action ->
                            ExternalEntityAddToAction.fromAction(this@WebViewActivity, action)
                        },
                    ),
                )
            }
        } ?: FailFast.fail {
            "entity_id not present in response from External bus for `entity/add_to/get_actions`"
        }
    }

    private fun handleWebViewGesture(direction: GestureDirection, pointerCount: Int) {
        lifecycleScope.launch {
            when (presenter.getGestureAction(direction, pointerCount)) {
                GestureAction.NONE -> {
                    // Do nothing
                }

                GestureAction.QUICKBAR_DEFAULT -> {
                    webView.dispatchKeyDownEventToDocument("e", "KeyE", 69)
                }

                GestureAction.QUICKBAR_DEVICES -> {
                    webView.dispatchKeyDownEventToDocument("d", "KeyD", 68)
                }

                GestureAction.QUICKBAR_COMMANDS -> {
                    webView.dispatchKeyDownEventToDocument("c", "KeyC", 67)
                }

                GestureAction.SHOW_SIDEBAR -> sendExternalBusMessage(ShowSidebar)

                GestureAction.OPEN_ASSIST -> startActivity(
                    AssistActivity.newInstance(
                        this@WebViewActivity,
                        serverId = presenter.getActiveServer(),
                    ),
                )

                GestureAction.NAVIGATE_FORWARD -> {
                    if (webView.canGoForward()) webView.goForward()
                }

                GestureAction.NAVIGATE_DASHBOARD -> navigateToDefaultDashboard()

                GestureAction.NAVIGATE_RELOAD -> {
                    webView.reload()
                }

                GestureAction.SERVER_LIST -> {
                    val serverChooser = ServerChooserFragment()
                    supportFragmentManager.setFragmentResultListener(
                        ServerChooserFragment.RESULT_KEY,
                        this@WebViewActivity,
                    ) {
                            _,
                            bundle,
                        ->
                        if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                            lifecycleScope.launch {
                                presenter.switchActiveServer(bundle.getInt(ServerChooserFragment.RESULT_SERVER))
                            }
                        }
                        supportFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
                    }
                    serverChooser.show(supportFragmentManager, ServerChooserFragment.TAG)
                }

                GestureAction.SERVER_NEXT -> presenter.nextServer()

                GestureAction.SERVER_PREVIOUS -> presenter.previousServer()

                GestureAction.OPEN_APP_SETTINGS -> startActivity(SettingsActivity.newInstance(this@WebViewActivity))

                GestureAction.OPEN_APP_DEVELOPER -> startActivity(
                    SettingsActivity.newInstance(
                        context = this@WebViewActivity,
                        screen = SettingsActivity.Deeplink.Developer,
                    ),
                )
            }
        }
    }

    private fun getAndSetStatusBarNavigationBarColors() {
        val htmlArraySpacer = "-SPACER-"
        webView.evaluateJavascript(
            "[" +
                "document.getElementsByTagName('html')[0].computedStyleMap().get('--app-header-background-color')[0]," +
                "document.getElementsByTagName('html')[0].computedStyleMap().get('--primary-background-color')[0]" +
                "].join('" + htmlArraySpacer + "')",
        ) { webViewColors ->
            lifecycleScope.launch(Dispatchers.Main) {
                var statusBarColor = 0
                var backgroundColor = 0

                if (!webViewColors.isNullOrEmpty() && webViewColors != "null") {
                    val trimmedColorString = webViewColors.substring(1, webViewColors.length - 1).trim()
                    val colors = trimmedColorString.split(htmlArraySpacer)

                    Timber.d("Color from webview is \"$trimmedColorString\"")

                    statusBarColor = presenter.parseWebViewColor(colors[0].trim())
                    backgroundColor = presenter.parseWebViewColor(colors[1].trim())
                }

                setStatusBarAndBackgroundColor(statusBarColor, backgroundColor)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.onStart(this)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // if null it means that the settings were not yet read so we should not recreate
            if (currentAutoplay != null && currentAutoplay != presenter.isAutoPlayVideoEnabled()) {
                recreate()
            }

            appLocked.value = presenter.isAppLocked()
            presenter.updateActiveServer()
        }

        setWebViewZoom()

        lifecycleScope.launch {
            SensorWorker.start(this@WebViewActivity)
            WebsocketManager.start(this@WebViewActivity)

            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || presenter.isWebViewDebugEnabled())

            requestedOrientation = when (presenter.getScreenOrientation()) {
                getString(
                    R.string.screen_orientation_option_array_value_portrait,
                ),
                -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                getString(
                    R.string.screen_orientation_option_array_value_landscape,
                ),
                -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            if (presenter.isKeepScreenOnEnabled()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            checkAndWarnForDisabledLocation()
            changeLog.showChangeLog(this@WebViewActivity, false)
        }

        if (::loadedUrl.isInitialized) {
            waitForConnection()
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            openFirstViewOnDashboardIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            presenter.setAppActive(false)
        }
        if (!isFinishing && !isRelaunching) SensorReceiver.updateAllSensors(this)
    }

    private suspend fun checkAndWarnForDisabledLocation() {
        var showLocationDisabledWarning = false
        val settingsWithLocationPermissions = mutableListOf<String>()
        if (!DisabledLocationHandler.isLocationEnabled(this) && presenter.isSsidUsed()) {
            showLocationDisabledWarning = true
            settingsWithLocationPermissions.add(getString(commonR.string.pref_connection_homenetwork))
        }
        for (manager in SensorReceiver.MANAGERS) {
            for (basicSensor in manager.getAvailableSensors(this)) {
                if (manager.isEnabled(this, basicSensor)) {
                    val permissions = manager.requiredPermissions(this, basicSensor.id)

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
            DisabledLocationHandler.showLocationDisabledWarnDialog(
                this@WebViewActivity,
                settingsWithLocationPermissions.toTypedArray(),
                true,
            )
        } else {
            DisabledLocationHandler.removeLocationDisabledWarning(this@WebViewActivity)
        }
    }

    fun exoPlayHls(json: JsonObject) {
        val payload = json["payload"]?.jsonObjectOrNull()
        val uri = payload?.getStringOrNull("url")?.toUri() ?: return
        val isMuted = payload.getBooleanOrElse("muted", false)
        runOnUiThread {
            exoPlayer.value = initializePlayer(this).apply {
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                addListener(
                    object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            super.onVideoSizeChanged(videoSize)
                            if (videoSize.height == 0 || videoSize.width == 0) return
                            playerSize.value?.let {
                                // If height is already set, it means the frontend has imposed a constraint; avoid overriding.
                                if (it.height == 0.dp) {
                                    playerSize.value = DpSize(it.width, it.width * videoSize.height / videoSize.width)
                                }
                            }
                        }
                    },
                )
                prepare()
                volume = if (isMuted) 0f else 1f
            }
        }
        sendExternalBusMessage(
            ExternalBusMessage(
                id = json["id"],
                type = "result",
                success = true,
                callback = {
                    Timber.d("Callback $it")
                },
            ),
        )
    }

    fun exoStopHls() {
        runOnUiThread {
            // We might be in fullscreen mode, so we display back the system UI just in case
            // same for the fullscreen status of ExoPlayer
            isExoFullScreen = false
            showSystemUI()
            exoPlayer.value?.release()
            exoPlayer.value = null
            playerSize.value = null
            playerTop.value = 0.dp
            playerLeft.value = 0.dp
        }
    }

    @OptIn(UnstableApi::class)
    fun exoResizeHls(json: JsonObject) {
        val payload = json["payload"]?.jsonObjectOrNull() ?: return
        // Payload is https://developer.mozilla.org/en-US/docs/Web/API/Element/getBoundingClientRect
        // The values are already scaled to the screen.
        // We only need to store the top left corner for the offset and the player size

        val left = payload.getIntOrElse("left", 0)
        val top = payload.getIntOrElse("top", 0)
        val right = payload.getIntOrElse("right", 0)
        // if the bottom value is not 0 we should take it it is a constraint from the frontend, otherwise we try to compute the
        // height based on the video's aspect ratio if available.
        val bottom =
            payload.getIntOrNull("bottom")?.takeIf { it > 0 } ?: exoPlayer.value?.videoFormat?.let { videoFormat ->
                if (videoFormat.width > 0) {
                    // Calculate height of the video based on aspect ratio
                    val width = right - left
                    val videoHeight = width * videoFormat.height / videoFormat.width
                    (top + videoHeight)
                } else {
                    payload.getIntOrNull("bottom")
                }
            } ?: payload.getIntOrElse("bottom", 0)

        playerTop.value = top.dp
        playerLeft.value = left.dp
        playerSize.value = DpSize((right - left).dp, (bottom - top).dp)
    }

    fun processHaptic(hapticType: String) {
        val vm = getSystemService<Vibrator>()

        Timber.d("Processing haptic tag for $hapticType")
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
                Timber.d("Authentication successful, unlocking app")
                appLocked.value = false
                lifecycleScope.launch {
                    presenter.setAppActive(true)
                }
            }

            Authenticator.CANCELED -> {
                Timber.d("Authentication canceled by user, closing activity")
                finishAffinity()
            }

            else -> Timber.d("Authentication failed, retry attempts allowed")
        }
        unlockingApp = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            lifecycleScope.launch {
                unlockAppIfNeeded()
                var path = intent.getStringExtra(EXTRA_PATH)
                if (path?.startsWith("entityId:") == true) {
                    // Get the entity ID from a string formatted "entityId:domain.entity"
                    // https://github.com/home-assistant/core/blob/dev/homeassistant/core.py#L159
                    val pattern = "(?<=^entityId:)((?!.+__)(?!_)[\\da-z_]+(?<!_)\\.(?!_)[\\da-z_]+(?<!_)$)".toRegex()
                    val entity = pattern.find(path)?.value ?: ""
                    if (
                        entity.isNotBlank() &&
                        serverManager.getServer(presenter.getActiveServer())?.version?.isAtLeast(2025, 6, 0) == true
                    ) {
                        path = "/?more-info-entity-id=$entity"
                    } else {
                        moreInfoEntity = entity
                    }
                }
                presenter.onViewReady(path)
                intent.removeExtra(EXTRA_PATH)

                if (presenter.isFullScreen() || isVideoFullScreen) {
                    hideSystemUI()
                } else {
                    showSystemUI()
                }
            }
        }
    }

    override suspend fun unlockAppIfNeeded() {
        appLocked.value = presenter.isAppLocked()
        if (appLocked.value) {
            if (!unlockingApp) {
                authenticator.authenticate(getString(commonR.string.biometric_title))
            }
            unlockingApp = true
        }
    }

    private fun hideSystemUI() {
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUI() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        lifecycleScope.launch {
            presenter.setAppActive(false)
        }
        videoHeight = decor.height
        val bounds = Rect(0, 0, 1920, 1080)
        if (isVideoFullScreen or isExoFullScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()
                mPictureInPictureParamsBuilder.setAspectRatio(
                    Rational(
                        bounds.width(),
                        bounds.height(),
                    ),
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
                val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(browserIntent)
            } catch (e: Exception) {
                Timber.e(e, "Unable to view url")
            }
        }
    }

    override fun setStatusBarAndBackgroundColor(statusBarColor: Int, backgroundColor: Int) {
        // Set background colors
        if (statusBarColor != 0) {
            this.statusBarColor.value = Color(statusBarColor)
        } else {
            Timber.e("Cannot set status bar color. Skipping coloring...")
        }
        if (backgroundColor != 0) {
            this.backgroundColor.value = Color(backgroundColor)
        } else {
            Timber.e("Cannot set background color. Skipping coloring...")
        }

        // Adjust the color of system bar font/icons to ensure proper contrast with
        // the current Home Assistant theme's background color.
        if (statusBarColor != 0) {
            windowInsetsController.isAppearanceLightStatusBars = !isColorDark(statusBarColor)
        }
        if (backgroundColor != 0) {
            windowInsetsController.isAppearanceLightNavigationBars = !isColorDark(backgroundColor)
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

    override fun showError(errorType: ErrorType, error: SslError?, description: String?) {
        if (isShowingError || !isStarted || isRelaunching) {
            return
        }
        isShowingError = true

        lifecycleScope.launch {
            val serverName = if (serverManager.defaultServers.size > 1) presenter.getActiveServerName() else null
            val alert = AlertDialog.Builder(this@WebViewActivity)
                .setTitle(
                    getString(
                        commonR.string.error_connection_failed_to,
                        serverName ?: getString(commonR.string.app_name),
                    ),
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
                return@launch
            } else if (tlsWebViewClient?.isTLSClientAuthNeeded == true &&
                errorType == ErrorType.AUTHENTICATION &&
                tlsWebViewClient.hasUserDeniedAccess
            ) {
                // If no key is available to the app
                alert.setMessage(commonR.string.tls_cert_not_found_message)
                alert.setTitle(commonR.string.tls_cert_title)
                alert.setPositiveButton(android.R.string.ok) { _, _ ->
                    ioScope.launch {
                        serverManager.getServer(presenter.getActiveServer())?.let {
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
                    ioScope.launch {
                        serverManager.getServer(presenter.getActiveServer())?.let {
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
                    startActivity(SettingsActivity.newInstance(this@WebViewActivity))
                }
                alert.setNeutralButton(commonR.string.exit) { _, _ ->
                    finishAffinity()
                }
            } else if (errorType == ErrorType.SECURITY_WARNING) {
                alert.setTitle(commonR.string.security_vulnerably_title)
                alert.setMessage(commonR.string.security_vulnerably_message)
                alert.setPositiveButton(commonR.string.security_vulnerably_view) { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = "https://www.home-assistant.io/latest-security-alert/".toUri()
                    startActivity(intent)
                }
                alert.setNegativeButton(commonR.string.security_vulnerably_understand) { _, _ ->
                    // Noop
                }
            } else {
                alert.setMessage(commonR.string.webview_error)
                alert.setPositiveButton(commonR.string.settings) { _, _ ->
                    startActivity(SettingsActivity.newInstance(this@WebViewActivity))
                }
                val isInternal = serverManager.getServer(presenter.getActiveServer())?.connection?.isInternal() == true
                val buttonRefreshesInternal = failedConnection == "external" && isInternal
                alert.setNegativeButton(
                    if (buttonRefreshesInternal) {
                        commonR.string.refresh_internal
                    } else {
                        commonR.string.refresh_external
                    },
                ) { _, _ ->
                    lifecycleScope.launch {
                        val url = serverManager.getServer(presenter.getActiveServer())?.let {
                            val base = it.connection.getUrl(buttonRefreshesInternal) ?: return@let null
                            base.toString().toUri()
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
    }

    @SuppressLint("InflateParams")
    private fun authenticationDialog(
        handler: HttpAuthHandler,
        host: String,
        resource: String,
        realm: String,
        authError: Boolean,
    ) {
        lifecycleScope.launch {
            val hostKey = resource + realm
            val httpAuth = authenticationDao.get(hostKey)

            val dialogLayout = DialogAuthenticationBinding.inflate(layoutInflater)
            val username = dialogLayout.username
            val password = dialogLayout.password
            val remember = dialogLayout.checkBox
            val viewPassword = dialogLayout.viewPassword

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

            if (!httpAuth?.host.isNullOrBlank() && !authError) {
                handler.proceed(httpAuth.username, httpAuth.password)
                firstAuthTime = System.currentTimeMillis()
                return@launch
            }

            var message = host + " " + getString(commonR.string.required_fields)
            if (resource.length >= 5) {
                message = if (resource.subSequence(0, 5).toString() == "http:") {
                    "http://" + message + " " + getString(commonR.string.not_private)
                } else {
                    "https://$message"
                }
            }
            isShowingError = true
            AlertDialog.Builder(this@WebViewActivity, R.style.Authentication_Dialog)
                .setTitle(commonR.string.auth_request)
                .setMessage(message)
                .setView(dialogLayout.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (username.text.toString() != "" && password.text.toString() != "") {
                        if (remember.isChecked) {
                            val auth = Authentication(
                                hostKey,
                                username.text.toString(),
                                password.text.toString(),
                            )
                            lifecycleScope.launch {
                                if (authError) {
                                    authenticationDao.update(auth)
                                } else {
                                    authenticationDao.insert(auth)
                                }
                            }
                        }
                        handler.proceed(username.text.toString(), password.text.toString())
                    } else {
                        AlertDialog.Builder(this@WebViewActivity)
                            .setTitle(commonR.string.auth_cancel)
                            .setMessage(commonR.string.auth_error_message)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                authenticationDialog(handler, host, resource, realm, authError)
                            }
                            .show()
                    }
                }
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    Toast.makeText(applicationContext, commonR.string.auth_cancel, Toast.LENGTH_SHORT)
                        .show()
                }
                .setOnDismissListener {
                    isShowingError = false
                    waitForConnection()
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
            CONNECTION_DELAY,
        )
    }

    override fun sendExternalBusMessage(message: ExternalBusMessage) {
        val map = mutableMapOf(
            "id" to message.id,
            "type" to message.type,
        )
        message.command?.let { map["command"] = it }
        message.success?.let { map["success"] = it }
        message.result?.let { map["result"] = it }
        message.error?.let { map["error"] = it }
        message.payload?.let { map["payload"] = it }
        val jsonObject = map.toJsonObject()
        val json = Json.encodeToString(jsonObject)
        val script = "externalBus($json);"

        Timber.d("Sending: $script")

        webView.evaluateJavascript(script, message.callback)
    }

    private fun downloadFile(url: String, contentDisposition: String, mimetype: String) {
        Timber.d("WebView requested download of $url")
        val uri = url.toUri()
        when (uri.scheme?.lowercase()) {
            "blob" -> {
                triggerBlobDownload(url, contentDisposition, mimetype)
            }

            "http", "https" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val request = DownloadManager.Request(uri)
                        .setMimeType(mimetype)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimetype),
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

                    getSystemService<DownloadManager>()?.enqueue(request)
                        ?: Timber.d("Unable to start download, cannot get DownloadManager")
                }
            }

            "data" -> {
                lifecycleScope.launch {
                    DataUriDownloadManager.saveDataUri(this@WebViewActivity, url, mimetype)
                }
            }

            else -> {
                Timber.w("Received download request for unsupported scheme ${uri.scheme}, forwarding to system")
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (e: ActivityNotFoundException) {
                    Timber.e(e, "Unable to forward download request to system")
                    Toast.makeText(this, commonR.string.failed_unsupported, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun triggerBlobDownload(url: String, contentDisposition: String, mimetype: String) {
        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val jsCode = """
        (function() {
            var url = '$url';
            var xhr = new XMLHttpRequest();
            xhr.open('GET', url, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (xhr.status == 200) {
                    var blob = xhr.response;
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        $javascriptInterface.handleBlob(reader.result, '$filename');
                    };
                    reader.readAsDataURL(blob);
                }
            };
            xhr.send();
        })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    /**
     * Send a key event to the webview's document root, to trigger frontend actions. Unlike the default
     * [WebView.dispatchKeyEvent] function, this does not used the focused element (to avoid text inputs).
     * The parameters should provide a JavaScript KeyboardEvent's properties.
     */
    private fun WebView.dispatchKeyDownEventToDocument(key: String, code: String, keyCode: Int) {
        val eventCode = """
        var event = new KeyboardEvent('keydown', {
            key: '$key',
            code: '$code',
            keyCode: $keyCode,
            which: $keyCode,
            bubbles: true,
            cancelable: true
        });
        document.dispatchEvent(event);
        """.trimIndent()
        evaluateJavascript(eventCode, null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Workaround to sideload on Android TV and use a remote for basic navigation in WebView
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun setWebViewZoom() = lifecycleScope.launch {
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
            """,
        ) {}
    }

    private suspend fun openFirstViewOnDashboardIfNeeded() {
        if (presenter.isAlwaysShowFirstViewOnAppStartEnabled() &&
            LifecycleHandler.isAppInBackground()
        ) {
            // Pattern matches urls which are NOT allowed to show the first view after app is started
            // This is
            // /config/* as these are the settings of HA but NOT /config/dashboard. This is just the overview of the HA settings
            // /hassio/* as these are the addons section of HA settings.
            if (webView.url?.matches(".*://.*/(config/(?!\\bdashboard\\b)|hassio)/*.*".toRegex()) == false) {
                Timber.d("Show first view of default dashboard.")
                navigateToDefaultDashboard()
            } else {
                Timber.d("User is in the Home Assistant config. Will not show first view of the default dashboard.")
            }
        }
    }

    /** Clear history and replace the current page with the default dashboard. */
    private fun navigateToDefaultDashboard() {
        // This way the user have a clear history stack.
        webView.clearHistory()

        lifecycleScope.launch {
            if (serverManager.getServer(presenter.getActiveServer())?.version?.isAtLeast(2025, 6, 0) == true) {
                sendExternalBusMessage(
                    NavigateTo("/", true),
                )
            } else {
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
                    null,
                )
            }
        }
    }

    private fun scanForImprov() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Timber.d("Improv scan request ignored because device doesn't have Bluetooth")
            return
        }
        if (!hasWindowFocus()) {
            Timber.d("Improv scan request ignored because webview doesn't have focus")
            return
        }
        lifecycleScope.launch {
            if (presenter.shouldShowImprovPermissions()) {
                supportFragmentManager.setFragmentResultListener(
                    ImprovPermissionDialog.RESULT_KEY,
                    this@WebViewActivity,
                ) {
                        _,
                        bundle,
                    ->
                    if (bundle.getBoolean(ImprovPermissionDialog.RESULT_GRANTED, false)) {
                        presenter.startScanningForImprov()
                    }
                    supportFragmentManager.clearFragmentResultListener(ImprovPermissionDialog.RESULT_KEY)
                }
                val dialog = ImprovPermissionDialog()
                dialog.show(supportFragmentManager, ImprovPermissionDialog.TAG)
            } else {
                val safePermissionToRequest = presenter.shouldRequestImprovPermission()
                if (safePermissionToRequest != null) {
                    requestImprovPermission.launch(safePermissionToRequest)
                } else {
                    presenter.startScanningForImprov()
                }
            }
        }
    }

    private fun configureImprovDevice(deviceName: String) {
        supportFragmentManager.setFragmentResultListener(ImprovSetupDialog.RESULT_KEY, this) { _, bundle ->
            if (bundle.containsKey(ImprovSetupDialog.RESULT_DOMAIN)) {
                bundle.getString(ImprovSetupDialog.RESULT_DOMAIN)?.let { improvDomain ->
                    lifecycleScope.launch {
                        val url = serverManager.getServer(presenter.getActiveServer())?.let url@{
                            val base = it.connection.getUrl() ?: return@url null
                            base.toString().toUri()
                                .buildUpon()
                                .appendEncodedPath("config/integrations/dashboard/add")
                                .appendQueryParameter("domain", improvDomain)
                                .appendQueryParameter("external_auth", "1")
                                .build()
                                .toString()
                        }
                        if (url != null) {
                            loadUrl(url = url, keepHistory = true, openInApp = true)
                        }
                    }
                }
                supportFragmentManager.clearFragmentResultListener(ImprovSetupDialog.RESULT_KEY)
            }
        }
        val dialog = ImprovSetupDialog.newInstance(deviceName)
        dialog.show(supportFragmentManager, ImprovSetupDialog.TAG)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.extras?.containsKey(EXTRA_SERVER) == true) {
            intent.extras?.getInt(EXTRA_SERVER)?.let {
                lifecycleScope.launch {
                    presenter.setActiveServer(it)
                }
                intent.removeExtra(EXTRA_SERVER)
            }
        }
    }
}
