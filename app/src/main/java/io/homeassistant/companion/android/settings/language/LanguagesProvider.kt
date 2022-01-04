package io.homeassistant.companion.android.settings.language

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.DisplayMetrics
import io.homeassistant.companion.android.BuildConfig
import java.util.Locale
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class LanguagesProvider @Inject constructor(
    private var langManager: LanguagesManager
) {

    fun getSupportedLanguages(context: Context): Map<String, String> {
        val listAppLocales = sortedMapOf<String, String>()
        val resources = context.resources

        if (langManager.getAppVersion() != BuildConfig.VERSION_NAME || langManager.getLocales().isNullOrEmpty()) {
            val listLocales = resources.assets.locales
            val defString = getStringResource(context, "")
            var supportedLocales = ""

            listLocales.forEach {
                if (getStringResource(context, it) != defString || it == Locale.ENGLISH.language) {
                    val name = makeLocale(it).displayLanguage.capitalize()
                    listAppLocales["$name ($it)"] = it
                    supportedLocales += "$it,"
                }
            }
            langManager.saveAppVersion(BuildConfig.VERSION_NAME)
            langManager.saveLocales(supportedLocales)
        } else {
            val listLocales = langManager.getLocales()!!.split(',')
            listLocales.forEach {
                if (it.isNotEmpty()) {
                    val name = makeLocale(it).displayLanguage.capitalize()
                    listAppLocales["$name ($it)"] = it
                }
            }
        }

        val languages = mutableMapOf(resources.getString(commonR.string.lang_option_label_default) to resources.getString(commonR.string.lang_option_value_default))
        languages.putAll(listAppLocales)
        return languages
    }

    private fun getStringResource(context: Context, lang: String): String {
        val resource = commonR.string.application_version
        val resources = context.resources
        val configuration = resources.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            configuration.setLocales(LocaleList(makeLocale(lang)))
            val c = context.createConfigurationContext(configuration)
            c.getString(resource)
        } else {
            val metrics = DisplayMetrics()
            configuration.locale = makeLocale(lang)
            val res = Resources(context.assets, metrics, configuration)
            res.getString(resource)
        }
    }

    private fun makeLocale(lang: String): Locale {
        return if (lang.contains('-')) {
            Locale(lang.split('-')[0], lang.split('-')[1])
        } else {
            Locale(lang)
        }
    }
}
