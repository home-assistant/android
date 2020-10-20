package io.homeassistant.companion.android.settings.language

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.DisplayMetrics
import io.homeassistant.companion.android.R
import io.sentry.core.util.StringUtils
import java.util.Locale

class LanguagesProvider {

    fun getSupportedLanguages(context: Context): Map<String, String> {
        val listAppLocales = mutableMapOf<String, String>()

        val resources = context.resources
        val configuration = resources.configuration
        val listLocales = resources.assets.locales

        listAppLocales[resources.getString(R.string.lang_option_label_default)] = resources.getString(R.string.lang_option_value_default)
        val defString = getStringResource(context, "")

        listLocales.forEach {
            if (getStringResource(context, it) != defString || it == Locale.ENGLISH.language) {
                listAppLocales[StringUtils.capitalize(getDefaultLocale(configuration).displayLanguage).toString()] = it
            }
        }
        return listAppLocales
    }

    private fun getStringResource(context: Context, lang: String): String {
        val resource = R.string.application_version
        val resources = context.resources
        val configuration = resources.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            configuration.setLocales(LocaleList(Locale(lang)))
            val c = context.createConfigurationContext(configuration)
            c.getString(resource)
        } else {
            val metrics = DisplayMetrics()
            configuration.locale = Locale(lang)
            val res = Resources(context.assets, metrics, configuration)
            res.getString(resource)
        }
    }

    private fun getDefaultLocale(config: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            config.locale
        }
    }
}
