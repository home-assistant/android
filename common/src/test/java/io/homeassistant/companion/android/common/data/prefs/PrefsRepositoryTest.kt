package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorageMock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrefsRepositoryTest {

    private lateinit var prefsRepository: PrefsRepository

    @Before
    fun setup() {
        prefsRepository = PrefsRepositoryImpl(
            localStorage = LocalStorageMock(),
            integrationStorage = LocalStorageMock()
        )
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
