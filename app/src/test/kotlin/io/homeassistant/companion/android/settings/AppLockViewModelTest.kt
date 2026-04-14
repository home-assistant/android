package io.homeassistant.companion.android.settings

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class AppLockViewModelTest {

    private val serverManager: ServerManager = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private lateinit var viewModel: AppLockViewModel

    @BeforeEach
    fun setup() {
        coEvery { serverManager.getServer(any<Int>()) } returns mockk<Server>(relaxed = true) {
            every { id } returns 1
        }
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        viewModel = AppLockViewModel(serverManager)
    }

    @Nested
    inner class SetAppActive {

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `Given a valid server when setting app active then calls integration repository`(
            active: Boolean,
        ) = runTest {
            coJustRun { integrationRepository.setAppActive(any()) }

            viewModel.setAppActive(1, active)
            advanceUntilIdle()

            coVerify { integrationRepository.setAppActive(active) }
        }

        @Test
        fun `Given a null server ID when setting app active then uses SERVER_ID_ACTIVE`() = runTest {
            coJustRun { integrationRepository.setAppActive(any()) }

            viewModel.setAppActive(null, true)
            advanceUntilIdle()

            coVerify { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) }
            coVerify { integrationRepository.setAppActive(true) }
        }

        @Test
        fun `Given a missing server when setting app active then does not call integration repository`() = runTest {
            coEvery { serverManager.getServer(any<Int>()) } returns null

            viewModel.setAppActive(99, true)
            advanceUntilIdle()

            coVerify(exactly = 0) { integrationRepository.setAppActive(any()) }
        }

        @Test
        fun `Given an invalid server when setting app active then handles exception gracefully`() = runTest {
            coEvery { serverManager.integrationRepository(any()) } throws IllegalArgumentException("test")

            viewModel.setAppActive(1, true)
            advanceUntilIdle()
        }
    }

    @Nested
    inner class IsAppLocked {

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `Given a valid server when checking app lock then returns lock state`(
            locked: Boolean,
        ) = runTest {
            coEvery { integrationRepository.isAppLocked() } returns locked

            val result = viewModel.isAppLocked(1)

            if (locked) assertTrue(result) else assertFalse(result)
        }

        @Test
        fun `Given a null server ID when checking app lock then uses SERVER_ID_ACTIVE`() = runTest {
            coEvery { integrationRepository.isAppLocked() } returns true

            viewModel.isAppLocked(null)

            coVerify { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) }
        }

        @Test
        fun `Given a missing server when checking app lock then returns false`() = runTest {
            coEvery { serverManager.getServer(any<Int>()) } returns null

            val result = viewModel.isAppLocked(99)

            assertFalse(result)
        }

        @Test
        fun `Given an invalid server when checking app lock then returns false`() = runTest {
            coEvery { serverManager.integrationRepository(any()) } throws IllegalArgumentException("test")

            val result = viewModel.isAppLocked(1)

            assertFalse(result)
        }
    }
}
