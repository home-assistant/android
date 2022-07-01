package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import javax.inject.Inject
import javax.inject.Named

class PrefsRepositoryImpl @Inject constructor(
    @Named("themes") private val localStorage: LocalStorage
) : PrefsRepository {

    companion object {
        private const val PREF_VER = "version"
        private const val PREF_THEME = "theme"
        private const val PREF_LANG = "lang"
        private const val PREF_LOCALES = "locales"
        private const val PREF_KEY_ALIAS = "key-alias"
        private const val PREF_CRASH_REPORTING_DISABLED = "crash_reporting"
    }

    override suspend fun getAppVersion(): String? {
        return localStorage.getString(PREF_VER)
    }

    override suspend fun saveAppVersion(ver: String) {
        localStorage.putString(PREF_VER, ver)
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

    override suspend fun getLocales(): String? {
        return localStorage.getString(PREF_LOCALES)
    }

    override suspend fun saveLocales(locales: String) {
        localStorage.putString(PREF_LOCALES, locales)
    }

    override suspend fun isCrashReporting(): Boolean {
        return !localStorage.getBoolean(PREF_CRASH_REPORTING_DISABLED)
    }

    override suspend fun setCrashReporting(crashReportingEnabled: Boolean) {
        localStorage.putBoolean(PREF_CRASH_REPORTING_DISABLED, !crashReportingEnabled)
    }

    override suspend fun saveKeyAlias(alias: String) {
        localStorage.putString(PREF_KEY_ALIAS, alias)
    }

    override suspend fun getKeyAlias(): String? {
        return localStorage.getString(PREF_KEY_ALIAS)
    }
}
