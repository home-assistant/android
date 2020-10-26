package io.homeassistant.companion.android.common.data.prefs

interface PrefsRepository {
    suspend fun getAppVersion(): String?

    suspend fun saveAppVersion(lang: String)

    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String)

    suspend fun getCurrentLang(): String?

    suspend fun saveLang(lang: String)

    suspend fun getLocales(): String?

    suspend fun saveLocales(lang: String)
}
