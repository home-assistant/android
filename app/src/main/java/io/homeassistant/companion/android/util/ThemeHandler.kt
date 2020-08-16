package io.homeassistant.companion.android.util

import android.content.Context
import android.content.res.Configuration
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

object ThemeHandler {
    fun setNightModeBaseOnTheme(theme: String?) {
        when (theme) {
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            "system" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
    fun setTheme(context: Context, webView: WebView?, theme: String?) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            setNightModeBaseOnTheme(theme)
            webView?.let {
                WebSettingsCompat.setForceDarkStrategy(
                    webView.settings,
                    WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                )
                when (theme) {
                    "newTheme" ->
                    {
                        // Just a template for custom themes
                        // context.setTheme(android.R.style.newTheme);
                    }
                    "dark" -> {
                        WebSettingsCompat.setForceDark(
                            webView.settings,
                            WebSettingsCompat.FORCE_DARK_ON
                        )
                    }
                    "system" -> {
                        val nightModeFlags =
                            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                            WebSettingsCompat.setForceDark(
                                webView.settings,
                                WebSettingsCompat.FORCE_DARK_ON
                            )
                        } else {
                            WebSettingsCompat.setForceDark(
                                webView.settings,
                                WebSettingsCompat.FORCE_DARK_OFF
                            )
                        }
                    }
                    else -> {
                        WebSettingsCompat.setForceDark(
                            webView.settings,
                            WebSettingsCompat.FORCE_DARK_OFF
                        )
                    }
                }
            }
        }
    }
}
