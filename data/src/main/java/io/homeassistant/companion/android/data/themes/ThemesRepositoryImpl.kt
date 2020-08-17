package io.homeassistant.companion.android.data.themes

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.themes.ThemesRepository
import javax.inject.Inject
import javax.inject.Named

class ThemesRepositoryImpl @Inject constructor(
    @Named("themes") private val localStorage: LocalStorage,
    @Named("osVersion") private val osVersion: String
) : ThemesRepository {

    companion object {
        private const val PREF_THEME = "theme"
        private const val P = 28
    }

    override suspend fun getCurrentTheme(): String {
        val theme = localStorage.getString(PREF_THEME)
        return if(theme.isNullOrEmpty()) {
            if (osVersion.toInt() >= P) {
                "system"
            } else "light"
        } else theme
    }

    override suspend fun saveTheme(theme: String) {
        localStorage.putString(PREF_THEME, theme)
    }
}
