package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.data.LocalStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val CURRENT_VERSION_CODE = 42

/** Key the `info.hannes.changelog` library used; duplicated to guard against accidental renames. */
private const val KEY = "ChangeLog_last_version_code"

class ChangelogRepositoryTest {

    private val localStorage: LocalStorage = mockk(relaxUnitFun = true)
    private val repository = ChangelogRepository(localStorage, CURRENT_VERSION_CODE)

    @Test
    fun `Given no stored version when checking if the app was updated then returns false and stores current version`() = runTest {
        coEvery { localStorage.getInt(KEY) } returns null

        assertFalse(repository.wasAppUpdatedSinceChangelogSeen())

        coVerify { localStorage.putInt(KEY, CURRENT_VERSION_CODE) }
    }

    @Test
    fun `Given older stored version when checking if the app was updated then returns true without storing`() = runTest {
        coEvery { localStorage.getInt(KEY) } returns CURRENT_VERSION_CODE - 1

        assertTrue(repository.wasAppUpdatedSinceChangelogSeen())

        coVerify(exactly = 0) { localStorage.putInt(any(), any()) }
    }

    @Test
    fun `Given current stored version when checking if the app was updated then returns false`() = runTest {
        coEvery { localStorage.getInt(KEY) } returns CURRENT_VERSION_CODE

        assertFalse(repository.wasAppUpdatedSinceChangelogSeen())
    }

    @Test
    fun `Given newer stored version when checking if the app was updated then returns false`() = runTest {
        coEvery { localStorage.getInt(KEY) } returns CURRENT_VERSION_CODE + 1

        assertFalse(repository.wasAppUpdatedSinceChangelogSeen())
    }

    @Test
    fun `When marking changelog seen then stores current version`() = runTest {
        repository.markChangelogSeen()

        coVerify { localStorage.putInt(KEY, CURRENT_VERSION_CODE) }
    }
}
