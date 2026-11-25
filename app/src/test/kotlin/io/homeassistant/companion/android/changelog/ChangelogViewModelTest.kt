package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

private const val CURRENT_VERSION_CODE = 42

@ExtendWith(MainDispatcherJUnit5Extension::class)
class ChangelogViewModelTest {

    private val prefsRepository: PrefsRepository = mockk(relaxUnitFun = true)

    private fun createViewModel(
        isAutomotive: Boolean = false,
        appVersion: AppVersion = AppVersion("2026.7.6-${BuildConfig.FLAVOR}", CURRENT_VERSION_CODE),
        homeAssistantModeSupported: Boolean = true,
        changelog: Changelog = currentChangelog,
    ) = ChangelogViewModel(
        prefsRepository,
        isAutomotive,
        appVersion,
        mockk<HomeAssistantModeManager> { every { isSupported() } returns homeAssistantModeSupported },
        changelog,
    )

    @Test
    fun `Given creation when initializing then marks changelog seen with the version code`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify { prefsRepository.markChangelogSeen(CURRENT_VERSION_CODE) }
    }

    @Test
    fun `Given current flavor version when reading state then flavor suffix is stripped and release url built`() {
        val state = createViewModel().uiState.value

        assertEquals("2026.7.6", state.versionName)
        assertEquals("https://github.com/home-assistant/android/releases/tag/2026.7.6", state.releaseUrl)
    }

    @Test
    fun `Given version without flavor suffix when reading state then version name is unchanged`() {
        val state = createViewModel(appVersion = AppVersion("2026.7.6", CURRENT_VERSION_CODE)).uiState.value

        assertEquals("2026.7.6", state.versionName)
    }

    @ParameterizedTest
    @EnumSource(ChangelogPlatform::class, names = ["APP", "AUTOMOTIVE"])
    fun `Given specific platform when reading state then current platform is the specific platform with the authored content`(platform: ChangelogPlatform) {
        val state = createViewModel(isAutomotive = platform == ChangelogPlatform.AUTOMOTIVE).uiState.value

        assertEquals(platform, state.currentPlatform)
        assertEquals(currentChangelog.toSections(), state.sections)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given Modes support when reading state then only the link to the mode setting depends on it`(supported: Boolean) {
        val modeEntry = ChangelogEntry(
            contentRes = commonR.string.changelog_entry_home_assistant_mode,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.HomeAssistantMode),
        )
        val otherEntry = ChangelogEntry(
            contentRes = commonR.string.changelog_entry_edge_gestures,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.Gestures),
        )

        val state = createViewModel(
            homeAssistantModeSupported = supported,
            changelog = Changelog(new = listOf(modeEntry, otherEntry)),
        ).uiState.value

        val shownModeEntry = if (supported) modeEntry else modeEntry.copy(action = null)
        assertEquals(listOf(ChangelogSection(ChangelogCategory.NEW, listOf(shownModeEntry, otherEntry))), state.sections)
    }
}
