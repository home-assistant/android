package io.homeassistant.companion.android.launch.applock

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class AppLockManagerTest {

    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)

    private val appLockManager = AppLockManager(serverManager)

    @Test
    fun `Given server not registered when checking lock then return false`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        assertFalse(appLockManager.isAppLocked())
    }

    @Test
    fun `Given server registered and app locked when checking lock then return true`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } returns integrationRepository
        coEvery { integrationRepository.isAppLocked() } returns true

        assertTrue(appLockManager.isAppLocked())
    }

    @Test
    fun `Given server registered and app not locked when checking lock then return false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } returns integrationRepository
        coEvery { integrationRepository.isAppLocked() } returns false

        assertFalse(appLockManager.isAppLocked())
    }

    @Test
    fun `Given exception when checking lock then return false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } throws IllegalStateException("no server")

        assertFalse(appLockManager.isAppLocked())
    }

    @Test
    fun `Given CancellationException when checking lock then propagate`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } throws CancellationException("cancelled")

        val result = runCatching { appLockManager.isAppLocked() }
        assertTrue(result.isFailure)
        assertInstanceOf(CancellationException::class.java, result.exceptionOrNull())
    }

    @Test
    fun `Given server registered when setting app active then delegate to integration repository`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } returns integrationRepository

        appLockManager.setAppActive(active = true)

        coVerify { integrationRepository.setAppActive(true) }
    }

    @Test
    fun `Given server registered when setting app inactive then delegate to integration repository`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } returns integrationRepository

        appLockManager.setAppActive(active = false)

        coVerify { integrationRepository.setAppActive(false) }
    }

    @Test
    fun `Given server not registered when setting app active then do not call integration repository`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        appLockManager.setAppActive(active = true)

        coVerify(exactly = 0) { serverManager.integrationRepository() }
    }

    @Test
    fun `Given exception when setting app active then do not throw`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } throws IllegalStateException("no server")

        appLockManager.setAppActive(active = false)
    }

    @Test
    fun `Given CancellationException when setting app active then propagate`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository() } throws CancellationException("cancelled")

        val result = runCatching { appLockManager.setAppActive(active = true) }
        assertTrue(result.isFailure)
        assertInstanceOf(CancellationException::class.java, result.exceptionOrNull())
    }
}
