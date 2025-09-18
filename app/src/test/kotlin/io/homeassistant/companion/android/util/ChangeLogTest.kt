package io.homeassistant.companion.android.util

import android.app.AlertDialog
import android.content.Context
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.themes.NightModeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class ChangeLogTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefsRepository = mockk<PrefsRepository>()
    private val nightModeManager = mockk<NightModeManager>()
    private val hannesChangeLog = mockk<info.hannes.changelog.ChangeLog>(relaxed = true)
    private val darkThemeChangeLog = mockk<DarkThemeChangeLog>(relaxed = true)
    private val dialog = mockk<AlertDialog>(relaxed = true)
    private lateinit var changeLog: TestableChangeLog

    @BeforeEach
    fun setup() {
        coEvery { nightModeManager.getCurrentNightMode() } returns NightModeTheme.LIGHT
        every { hannesChangeLog.fullLogDialog } returns dialog
        every { hannesChangeLog.isFirstRun } returns true
        every { hannesChangeLog.isFirstRunEver } returns false
        every { darkThemeChangeLog.fullLogDialog } returns dialog
        every { darkThemeChangeLog.isFirstRun } returns true
        every { darkThemeChangeLog.isFirstRunEver } returns false

        changeLog = TestableChangeLog(nightModeManager, prefsRepository, hannesChangeLog, darkThemeChangeLog)
    }

    @Test
    fun `Given change log popup is not enabled when showing change log then popup is not shown`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false
        changeLog.showChangeLog(context, forceShow = false)
        verify(exactly = 0) { dialog.show() }
    }

    @Test
    fun `Given change log popup is enabled when showing change log then popup is shown`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns true
        changeLog.showChangeLog(context, forceShow = false)
        verify(exactly = 1) { dialog.show() }
    }

    @Test
    fun `Given forceShow is true when showing change log then popup is shown regardless of enabled setting`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false
        changeLog.showChangeLog(context, forceShow = true)
        verify(exactly = 1) { dialog.show() }
    }

    // Test class that extends ChangeLog and overrides the factory methods for testing
    private class TestableChangeLog(
        nightModeManager: NightModeManager,
        prefsRepository: PrefsRepository,
        private val mockChangeLog: info.hannes.changelog.ChangeLog,
        private val mockDarkThemeChangeLog: DarkThemeChangeLog,
    ) : ChangeLog(nightModeManager, prefsRepository) {

        override fun createChangeLog(context: Context): info.hannes.changelog.ChangeLog {
            return mockChangeLog
        }

        override fun createDarkThemeChangeLog(context: Context): DarkThemeChangeLog {
            return mockDarkThemeChangeLog
        }
    }
}
