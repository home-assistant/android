package io.homeassistant.companion.android.common.push

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PushProviderManagerTest {

    private lateinit var serverManager: ServerManager
    private lateinit var integrationRepository: IntegrationRepository

    @Before
    fun setUp() {
        serverManager = mockk(relaxed = true)
        integrationRepository = mockk(relaxed = true)
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.updateRegistration(any(), any()) } just Runs
    }

    private fun createProvider(
        name: String,
        available: Boolean = true,
        active: Boolean = false,
        registrationResult: PushRegistrationResult? = null,
    ): PushProvider = object : PushProvider {
        override val name = name
        override suspend fun isAvailable() = available
        override suspend fun isActive() = active
        override suspend fun register() = registrationResult
        override suspend fun unregister() {}
    }

    @Test
    fun `getActiveProvider returns the active provider`() = runTest {
        val provider1 = createProvider("FCM", active = false)
        val provider2 = createProvider("UnifiedPush", active = true)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val active = manager.getActiveProvider()
        assertNotNull(active)
        assertEquals("UnifiedPush", active!!.name)
    }

    @Test
    fun `getActiveProvider returns null when no provider is active`() = runTest {
        val provider1 = createProvider("FCM", active = false)
        val provider2 = createProvider("UnifiedPush", active = false)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        assertNull(manager.getActiveProvider())
    }

    @Test
    fun `selectAndRegister registers the requested provider`() = runTest {
        val result = PushRegistrationResult("token123", "https://push.example.com", true)
        val provider1 = createProvider("FCM", available = true, registrationResult = PushRegistrationResult("fcm-token", ""))
        val provider2 = createProvider("UnifiedPush", available = true, registrationResult = result)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val reg = manager.selectAndRegister("UnifiedPush")
        assertNotNull(reg)
        assertEquals("token123", reg!!.pushToken)
        assertEquals(true, reg.encrypt)
    }

    @Test
    fun `selectAndRegister returns null when requested provider unavailable`() = runTest {
        val provider1 = createProvider("FCM", available = true, registrationResult = PushRegistrationResult("fcm-token", ""))
        val provider2 = createProvider("UnifiedPush", available = false)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val reg = manager.selectAndRegister("UnifiedPush")
        assertNull(reg)
    }

    @Test
    fun `selectAndRegister returns null when provider not found`() = runTest {
        val provider1 = createProvider("FCM", available = true)
        val manager = PushProviderManager(setOf(provider1), serverManager)

        assertNull(manager.selectAndRegister("UnifiedPush"))
    }

    @Test
    fun `getAllProviders returns all providers`() {
        val provider1 = createProvider("WebSocket")
        val provider2 = createProvider("FCM")
        val provider3 = createProvider("UnifiedPush")
        val manager = PushProviderManager(setOf(provider1, provider2, provider3), serverManager)

        val all = manager.getAllProviders()
        assertEquals(3, all.size)
    }

    @Test
    fun `getProvider returns provider by name`() {
        val provider1 = createProvider("FCM")
        val provider2 = createProvider("UnifiedPush")
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val found = manager.getProvider("FCM")
        assertNotNull(found)
        assertEquals("FCM", found!!.name)
    }

    @Test
    fun `getProvider returns null for unknown name`() {
        val provider1 = createProvider("FCM")
        val manager = PushProviderManager(setOf(provider1), serverManager)

        assertNull(manager.getProvider("UnifiedPush"))
    }

    @Test
    fun `updateServerRegistration updates all default servers`() = runTest {
        val server1 = mockk<Server>(relaxed = true) { every { id } returns 1 }
        val server2 = mockk<Server>(relaxed = true) { every { id } returns 2 }
        coEvery { serverManager.servers() } returns listOf(server1, server2)

        val manager = PushProviderManager(emptySet(), serverManager)
        val result = PushRegistrationResult("token", "https://push.example.com", true)

        manager.updateServerRegistration(result)

        coVerify(exactly = 1) { serverManager.integrationRepository(1) }
        coVerify(exactly = 1) { serverManager.integrationRepository(2) }
        coVerify(exactly = 2) { integrationRepository.updateRegistration(any(), any()) }
    }

    @Test
    fun `updateServerRegistration updates single server when serverId specified`() = runTest {
        val server = mockk<Server>(relaxed = true) { every { id } returns 42 }
        coEvery { serverManager.getServer(42) } returns server

        val manager = PushProviderManager(emptySet(), serverManager)
        val result = PushRegistrationResult("token", "", false)

        manager.updateServerRegistration(result, serverId = 42)

        coVerify(exactly = 1) { serverManager.integrationRepository(42) }
        coVerify(exactly = 1) { integrationRepository.updateRegistration(any(), any()) }
    }

    @Test
    fun `updateServerRegistration skips when not authenticated`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        val manager = PushProviderManager(emptySet(), serverManager)
        val result = PushRegistrationResult("token", "", false)

        manager.updateServerRegistration(result)

        coVerify(exactly = 0) { integrationRepository.updateRegistration(any(), any()) }
    }

    @Test
    fun `selectAndRegister unregisters current provider when switching`() = runTest {
        var unregistered = false
        val currentProvider = object : PushProvider {
            override val name = "FCM"
            override suspend fun isAvailable() = true
            override suspend fun isActive() = true
            override suspend fun register() = PushRegistrationResult("fcm", "")
            override suspend fun unregister() {
                unregistered = true
            }
        }
        val newResult = PushRegistrationResult("up-token", "https://up.example.com", true)
        val newProvider = object : PushProvider {
            override val name = "UnifiedPush"
            override suspend fun isAvailable() = true
            override suspend fun isActive() = false
            override suspend fun register() = newResult
            override suspend fun unregister() {}
        }

        val manager = PushProviderManager(setOf(currentProvider, newProvider), serverManager)
        val reg = manager.selectAndRegister("UnifiedPush")

        assertEquals(true, unregistered)
        assertNotNull(reg)
        assertEquals("up-token", reg!!.pushToken)
    }

    @Test
    fun `PushRegistrationResult default values`() {
        val result = PushRegistrationResult("token")
        assertNull(result.pushUrl)
        assertEquals(false, result.encrypt)
    }
}
