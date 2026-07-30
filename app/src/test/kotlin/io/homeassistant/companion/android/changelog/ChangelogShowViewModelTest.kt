package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChangelogShowViewModelTest {

    private val changelogRepository: ChangelogRepository = mockk()
    private val prefsRepository: PrefsRepository = mockk()
    private val viewModel = ChangelogShowViewModel(changelogRepository, prefsRepository)

    @Test
    fun `Given enabled popup and unseen changelog when consuming then returns true only once`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns true
        coEvery { changelogRepository.wasAppUpdatedSinceChangelogSeen() } returns true

        assertTrue(viewModel.consumeShouldShowChangelog())
        assertFalse(viewModel.consumeShouldShowChangelog())

        coVerify(exactly = 1) { changelogRepository.wasAppUpdatedSinceChangelogSeen() }
    }

    @Test
    fun `Given disabled popup when consuming then returns false without checking if the app was updated`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false

        assertFalse(viewModel.consumeShouldShowChangelog())

        // wasAppUpdatedSinceChangelogSeen marks a fresh install as seen, the short-circuit must prevent that
        coVerify(exactly = 0) { changelogRepository.wasAppUpdatedSinceChangelogSeen() }
    }

    @Test
    fun `Given seen changelog when consuming then returns false`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns true
        coEvery { changelogRepository.wasAppUpdatedSinceChangelogSeen() } returns false

        assertFalse(viewModel.consumeShouldShowChangelog())
    }

    @Test
    fun `Given consumed decision when consuming again then repositories are not queried again`() = runTest {
        coEvery { prefsRepository.isChangeLogPopupEnabled() } returns false

        assertFalse(viewModel.consumeShouldShowChangelog())
        assertFalse(viewModel.consumeShouldShowChangelog())

        coVerify(exactly = 1) { prefsRepository.isChangeLogPopupEnabled() }
    }
}
