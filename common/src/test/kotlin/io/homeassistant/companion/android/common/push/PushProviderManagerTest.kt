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
        every { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.updateRegistration(any(), any()) } just Runs
    }

    private fun createProvider(
        name: String,
        priority: Int,
        available: Boolean = true,
        active: Boolean = false,
        registrationResult: PushRegistrationResult? = null
    ): PushProvider = object : PushProvider {
        override val name = name
        override val priority = priority
        override suspend fun isAvailable() = available
        override suspend fun isActive() = active
        override suspend fun register() = registrationResult
        override suspend fun unregister() {}
    }

    @Test
    fun `getActiveProvider returns the active provider`() = runTest {
        val provider1 = createProvider("FCM", 20, active = false)
        val provider2 = createProvider("UnifiedPush", 10, active = true)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val active = manager.getActiveProvider()
        assertNotNull(active)
        assertEquals("UnifiedPush", active!!.name)
    }

    @Test
    fun `getActiveProvider returns null when no provider is active`() = runTest {
        val provider1 = createProvider("FCM", 20, active = false)
        val provider2 = createProvider("UnifiedPush", 10, active = false)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        assertNull(manager.getActiveProvider())
    }

    @Test
    fun `getBestAvailableProvider returns highest priority available provider`() = runTest {
        val provider1 = createProvider("FCM", 20, available = true)
        val provider2 = createProvider("UnifiedPush", 10, available = true)
        val provider3 = createProvider("WebSocket", 30, available = true)
        val manager = PushProviderManager(setOf(provider1, provider2, provider3), serverManager)

        val best = manager.getBestAvailableProvider()
        assertNotNull(best)
        assertEquals("UnifiedPush", best!!.name)
    }

    @Test
    fun `getBestAvailableProvider skips unavailable providers`() = runTest {
        val provider1 = createProvider("FCM", 20, available = true)
        val provider2 = createProvider("UnifiedPush", 10, available = false)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val best = manager.getBestAvailableProvider()
        assertNotNull(best)
        assertEquals("FCM", best!!.name)
    }

    @Test
    fun `getBestAvailableProvider returns null when no provider is available`() = runTest {
        val provider1 = createProvider("FCM", 20, available = false)
        val provider2 = createProvider("UnifiedPush", 10, available = false)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        assertNull(manager.getBestAvailableProvider())
    }

    @Test
    fun `selectAndRegister uses preferred provider when available`() = runTest {
        val result = PushRegistrationResult("token123", "https://push.example.com", true)
        val provider1 = createProvider("FCM", 20, available = true, registrationResult = PushRegistrationResult("fcm-token", ""))
        val provider2 = createProvider("UnifiedPush", 10, available = true, registrationResult = result)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val reg = manager.selectAndRegister("UnifiedPush")
        assertNotNull(reg)
        assertEquals("token123", reg!!.pushToken)
        assertEquals(true, reg.encrypt)
    }

    @Test
    fun `selectAndRegister falls back when preferred provider unavailable`() = runTest {
        val fcmResult = PushRegistrationResult("fcm-token", "")
        val provider1 = createProvider("FCM", 20, available = true, registrationResult = fcmResult)
        val provider2 = createProvider("UnifiedPush", 10, available = false, registrationResult = null)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val reg = manager.selectAndRegister("UnifiedPush")
        assertNotNull(reg)
        assertEquals("fcm-token", reg!!.pushToken)
    }

    @Test
    fun `selectAndRegister returns null when no provider available`() = runTest {
        val provider1 = createProvider("FCM", 20, available = false)
        val manager = PushProviderManager(setOf(provider1), serverManager)

        assertNull(manager.selectAndRegister())
    }

    @Test
    fun `getAllProviders returns sorted by priority`() {
        val provider1 = createProvider("WebSocket", 30)
        val provider2 = createProvider("FCM", 20)
        val provider3 = createProvider("UnifiedPush", 10)
        val manager = PushProviderManager(setOf(provider1, provider2, provider3), serverManager)

        val all = manager.getAllProviders()
        assertEquals(3, all.size)
        assertEquals("UnifiedPush", all[0].name)
        assertEquals("FCM", all[1].name)
        assertEquals("WebSocket", all[2].name)
    }

    @Test
    fun `getProvider returns provider by name`() {
        val provider1 = createProvider("FCM", 20)
        val provider2 = createProvider("UnifiedPush", 10)
        val manager = PushProviderManager(setOf(provider1, provider2), serverManager)

        val found = manager.getProvider("FCM")
        assertNotNull(found)
        assertEquals("FCM", found!!.name)
    }

    @Test
    fun `getProvider returns null for unknown name`() {
        val provider1 = createProvider("FCM", 20)
        val manager = PushProviderManager(setOf(provider1), serverManager)

        assertNull(manager.getProvider("UnifiedPush"))
    }

    @Test
    fun `updateServerRegistration updates all default servers`() = runTest {
        val server1 = mockk<Server>(relaxed = true) { every { id } returns 1 }
        val server2 = mockk<Server>(relaxed = true) { every { id } returns 2 }
        every { serverManager.defaultServers } returns listOf(server1, server2)

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
        every { serverManager.getServer(42) } returns server

        val manager = PushProviderManager(emptySet(), serverManager)
        val result = PushRegistrationResult("token", "", false)

        manager.updateServerRegistration(result, serverId = 42)

        coVerify(exactly = 1) { serverManager.integrationRepository(42) }
        coVerify(exactly = 1) { integrationRepository.updateRegistration(any(), any()) }
    }

    @Test
    fun `updateServerRegistration skips when not authenticated`() = runTest {
        every { serverManager.isRegistered() } returns false

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
            override val priority = 20
            override suspend fun isAvailable() = true
            override suspend fun isActive() = true
            override suspend fun register() = PushRegistrationResult("fcm", "")
            override suspend fun unregister() { unregistered = true }
        }
        val newResult = PushRegistrationResult("up-token", "https://up.example.com", true)
        val newProvider = object : PushProvider {
            override val name = "UnifiedPush"
            override val priority = 10
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
