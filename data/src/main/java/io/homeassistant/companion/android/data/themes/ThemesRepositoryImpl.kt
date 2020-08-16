package io.homeassistant.companion.android.data.themes

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.themes.ThemesRepository
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
