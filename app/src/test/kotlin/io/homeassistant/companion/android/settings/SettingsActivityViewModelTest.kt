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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsActivityViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherJUnit5Extension()
    }

    private val serverManager: ServerManager = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private lateinit var viewModel: SettingsActivityViewModel

    @BeforeEach
    fun setup() {
        coEvery { serverManager.getServer(any<Int>()) } returns mockk<Server>(relaxed = true) {
            every { id } returns 1
        }
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        viewModel = SettingsActivityViewModel(serverManager)
    }

    @Test
    fun `setAppActive true calls integrationRepository setAppActive with true`() = runTest {
        coJustRun { integrationRepository.setAppActive(any()) }

        viewModel.setAppActive(1, true)
        advanceUntilIdle()

        coVerify { integrationRepository.setAppActive(true) }
    }

    @Test
    fun `setAppActive false calls integrationRepository setAppActive with false`() = runTest {
        coJustRun { integrationRepository.setAppActive(any()) }

        viewModel.setAppActive(1, false)
        advanceUntilIdle()

        coVerify { integrationRepository.setAppActive(false) }
    }

    @Test
    fun `setAppActive with null serverId uses SERVER_ID_ACTIVE`() = runTest {
        coJustRun { integrationRepository.setAppActive(any()) }

        viewModel.setAppActive(null, true)
        advanceUntilIdle()

        coVerify { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) }
        coVerify { integrationRepository.setAppActive(true) }
    }

    @Test
    fun `setAppActive handles missing server gracefully`() = runTest {
        coEvery { serverManager.getServer(any<Int>()) } returns null

        viewModel.setAppActive(99, true)
        advanceUntilIdle()

        coVerify(exactly = 0) { integrationRepository.setAppActive(any()) }
    }

    @Test
    fun `setAppActive handles IllegalArgumentException gracefully`() = runTest {
        coEvery { serverManager.integrationRepository(any()) } throws IllegalArgumentException("test")

        viewModel.setAppActive(1, true)
        advanceUntilIdle()

        // Should not crash
    }

    @Test
    fun `isAppLocked returns true when app is locked`() = runTest {
        coEvery { integrationRepository.isAppLocked() } returns true

        val result = viewModel.isAppLocked(1)

        assertTrue(result)
    }

    @Test
    fun `isAppLocked returns false when app is not locked`() = runTest {
        coEvery { integrationRepository.isAppLocked() } returns false

        val result = viewModel.isAppLocked(1)

        assertFalse(result)
    }

    @Test
    fun `isAppLocked returns false when server not found`() = runTest {
        coEvery { serverManager.getServer(any<Int>()) } returns null

        val result = viewModel.isAppLocked(99)

        assertFalse(result)
    }

    @Test
    fun `isAppLocked handles IllegalArgumentException gracefully`() = runTest {
        coEvery { serverManager.integrationRepository(any()) } throws IllegalArgumentException("test")

        val result = viewModel.isAppLocked(1)

        assertFalse(result)
    }

    @Test
    fun `isAppLocked with null serverId uses SERVER_ID_ACTIVE`() = runTest {
        coEvery { integrationRepository.isAppLocked() } returns true

        viewModel.isAppLocked(null)

        coVerify { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) }
    }
}
