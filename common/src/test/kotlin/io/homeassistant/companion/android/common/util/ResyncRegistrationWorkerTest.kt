package io.homeassistant.companion.android.common.util

import android.content.Context
import androidx.work.ListenableWorker
import dagger.hilt.EntryPoints
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.util.ResyncRegistrationWorker.Companion.ResyncRegistrationWorkerEntryPoint
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private val appVersion = AppVersion.from("1.1.1.", 1)
private val messagingToken = MessagingToken("hello")

@ExtendWith(ConsoleLogExtension::class)
class ResyncRegistrationWorkerTest {

    private val serverManager: ServerManager = mockk()
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)
    private val context: Context = mockk()

    private lateinit var worker: ResyncRegistrationWorker

    @BeforeEach
    fun setup() {
        every { context.applicationContext } returns context

        worker = ResyncRegistrationWorker(context, mockk())

        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository

        mockkStatic(EntryPoints::class)
        mockEntryPoints()
    }

    @Test
    fun `Given no servers registered when doWork then return success`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { serverManager.defaultServers }
    }

    @Test
    fun `Given servers registered when doWork then return success and update registration and cache`() = runTest {
        val server1 = mockk<Server>(relaxed = true).apply { every { id } returns 1 }
        val server2 = mockk<Server>(relaxed = true).apply { every { id } returns 2 }

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(server1, server2)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(1)
            serverManager.integrationRepository(2)
            serverManager.webSocketRepository(1)
            serverManager.webSocketRepository(2)
        }
        coVerify(exactly = 2) {
            integrationRepository.updateRegistration(any())
            integrationRepository.getConfig()
            webSocketRepository.getCurrentUser()
        }
    }

    @Test
    fun `Given servers registered but one throws while getting integration repository when doWork then return failure and update registration and cache only for one server`() = runTest {
        val server1 = mockk<Server>(relaxed = true).apply { every { id } returns 1 }
        val server2 = mockk<Server>(relaxed = true).apply { every { id } returns 2 }

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(server1, server2)

        coEvery { serverManager.integrationRepository(1) } throws IllegalStateException()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(1)
            serverManager.integrationRepository(2)
            serverManager.webSocketRepository(2)
        }
        coVerify(exactly = 0) {
            serverManager.webSocketRepository(1)
        }
        coVerify(exactly = 1) {
            integrationRepository.updateRegistration(any())
            integrationRepository.getConfig()
            webSocketRepository.getCurrentUser()
        }
    }

    @Test
    fun `Given servers registered but one throws while getting websocket repository when doWork then return failure and update registration and cache only for one server`() = runTest {
        val server1 = mockk<Server>(relaxed = true).apply { every { id } returns 1 }
        val server2 = mockk<Server>(relaxed = true).apply { every { id } returns 2 }

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(server1, server2)

        coEvery { serverManager.webSocketRepository(1) } throws IllegalStateException()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(1)
            serverManager.integrationRepository(2)
            serverManager.webSocketRepository(1)
            serverManager.webSocketRepository(2)
            webSocketRepository.getCurrentUser()
        }
        coVerify(exactly = 2) {
            integrationRepository.updateRegistration(any())
            integrationRepository.getConfig()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given server registered when doWork then return success and update registration with proper parameters`(pushWebsocketSupport: Boolean) = runTest {
        val server1 = mockk<Server>(relaxed = true).apply { every { id } returns 1 }

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.defaultServers } returns listOf(server1)
        mockEntryPoints(pushWebsocketSupport)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            serverManager.integrationRepository(1)
            serverManager.webSocketRepository(1)
            integrationRepository.updateRegistration(
                DeviceRegistration(
                    appVersion = appVersion,
                    deviceName = null,
                    pushToken = messagingToken,
                    pushWebsocket = pushWebsocketSupport,
                ),
            )
            integrationRepository.getConfig()
            webSocketRepository.getCurrentUser()
        }
    }

    private fun mockEntryPoints(pushWebsocketSupport: Boolean = false) {
        every {
            EntryPoints.get(any(), ResyncRegistrationWorkerEntryPoint::class.java)
        } returns mockk(relaxed = true) {
            every { serverManager() } returns serverManager
            every { appVersionProvider() } returns AppVersionProvider { appVersion }
            every { pushToken() } returns MessagingTokenProvider { return@MessagingTokenProvider messagingToken }
            every { pushWebsocketSupport() } returns pushWebsocketSupport
        }
    }
}
