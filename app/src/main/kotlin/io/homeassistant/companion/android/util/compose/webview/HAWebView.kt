package io.homeassistant.companion.android.util.compose.webview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.SdkVersion
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Process-wide instance shared by every [HAWebView], mirroring the global scope of
 * [WebView.pauseTimers]/[WebView.resumeTimers].
 */
private val webViewTimersManager = WebViewTimersManager()

/**
 * How long the frontend waits after the page is hidden before it tears down WebRTC camera streams
 * (`HIDDEN_CLEANUP_DELAY` in `ha-web-rtc-player.ts`). Its cleanup runs on a JavaScript timer, so timers
 * must keep running at least that long after the last WebView screen stops.
 */
private val FRONTEND_HIDDEN_CLEANUP_DELAY: Duration = 60.seconds

/** Delay before pausing the global WebView timers once no WebView screen is started. */
@VisibleForTesting
internal val WEBVIEW_TIMERS_PAUSE_DELAY: Duration = FRONTEND_HIDDEN_CLEANUP_DELAY + 10.seconds

const val BLANK_URL = "about:blank"

/** `external_auth` query parameter to signal to the frontend authentication uses app external authentication. */
const val EXTERNAL_AUTH_QUERY_PARAM = "external_auth"

@VisibleForTesting const val HA_WEBVIEW_TAG = "ha_web_view_tag"

/**
 * A composable that displays a WebView specifically configured for Home Assistant.
 * This WebView includes default settings for Home Assistant, such as:
 * - Javascript/dom storage enabled
 * - Zoom controls disabled
 * - Night mode support
 * - Custom user agent
 * - Transparent background
 *
 * This composable provides a convenient way to embed a WebView within a Jetpack Compose UI.
 * Further customization of the WebView instance is possible through the [configure] lambda.
 *
 * The WebView will be sized to match the [modifier].
 *
 * If the system WebView fails to initialize (e.g. due to a misconfigured or incompatible
 * WebView provider), the [onWebViewCreationFailed] callback is invoked with the exception
 * and a placeholder view is shown instead.
 *
 * @param onWebViewCreationFailed Called when the WebView fails to initialize due to a system-level
 *                                issue such as a broken or incompatible WebView provider.
 * @param modifier The modifier to be applied to this WebView.
 * @param debuggingEnabled Whether WebView contents can be debugged, null leaves the process-wide
 *                         setting untouched. Ignored in debug builds, which are always debuggable.
 * @param nightModeTheme current [NightModeTheme]
 * @param configure A lambda that allows for customization of the WebView instance.
 * @param factory A lambda that creates the WebView instance. If this returns null, a new
 *                WebView will be created with the current context. This is useful for providing
 *                a pre-configured WebView instance.
 */
