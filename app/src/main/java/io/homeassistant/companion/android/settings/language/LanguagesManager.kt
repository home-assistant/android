package io.homeassistant.companion.android.settings.language

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import kotlinx.coroutines.runBlocking
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class LanguagesManager @Inject constructor(
    private var prefs: PrefsRepository
) {
    companion object {
        private const val TAG = "LanguagesManager"

        const val DEF_LOCALE = "default"
        private const val SYSTEM_MANAGES_LOCALE = "system_managed"
    }

    fun getCurrentLang(): String {
        return runBlocking {
            val lang = prefs.getCurrentLang()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                migrateLangSetting()
                AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { DEF_LOCALE }
            } else {
                if (lang.isNullOrEmpty()) {
                    prefs.saveLang(DEF_LOCALE)
                    DEF_LOCALE
                } else {
                    lang
                }
            }
        }
    }

    fun saveLang(lang: String?) {
        return runBlocking {
            if (!lang.isNullOrEmpty()) {
                val currentLang = getCurrentLang()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val languages =
                        if (lang == DEF_LOCALE) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(lang)
                        }
                    AppCompatDelegate.setApplicationLocales(languages) // Applying will also save it
                } else if (currentLang != lang) {
                    prefs.saveLang(lang)
                    applyCurrentLang()
                }
            }
        }
    }

    fun applyCurrentLang() = runBlocking {
        migrateLangSetting()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            val lang = getCurrentLang()
            val languages =
                if (lang == DEF_LOCALE) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(lang)
                }
            AppCompatDelegate.setApplicationLocales(languages)
        } // else on Android 13+ the system will manage the app's language
    }

    private suspend fun migrateLangSetting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val lang = prefs.getCurrentLang()
        if (lang == SYSTEM_MANAGES_LOCALE) return

        // First run on Android 13: save in AndroidX, update app preference
        val languages =
            if (lang == DEF_LOCALE) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(lang)
            }
        AppCompatDelegate.setApplicationLocales(languages)
        prefs.saveLang(SYSTEM_MANAGES_LOCALE)
    }

    fun getLocaleTags(context: Context): List<String> {
        return runBlocking {
            val languagesList = mutableListOf<String>()
            try {
                context.resources.getXml(commonR.xml.locales_config).use {
                    var tagType = it.eventType
                    while (tagType != XmlPullParser.END_DOCUMENT) {
                        if (tagType == XmlPullParser.START_TAG && it.name == "locale") {
                            languagesList += it.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        }
                        tagType = it.next()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while parsing locale config XML", e)
            }

            languagesList
        }
    }
}
