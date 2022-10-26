package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefsRepositoryTest {

    class LocalStorageMock : LocalStorage {
        private var version: String? = null
        private var theme: String? = null
        private var lang: String? = null
        private var locales: String? = null
        private var keyAlias: String? = null
        private var crashReporting: Boolean = false

        override suspend fun putLong(key: String, value: Long?) = Unit
        override suspend fun getLong(key: String) = null
        override suspend fun putInt(key: String, value: Int?) = Unit
        override suspend fun getInt(key: String) = null
        override suspend fun putStringSet(key: String, value: Set<String>) = Unit
        override suspend fun getStringSet(key: String) = null
        override suspend fun remove(key: String) = Unit

        override suspend fun putString(key: String, value: String?) {
            when (key) {
                "version" -> version = value
                "theme" -> theme = value
                "lang" -> lang = value
                "locales" -> locales = value
                "key-alias" -> keyAlias = value
            }
        }

        override suspend fun getString(key: String): String? = when (key) {
            "version" -> version
            "theme" -> theme
            "lang" -> lang
            "locales" -> locales
            "key-alias" -> keyAlias
            else -> null
        }

        override suspend fun putBoolean(key: String, value: Boolean) {
            when (key) {
                "crash_reporting" -> crashReporting = value
            }
        }

        override suspend fun getBoolean(key: String) = when (key) {
            "crash_reporting" -> crashReporting
            else -> false
        }
    }

    private lateinit var prefsRepository: PrefsRepository

    @Before
    fun setup() {
        prefsRepository = PrefsRepositoryImpl(LocalStorageMock())
    }

    @Test
    fun appVersionTest() = runTest {
        assertNull(prefsRepository.getAppVersion())

        prefsRepository.saveAppVersion("TEST-full")
        assertEquals("TEST-full", prefsRepository.getAppVersion())
    }

    @Test
    fun themeTest() = runTest {
        assertNull(prefsRepository.getCurrentTheme())

        prefsRepository.saveTheme("dark")
        assertEquals("dark", prefsRepository.getCurrentTheme())
    }

    @Test
    fun langTest() = runTest {
        assertNull(prefsRepository.getCurrentLang())

        prefsRepository.saveLang("en-US")
        assertEquals("en-US", prefsRepository.getCurrentLang())
    }

    @Test
    fun localesTest() = runTest {
        assertNull(prefsRepository.getLocales())

        prefsRepository.saveLocales("en,es")
        assertEquals("en,es", prefsRepository.getLocales())
    }

    @Test
    fun crashReportingTest() = runTest {
        assertTrue(prefsRepository.isCrashReporting())

        prefsRepository.setCrashReporting(true)
        assertTrue(prefsRepository.isCrashReporting())

        prefsRepository.setCrashReporting(false)
        assertFalse(prefsRepository.isCrashReporting())
    }

    @Test
    fun keyAliasTest() = runTest {
        assertNull(prefsRepository.getKeyAlias())

        prefsRepository.saveKeyAlias("key")
        assertEquals("key", prefsRepository.getKeyAlias())
    }
}
