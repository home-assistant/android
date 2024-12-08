package io.shpro.companion.android.settings.language

import android.content.Context
import io.shpro.companion.android.common.R as commonR
import io.shpro.companion.android.common.util.capitalize
import java.util.Locale
import javax.inject.Inject

class LanguagesProvider @Inject constructor(
    private var langManager: LanguagesManager
) {

    fun getSupportedLanguages(context: Context): Map<String, String> {
        val listAppLocales = sortedMapOf<String, String>()
        val resources = context.resources

        val locales = langManager.getLocaleTags(context)
        locales.forEach {
            val locale = makeLocale(it)
            var display = locale.getDisplayLanguage(locale).capitalize(Locale.getDefault())
            if (locale.country.isNotBlank()) display += " (${locale.getDisplayCountry(locale)})"
            listAppLocales[display] = it
        }

        val languages = mutableMapOf(resources.getString(commonR.string.lang_option_label_default) to LanguagesManager.DEF_LOCALE)
        languages.putAll(listAppLocales)
        return languages
    }

    private fun makeLocale(lang: String): Locale {
        return if (lang.contains('-')) {
            Locale(lang.split('-')[0], lang.split('-')[1])
        } else {
            Locale(lang)
        }
    }
}
