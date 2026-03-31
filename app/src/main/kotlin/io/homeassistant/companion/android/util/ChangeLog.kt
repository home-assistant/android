package io.homeassistant.companion.android.util

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.annotation.VisibleForTesting
import info.hannes.changelog.ChangeLog
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.themes.NightModeManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class ChangeLog @Inject constructor(
    val nightModeManager: NightModeManager,
    private val prefsRepository: PrefsRepository,
) {
    @VisibleForTesting
    internal open fun createChangeLog(context: Context): ChangeLog {
        return ChangeLog(context)
    }

    @VisibleForTesting
    internal open fun createDarkThemeChangeLog(context: Context): DarkThemeChangeLog {
        return DarkThemeChangeLog(context)
    }

    suspend fun showChangeLog(context: Context, forceShow: Boolean) {
        // Check if user has enabled change log popup or this is a forced show
        if (!forceShow && !prefsRepository.isChangeLogPopupEnabled()) {
            return
        }

        val isDarkTheme = when (nightModeManager.getCurrentNightMode()) {
            NightModeTheme.ANDROID, NightModeTheme.SYSTEM -> {
                val nightModeFlags =
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            NightModeTheme.DARK -> true
            else -> false
        }

        // Ensure UI operations happen on Main thread
        withContext(Dispatchers.Main) {
            val changeLog = if (isDarkTheme) {
                createDarkThemeChangeLog(context)
            } else {
                createChangeLog(context)
            }
            if ((!changeLog.isFirstRunEver && changeLog.isFirstRun) || forceShow) {
                changeLog.fullLogDialog.show()
            }
        }
    }
}

class DarkThemeChangeLog internal constructor(context: Context) :
    ChangeLog(ContextThemeWrapper(context, R.style.Theme_HomeAssistant_PopupTheme), DARK_THEME_CSS) {
    companion object {
        internal const val DARK_THEME_CSS = "body { color: #ffffff; background-color: #282828; }\n$DEFAULT_CSS"
    }
}
