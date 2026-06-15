package io.homeassistant.companion.android.settings

import io.homeassistant.companion.android.applock.AppLockStateManager
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class SettingsViewModelTest {

    private val appLockStateManager: AppLockStateManager = mockk()
    private lateinit var viewModel: SettingsViewModel

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given setAppActive when invoked then delegates to manager in viewModelScope`(
        active: Boolean,
    ) = runTest {
        coJustRun { appLockStateManager.setAppActive(any(), any()) }
        viewModel = SettingsViewModel(appLockStateManager)

        viewModel.setAppActive(serverId = 1, active = active)
        advanceUntilIdle()

        coVerify { appLockStateManager.setAppActive(serverId = 1, active = active) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given isAppLocked when invoked then returns the manager's result`(
        locked: Boolean,
    ) = runTest {
        coEvery { appLockStateManager.isAppLocked(any()) } returns locked
        viewModel = SettingsViewModel(appLockStateManager)

        val result = viewModel.isAppLocked(serverId = 1)

        if (locked) assertTrue(result) else assertFalse(result)
        coVerify { appLockStateManager.isAppLocked(serverId = 1) }
    }
}
