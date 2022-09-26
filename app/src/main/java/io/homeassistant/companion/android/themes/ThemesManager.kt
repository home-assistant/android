package io.homeassistant.companion.android.themes

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
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

    fun setThemeForWebView() {
        val theme = getCurrentTheme()
        setNightModeBasedOnTheme(theme)
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
