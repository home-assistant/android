package io.homeassistant.companion.android.themes

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatDelegate
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ThemesManager @Inject constructor(
    private val themesUseCase: PrefsRepository
) {

    fun getCurrentTheme(): String {
        return runBlocking {
            val theme = themesUseCase.getCurrentTheme()
            if (theme.isNullOrEmpty()) {
                val toSetTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    "system"
                } else "light"
                themesUseCase.saveTheme(toSetTheme)
                toSetTheme
            } else theme
        }
    }

    fun saveTheme(theme: String?) {
        return runBlocking {
            if (!theme.isNullOrEmpty()) {
                val currentTheme = getCurrentTheme()
                if (currentTheme != theme) {
                    themesUseCase.saveTheme(theme)
                    setNightModeBasedOnTheme(theme)
                }
            }
        }
    }

    fun setThemeForWebView(context: Context, webSettings: WebSettings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
        ) {
            val theme = getCurrentTheme()

            setNightModeBasedOnTheme(theme)

            WebSettingsCompat.setForceDarkStrategy(
                webSettings,
                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
            )
            when (theme) {
                "dark" -> {
                    WebSettingsCompat.setForceDark(
                        webSettings,
                        WebSettingsCompat.FORCE_DARK_ON
                    )
                }
                "android", "system" -> {
                    val nightModeFlags =
                        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        WebSettingsCompat.setForceDark(
                            webSettings,
                            WebSettingsCompat.FORCE_DARK_ON
                        )
                    } else {
                        WebSettingsCompat.setForceDark(
                            webSettings,
                            WebSettingsCompat.FORCE_DARK_OFF
                        )
                    }
                }
                else -> {
                    WebSettingsCompat.setForceDark(
                        webSettings,
                        WebSettingsCompat.FORCE_DARK_OFF
                    )
                }
            }
        }
    }

    private fun setNightModeBasedOnTheme(theme: String?) {
        when (theme) {
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            "android", "system" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}
