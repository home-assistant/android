package io.homeassistant.companion.android.settings.language

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class LanguagesManager @Inject constructor(
    private var prefs: PrefsRepository
) {
    companion object {
        private const val DEF_LOCALE = "default"
    }

    fun getAppVersion(): String? {
        return runBlocking {
            prefs.getAppVersion()
        }
    }

    fun saveAppVersion(ver: String) {
        return runBlocking {
            prefs.saveAppVersion(ver)
        }
    }

    fun getCurrentLang(): String {
        return runBlocking {
            val lang = prefs.getCurrentLang()
            if (lang.isNullOrEmpty()) {
                prefs.saveLang(DEF_LOCALE)
                DEF_LOCALE
            } else lang
        }
    }

    fun saveLang(lang: String?) {
        return runBlocking {
            if (!lang.isNullOrEmpty()) {
                val currentLang = getCurrentLang()
                if (currentLang != lang) {
                    prefs.saveLang(lang)
                }
            }
        }
    }

    fun getLocales(): String? {
        return runBlocking {
            prefs.getLocales()
        }
    }

    fun saveLocales(locales: String) {
        return runBlocking {
            prefs.saveLocales(locales)
        }
    }

    private fun makeLocale(lang: String): Locale {
        return if (lang.contains('-')) {
            Locale(lang.split('-')[0], lang.split('-')[1])
        } else {
            Locale(lang)
        }
    }

    private fun getDefaultLocale(config: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            config.locale
        }
    }

    private fun getDeviceLocale(): Locale {
        return getDefaultLocale(Resources.getSystem().configuration)
    }

    private fun getApplicationLocale(context: Context): Locale {
        return getDefaultLocale(context.resources.configuration)
    }

    fun getContextWrapper(context: Context): ContextWrapper {
        return when {
            getCurrentLang() == DEF_LOCALE && getApplicationLocale(context) != getDeviceLocale() -> {
                ContextWrapper(updateContext(context, getDeviceLocale()))
            }
            getCurrentLang() != DEF_LOCALE -> {
                val locale = makeLocale(getCurrentLang())
                ContextWrapper(updateContext(context, locale))
            }
            else -> {
                ContextWrapper(context)
            }
        }
    }

    private fun updateContext(context: Context, locale: Locale): Context {
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            configuration.locale = locale
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(configuration)
        } else {
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        return context
    }
}
