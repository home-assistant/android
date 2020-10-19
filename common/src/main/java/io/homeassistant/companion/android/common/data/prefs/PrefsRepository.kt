package io.homeassistant.companion.android.common.data.prefs

interface PrefsRepository {
    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String)

    suspend fun getCurrentLang(): String?

    suspend fun saveLang(lang: String)
}
