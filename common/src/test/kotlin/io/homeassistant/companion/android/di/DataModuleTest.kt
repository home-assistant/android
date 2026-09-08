package io.homeassistant.companion.android.di

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Storage constants of the changelog library (com.github.AppDevNext:ChangeLog) previously used by the app
private const val LEGACY_CHANGELOG_PREFS_NAME = "changelog"
private const val LEGACY_CHANGELOG_VERSION_KEY = "ChangeLog_last_version_code"

class DataModuleTest {

    private val sharedPreferences = mockk<SharedPreferences>()
    private val context = mockk<Context> {
        every { getSharedPreferences(LEGACY_CHANGELOG_PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPreferences
    }

    @Test
    fun `Given stored legacy changelog pref when invoking the legacy changelog pref provider then returns true`() = runTest {
        every { sharedPreferences.contains(LEGACY_CHANGELOG_VERSION_KEY) } returns true

        assertTrue(DataModule.provideLegacyChangelogPref(context)())
    }

    @Test
    fun `Given no legacy changelog pref when invoking the legacy changelog pref provider then returns false`() = runTest {
        every { sharedPreferences.contains(LEGACY_CHANGELOG_VERSION_KEY) } returns false

        assertFalse(DataModule.provideLegacyChangelogPref(context)())
    }
}
