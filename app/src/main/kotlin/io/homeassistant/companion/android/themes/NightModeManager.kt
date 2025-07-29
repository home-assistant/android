package io.homeassistant.companion.android.themes

import android.os.Build
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import javax.inject.Inject
import timber.log.Timber

class NightModeManager @Inject constructor(private val prefsRepository: PrefsRepository) {

    suspend fun getCurrentNightMode(): NightModeTheme {
        val nightMode = prefsRepository.getCurrentNightModeTheme()
        return if (nightMode == null) {
            val nightModeThemeToSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                NightModeTheme.SYSTEM
            } else {
                NightModeTheme.LIGHT
            }
            prefsRepository.saveNightModeTheme(nightModeThemeToSet)
            nightModeThemeToSet
        } else {
            nightMode
        }
    }

    suspend fun saveNightMode(nightModeTheme: NightModeTheme?) {
        if (nightModeTheme !== null) {
            val currentNightMode = getCurrentNightMode()
            if (currentNightMode != nightModeTheme) {
                prefsRepository.saveNightModeTheme(nightModeTheme)
                nightModeTheme.setAsDefaultNightMode()
            }
        } else {
            Timber.i("Skipping saving night mode theme since nightModeTheme is null")
        }
    }
}
