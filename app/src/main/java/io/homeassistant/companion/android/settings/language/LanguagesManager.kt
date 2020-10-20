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

    private fun getDefaultLocale(config: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            config.locale
        }
    }

    private fun getDeviceLanguage(): String {
        return getDefaultLocale(Resources.getSystem().configuration).language
    }

    private fun getApplicationLanguage(context: Context): String {
        return getDefaultLocale(context.resources.configuration).language
    }

    fun getContextWrapper(context: Context): ContextWrapper {
        return when {
            getCurrentLang() == DEF_LOCALE && getApplicationLanguage(context) != getDeviceLanguage() -> {
                ContextWrapper(updateContext(context, getDeviceLanguage()))
            }
            getCurrentLang() != DEF_LOCALE && getCurrentLang() != getDeviceLanguage() -> {
                ContextWrapper(updateContext(context, getCurrentLang()))
            }
            else -> {
                ContextWrapper(context)
            }
        }
    }

    private fun updateContext(context: Context, lang: String): Context {
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(Locale(lang))
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            configuration.locale = Locale(lang)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(configuration)
        } else {
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        return context
    }
}