@Composable
fun HAWebView(
    onWebViewCreationFailed: (Throwable) -> Unit,
    debuggingEnabled: Boolean?,
    modifier: Modifier = Modifier,
    configure: WebView.() -> Unit = {},
    factory: () -> WebView? = { null },
    // Only used when the backstack of the webView is empty
    onBackPressed: (() -> Unit)? = null,
    nightModeTheme: NightModeTheme? = null,
) {
    var webview by remember { mutableStateOf<WebView?>(null) }
    val uiMode = LocalConfiguration.current.uiMode
    val modifier = modifier.testTag(HA_WEBVIEW_TAG)

    // In preview/screenshot mode, show a placeholder instead of WebView
    // to avoid having issue with screenshots.
    if (LocalInspectionMode.current) {
        Text(
            text = "WebviewPlaceholder",
            modifier = modifier,
        )
    } else {
        AndroidView(
            factory = { context ->
                try {
                    applyWebContentsDebugging(debuggingEnabled)
                    (factory() ?: WebView(context)).apply {
                        webview = this
                        // We want the modifier to determine the size so the WebView should match the parent
                        this.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        defaultSettings()
                        configure(this)
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to create WebView, the system WebView may be misconfigured")
                    onWebViewCreationFailed(t)
                    // AndroidView requires a non-null View; return an empty placeholder
                    FrameLayout(context)
                }
            },
            update = { view ->
                nightModeTheme?.let {
                    if (view is WebView) {
                        view.settings.setNightModeTheme(it, uiMode)
                    }
                }
            },
            modifier = modifier,
            onRelease = {
                Timber.d("onRelease WebView, stopping loading")
                (it as? WebView)?.stopLoading()
                webview = null
            },
        )

        WebViewLifecycleEffect(webView = webview)

        // The setting is process-wide and can change while the WebView is shown
        LaunchedEffect(debuggingEnabled, webview) {
            if (webview != null) applyWebContentsDebugging(debuggingEnabled)
        }
    }

    // To avoid checking doUpdateVisitedHistory from the webViewClient we simply delegate the back button handling
    // to the webView and when the webview backstack is empty we call the callback given in parameter that should be
    // handle by the navHost.
    BackHandler(onBackPressed != null) {
        webview.takeIf { it?.canGoBack() == true }?.goBack() ?: onBackPressed?.invoke()
    }
}

/**
 * Freezes the [webView] while its screen is not visible: pauses the view's own processing and,
 * through the [manager], the global WebView timers once no other WebView screen is started either.
 *
 * Scoped to the composition's lifecycle owner, so leaving the screen, the app, or the composition freezes the view.
 */
@VisibleForTesting
@Composable
internal fun WebViewLifecycleEffect(webView: WebView?, manager: WebViewTimersManager = webViewTimersManager) {
    if (webView != null) {
        LifecycleStartEffect(webView) {
            Timber.d("Webview started")
            manager.onWebViewStarted(webView)
            webView.onResume()
            onStopOrDispose {
                Timber.d("Webview stopped")
                webView.onPause()
                manager.onWebViewStopped()
            }
        }
    }
}

/**
 * Reference-counts the started WebView screens to drive the process-global
 * [WebView.pauseTimers]/[WebView.resumeTimers] pair.
 *
 * Those calls affect every WebView in the process, so a screen must not pause timers while another
 * WebView screen is still visible (activity transitions overlap: the new activity starts before the
 * old one stops). Timers are resumed when the first screen starts and paused [WEBVIEW_TIMERS_PAUSE_DELAY]
 * after the last started screen stops, giving the frontend time to run its own hidden-page cleanup
 * (closing WebRTC streams) before its timers freeze. A screen starting during that delay cancels the pause.
 *
 * WebViews are only tracked weakly so the manager never keeps a disposed screen alive; the deferred pause
 * runs on whichever tracked WebView is still alive, since the call is process-wide anyway.
 *
 * @param scope The scope running the deferred pause, which must dispatch on the main thread.
 */
@MainThread
internal class WebViewTimersManager @VisibleForTesting constructor(private val scope: CoroutineScope) {

    /** Process-wide instance: the scope lives as long as the process. */
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    private var startedWebViews = 0
    private val trackedWebViews = mutableListOf<WeakReference<WebView>>()
    private var pendingPause: Job? = null

    /** Registers a started WebView screen, resuming global timers if it is the first one. */
    fun onWebViewStarted(webView: WebView) {
        track(webView)
        if (startedWebViews == 0) {
            pendingPause?.cancel()
            pendingPause = null
            webView.resumeTimers()
        }
        startedWebViews++
    }

    /** Unregisters a started WebView screen, scheduling the global timers pause if it was the last one. */
    fun onWebViewStopped() {
        if (startedWebViews == 0) {
            FailFast.fail { "onWebViewStopped called without a matching onWebViewStarted" }
            return
        }
        startedWebViews--
        if (startedWebViews == 0) {
            schedulePause()
        }
    }

    private fun track(webView: WebView) {
        trackedWebViews.removeAll { it.get() == null }
        if (trackedWebViews.none { it.get() === webView }) {
            trackedWebViews += WeakReference(webView)
        }
    }

    private fun schedulePause() {
        pendingPause = scope.launch {
            delay(WEBVIEW_TIMERS_PAUSE_DELAY)
            pendingPause = null
            val webView = trackedWebViews.firstNotNullOfOrNull { it.get() }
            if (webView == null) {
                Timber.d("Every WebView was collected before the timers pause, nothing to pause")
            } else {
                Timber.d("Pausing WebView timers")
                webView.pauseTimers()
            }
        }
    }
}

fun WebView.settings(configureDsl: WebSettings.() -> Unit) {
    try {
        settings.configureDsl()
    } catch (e: NoSuchMethodError) {
        // While displaying the WebView within a Preview or while making screenshot test getSettings is throwing
        // `java.lang.NoSuchMethodError` we catch the error to be able to continue to use Preview and screenshot tests.
        Timber.w(
            e,
            "Failed to configure WebView settings",
        )
    }
}

private fun applyWebContentsDebugging(debuggingEnabled: Boolean?) {
    when {
        BuildConfig.DEBUG -> WebView.setWebContentsDebuggingEnabled(true)
        debuggingEnabled != null -> WebView.setWebContentsDebuggingEnabled(debuggingEnabled)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.defaultSettings() {
    settings {
        // https://github.com/home-assistant/android/pull/3353
        minimumFontSize = 5
        javaScriptEnabled = true
        domStorageEnabled = true
        // https://github.com/home-assistant/android/pull/2252
        displayZoomControls = false
        userAgentString += " ${HomeAssistantApis.USER_AGENT_STRING}"
    }
    // Set WebView background color to transparent, so that the theme of the android activity has control over it.
    // This enables the ability to have the launch screen behind the WebView until the web frontend gets rendered
    setBackgroundColor(Color.TRANSPARENT)
}

@Suppress("DEPRECATION")
private fun WebSettings.setNightModeTheme(nightModeTheme: NightModeTheme, uiMode: Int) {
    if (!SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)
    ) {
        // While an up-to-date official WebView respects the app light/dark theme automatically,
        // some users are running forks where this doesn't seem to work or are reportedly
        // unable to update. These deprecated settings are set to preserve compatibility for
        // those users. Issue: https://github.com/home-assistant/android/issues/2985
        WebSettingsCompat.setForceDarkStrategy(
            this,
            WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
        )
        when (nightModeTheme) {
            NightModeTheme.DARK -> {
                WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
            }

            NightModeTheme.ANDROID, NightModeTheme.SYSTEM -> {
                val nightModeFlags = uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
                } else {
                    WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
                }
            }

            else -> {
                WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
            }
        }
    }
}
