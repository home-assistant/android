package io.homeassistant.companion.android.util.compose.webview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import timber.log.Timber

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
 * @param modifier The modifier to be applied to this WebView.
 * @param nightModeTheme current [NightModeTheme]
 * @param configure A lambda that allows for customization of the WebView instance.
 * @param factory A lambda that creates the WebView instance. If this returns null, a new
 *                WebView will be created with the current context. This is useful for providing
 *                a pre-configured WebView instance.
 */
@Composable
fun HAWebView(
    nightModeTheme: NightModeTheme?,
    modifier: Modifier = Modifier,
    configure: WebView.() -> Unit = {},
    factory: () -> WebView? = { null },
) {
    val uiMode = LocalConfiguration.current.uiMode

    AndroidView(
        factory = { context ->
            (factory() ?: WebView(context)).apply {
                // We want the modifier to determine the size so the WebView should match the parent
                this.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )

                settings {
                    // Use DSL to catch `NoSuchMethodError`
                    defaultSettings()
                }

                // Set WebView background color to transparent, so that the theme of the android activity has control over it.
                // This enables the ability to have the launch screen behind the WebView until the web frontend gets rendered
                setBackgroundColor(Color.TRANSPARENT)

                configure(this)
            }
        },
        update = { webView ->
            nightModeTheme?.let {
                webView.settings.setNightModeTheme(it, uiMode)
            }
        },
        onRelease = {
            Timber.d("onRelease WebView, stopping loading")
            it.stopLoading()
        },
        modifier = modifier,
    )
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

@SuppressLint("SetJavaScriptEnabled")
private fun WebSettings.defaultSettings() {
    // https://github.com/home-assistant/android/pull/3353
    minimumFontSize = 5
    javaScriptEnabled = true
    domStorageEnabled = true
    // https://github.com/home-assistant/android/pull/2252
    displayZoomControls = false
    userAgentString += " ${HomeAssistantApis.USER_AGENT_STRING}"
}

@Suppress("DEPRECATION")
private fun WebSettings.setNightModeTheme(nightModeTheme: NightModeTheme, uiMode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
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
