package io.homeassistant.companion.android.themes

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class NightModeManagerTest {

    private val prefsRepository: PrefsRepository = mockk(relaxed = true)

    private lateinit var manager: NightModeManager

    @BeforeEach
    fun setup() {
        manager = NightModeManager(prefsRepository)
    }

    @ParameterizedTest
    @EnumSource(NightModeTheme::class)
    fun `Given theme pref when getting current night mode then pref is returned without saving`(
        theme: NightModeTheme,
    ) = runTest {
        coEvery { prefsRepository.getCurrentNightModeTheme() } returns theme

        assertEquals(theme, manager.getCurrentNightMode())
        coVerify(exactly = 0) { prefsRepository.saveNightModeTheme(any()) }
    }

    @Test
    fun `Given theme pref updates when getting current night mode flow then pref is emitted each time without saving`() = runTest {
        val themeFlow = MutableSharedFlow<NightModeTheme>()
        coEvery { prefsRepository.nightModeThemeFlow() } returns themeFlow

        manager.getCurrentNightModeFlow().test {
            NightModeTheme.entries.forEach { theme ->
                themeFlow.emit(theme)

                assertEquals(theme, awaitItem())
                coVerify(exactly = 0) { prefsRepository.saveNightModeTheme(any()) }
            }
        }
    }

    @ParameterizedTest(name = "sdk={0}, pref={1}")
    @CsvSource(
        "26, light", // <P has no system setting so set to light
        "28, system", // >=P has system setting so set to system
    )
    fun `Given no theme pref when getting current night mode then default pref is set and returned`(
        sdk: Int,
        prefStorageValue: String,
    ) = runTest {
        SdkVersion.sdkInt = sdk
        coEvery { prefsRepository.getCurrentNightModeTheme() } returns null
        val theme = NightModeTheme.fromStorageValue(prefStorageValue)

        assertEquals(theme, manager.getCurrentNightMode())
        coVerify { prefsRepository.saveNightModeTheme(theme!!) }
    }
}
