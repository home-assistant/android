package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class ChangelogViewModelTest {

    private val changelogRepository: ChangelogRepository = mockk(relaxUnitFun = true)

    private fun createViewModel(isAutomotive: Boolean = false, rawVersionName: String = "2026.7.6-full") = ChangelogViewModel(changelogRepository, isAutomotive, rawVersionName)

    @Test
    fun `Given creation when initializing then marks changelog seen`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify { changelogRepository.markChangelogSeen() }
    }

    @Test
    fun `Given full flavor version when reading state then flavor suffix is stripped and release url built`() {
        val state = createViewModel(rawVersionName = "2026.7.6-full").uiState.value

        assertEquals("2026.7.6", state.versionName)
        assertEquals("https://github.com/home-assistant/android/releases/tag/2026.7.6", state.releaseUrl)
    }

    @Test
    fun `Given minimal flavor version when reading state then flavor suffix is stripped`() {
        assertEquals("2026.7.6", createViewModel(rawVersionName = "2026.7.6-minimal").uiState.value.versionName)
    }

    @Test
    fun `Given phone app when reading state then current platform is app with the authored content`() {
        val state = createViewModel(isAutomotive = false).uiState.value

        assertEquals(ChangelogPlatform.APP, state.currentPlatform)
        assertEquals(currentChangelog.toSections(), state.sections)
    }

    @Test
    fun `Given automotive when reading state then current platform is automotive`() {
        assertEquals(ChangelogPlatform.AUTOMOTIVE, createViewModel(isAutomotive = true).uiState.value.currentPlatform)
    }
}
