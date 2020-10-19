package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import javax.inject.Inject
import javax.inject.Named

class PrefsRepositoryImpl @Inject constructor(
    @Named("prefs") private val localStorage: LocalStorage
) : PrefsRepository {

    companion object {
        private const val PREF_THEME = "theme"
        private const val PREF_LANG = "lang"
    }

    override suspend fun getCurrentTheme(): String? {
        return localStorage.getString(PREF_THEME)
    }

    override suspend fun saveTheme(theme: String) {
        localStorage.putString(PREF_THEME, theme)
    }

    override suspend fun getCurrentLang(): String? {
        return localStorage.getString(PREF_LANG)
    }

    override suspend fun saveLang(lang: String) {
        localStorage.putString(PREF_LANG, lang)
    }
}
