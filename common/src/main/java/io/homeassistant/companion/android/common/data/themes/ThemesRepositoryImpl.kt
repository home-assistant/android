package io.homeassistant.companion.android.common.data.themes

import io.homeassistant.companion.android.common.data.LocalStorage
import javax.inject.Inject
import javax.inject.Named

class ThemesRepositoryImpl @Inject constructor(
    @Named("themes") private val localStorage: LocalStorage
) : ThemesRepository {

    companion object {
        private const val PREF_THEME = "theme"
    }

    override suspend fun getCurrentTheme(): String? {
        return localStorage.getString(PREF_THEME)
    }

    override suspend fun saveTheme(theme: String) {
        localStorage.putString(PREF_THEME, theme)
    }
}
