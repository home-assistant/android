package io.homeassistant.companion.android.util

import android.app.AlertDialog
import android.content.Context
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.themes.NightModeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChangeLogTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefsRepository = mockk<PrefsRepository>()
    private val nightModeManager = mockk<NightModeManager>()
    private val hannesChangeLog = mockk<info.hannes.changelog.ChangeLog>(relaxed = true)
    private val dialog = mockk<AlertDialog>(relaxed = true)

    @BeforeEach
    fun setup() {
        coEvery { nightModeManager.getCurrentNightMode() } returns io.homeassistant.companion.android.common.data.prefs.NightModeTheme.LIGHT
        every { hannesChangeLog.fullLogDialog } returns dialog
        every { hannesChangeLog.isFirstRun } returns true
        every { hannesChangeLog.isFirstRunEver } returns false
    }

    @Test
    fun `Given change log popup is not enabled when showing change log then popup is not shown`() {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false
        val changeLog = spyk(ChangeLog(nightModeManager, prefsRepository), recordPrivateCalls = true)
        every { changeLog["createChangeLog"](any<Context>()) } returns hannesChangeLog
        changeLog.showChangeLog(context, forceShow = false)
        verify(exactly = 0) { dialog.show() }
    }

    @Test
    fun `Given change log popup is enabled when showing change log then popup is shown`() {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns true
        val changeLog = spyk(ChangeLog(nightModeManager, prefsRepository), recordPrivateCalls = true)
        every { changeLog["createChangeLog"](any<Context>()) } returns hannesChangeLog
        changeLog.showChangeLog(context, forceShow = false)
        verify(exactly = 1) { dialog.show() }
    }

    @Test
    fun `Given forceShow is true when showing change log then popup is shown regardless of enabled setting`() {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false
        val changeLog = spyk(ChangeLog(nightModeManager, prefsRepository), recordPrivateCalls = true)
        every { changeLog["createChangeLog"](any<Context>()) } returns hannesChangeLog
        changeLog.showChangeLog(context, forceShow = true)
        verify(exactly = 1) { dialog.show() }
    }
}
