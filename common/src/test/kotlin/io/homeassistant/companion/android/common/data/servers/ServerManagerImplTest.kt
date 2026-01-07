package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepositoryFactory
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.common.data.integration.IntegrationRepositoryFactory
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepositoryFactory
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.server.TemporaryServer
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class ServerManagerImplTest {

    private val authenticationRepositoryFactory: AuthenticationRepositoryFactory = mockk()
    private val integrationRepositoryFactory: IntegrationRepositoryFactory = mockk()
    private val webSocketRepositoryFactory: WebSocketRepositoryFactory = mockk()
    private val serverConnectionStateProviderFactory: ServerConnectionStateProviderFactory = mockk()
    private val prefsRepository: PrefsRepository = mockk()
    private val serverDao: ServerDao = mockk()
    private val sensorDao: SensorDao = mockk()
    private val settingsDao: SettingsDao = mockk()
    private val localStorage: LocalStorage = mockk()

    private lateinit var serverManager: ServerManagerImpl

    private fun createServer(
        id: Int = 1,
        name: String = "Test Server",
        externalUrl: String = "https://example.com",
        webhookId: String? = "webhook123",
    ) = Server(
        id = id,
        _name = name,
        connection = ServerConnectionInfo(externalUrl = externalUrl, webhookId = webhookId),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @BeforeEach
    fun setup() {
        serverManager = ServerManagerImpl(
            authenticationRepositoryFactory = authenticationRepositoryFactory,
            integrationRepositoryFactory = integrationRepositoryFactory,
            webSocketRepositoryFactory = webSocketRepositoryFactory,
            serverConnectionStateProviderFactory = serverConnectionStateProviderFactory,
            prefsRepository = prefsRepository,
            serverDao = serverDao,
            sensorDao = sensorDao,
            settingsDao = settingsDao,
            localStorage = localStorage,
        )
    }

    @Nested
    inner class ServersTest {

        @Test
        fun `Given servers exist when servers then returns all servers from DAO`() = runTest {
            val servers = listOf(createServer(id = 1), createServer(id = 2))
            coEvery { serverDao.getAll() } returns servers

            val result = serverManager.servers()

            assertEquals(servers, result)
        }

        @Test
        fun `Given no servers when servers then returns empty list`() = runTest {
            coEvery { serverDao.getAll() } returns emptyList()

            val result = serverManager.servers()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ServersFlowTest {

        @Test
        fun `Given serversFlow then returns flow from DAO`() {
            val serversFlow = flowOf(listOf(createServer()))
            every { serverDao.getAllFlow() } returns serversFlow

            val result = serverManager.serversFlow

            assertEquals(serversFlow, result)
        }
    }

    @Nested
    inner class IsRegisteredTest {

        @Test
        fun `Given no servers when isRegistered then returns false`() = runTest {
            coEvery { serverDao.getAll() } returns emptyList()

            val result = serverManager.isRegistered()

            assertFalse(result)
        }

        @Test
        fun `Given server has no webhookId when isRegistered then returns false`() = runTest {
            val unregisteredServer = createServer(webhookId = null)
            coEvery { serverDao.getAll() } returns listOf(unregisteredServer)

            val result = serverManager.isRegistered()

            assertFalse(result)
        }

        @Test
        fun `Given server with webhookId and with connected session when isRegistered then returns true`() = runTest {
            val server = createServer(id = 1, webhookId = "webhook123")
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.getAll() } returns listOf(server)
            coEvery { authenticationRepositoryFactory.create(1) } returns authRepo
            coEvery { authRepo.getSessionState() } returns SessionState.CONNECTED
            coEvery { serverDao.get(1) } returns server

            val result = serverManager.isRegistered()

            assertTrue(result)
        }

        @Test
        fun `Given server with webhookId and with anonymous session when isRegistered then returns false`() = runTest {
            val server = createServer(id = 1, webhookId = "webhook123")
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.getAll() } returns listOf(server)
            coEvery { authenticationRepositoryFactory.create(1) } returns authRepo
            coEvery { authRepo.getSessionState() } returns SessionState.ANONYMOUS
            coEvery { serverDao.get(1) } returns server

            val result = serverManager.isRegistered()

            assertFalse(result)
        }

        @Test
        fun `Given multiple servers with one connected when isRegistered then returns true`() = runTest {
            val server1 = createServer(id = 1, webhookId = null)
            val server2 = createServer(id = 2, webhookId = "webhook123")
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.getAll() } returns listOf(server1, server2)
            coEvery { authenticationRepositoryFactory.create(2) } returns authRepo
            coEvery { authRepo.getSessionState() } returns SessionState.CONNECTED
            coEvery { serverDao.get(2) } returns server2

            val result = serverManager.isRegistered()

            assertTrue(result)
        }
    }

    @Nested
    inner class AddServerTest {

        @Test
        fun `Given TemporaryServer when addServer then adds to DAO and return new ID`() = runTest {
            val temporaryServer = TemporaryServer(
                externalUrl = "https://home.example.com",
                session = ServerSessionInfo(accessToken = "token123"),
                allowInsecureConnection = false,
            )
            val serverSlot = slot<Server>()
            coEvery { serverDao.add(capture(serverSlot)) } returns 42L

            val result = serverManager.addServer(temporaryServer)

            assertEquals(42, result)
            assertEquals("https://home.example.com", serverSlot.captured.connection.externalUrl)
            assertEquals(false, serverSlot.captured.connection.allowInsecureConnection)
            assertEquals("token123", serverSlot.captured.session.accessToken)
        }
    }

    @Nested
    inner class GetServerByIdTest {

        @Test
        fun `Given server exists when getServer with id then returns server`() = runTest {
            val server = createServer(id = 5)
            coEvery { serverDao.get(5) } returns server

            val result = serverManager.getServer(5)

            assertEquals(server, result)
        }

        @Test
        fun `Given server not found when getServer with id then returns null`() = runTest {
            coEvery { serverDao.get(99) } returns null

            val result = serverManager.getServer(99)

            assertNull(result)
        }

        @Test
        fun `Given active server set when getServer with SERVER_ID_ACTIVE then returns active server`() = runTest {
            val server = createServer(id = 3)
            coEvery { localStorage.getInt("active_server") } returns 3
            coEvery { serverDao.get(3) } returns server

            val result = serverManager.getServer(SERVER_ID_ACTIVE)

            assertEquals(server, result)
        }

        @Test
        fun `Given no active server but servers exist when getServer with SERVER_ID_ACTIVE then returns last server`() = runTest {
            val server = createServer(id = 7)
            coEvery { localStorage.getInt("active_server") } returns null
            coEvery { serverDao.getLastServerId() } returns 7
            coEvery { serverDao.get(7) } returns server

            val result = serverManager.getServer(SERVER_ID_ACTIVE)

            assertEquals(server, result)
        }

        @Test
        fun `Given no active server and no servers when getServer with SERVER_ID_ACTIVE then returns null`() = runTest {
            coEvery { localStorage.getInt("active_server") } returns null
            coEvery { serverDao.getLastServerId() } returns null

            val result = serverManager.getServer(SERVER_ID_ACTIVE)

            assertNull(result)
        }
    }

    @Nested
    inner class GetServerByWebhookIdTest {

        @Test
        fun `Given server exists when getServer with webhookId then returns server`() = runTest {
            val server = createServer(webhookId = "webhook456")
            coEvery { serverDao.get("webhook456") } returns server

            val result = serverManager.getServer("webhook456")

            assertEquals(server, result)
        }

        @Test
        fun `Given server not found when getServer with webhookId then returns null`() = runTest {
            coEvery { serverDao.get("unknown") } returns null

            val result = serverManager.getServer("unknown")

            assertNull(result)
        }
    }

    @Nested
    inner class ActivateServerTest {

        @Test
        fun `Given valid id when activateServer then stores in localStorage`() = runTest {
            coEvery { localStorage.putInt("active_server", 5) } just Runs

            serverManager.activateServer(5)

            coVerify { localStorage.putInt("active_server", 5) }
        }

        @Test
        fun `Given SERVER_ID_ACTIVE when activateServer then does nothing`() = runTest {
            serverManager.activateServer(SERVER_ID_ACTIVE)

            coVerify(exactly = 0) { localStorage.putInt(any(), any()) }
        }
    }

    @Nested
    inner class UpdateServerTest {

        @Test
        fun `Given server when updateServer then updates in DAO`() = runTest {
            val server = createServer(id = 3, name = "Updated Server")
            coEvery { serverDao.update(server) } just Runs

            serverManager.updateServer(server)

            coVerify { serverDao.update(server) }
        }
    }

    @Nested
    inner class RemoveServerTest {

        @Test
        fun `Given server when removeServer then cleans up all resources in order`() = runTest {
            val serverId = 5
            val authRepo: AuthenticationRepositoryImpl = mockk()
            val integrationRepo: IntegrationRepositoryImpl = mockk()
            val webSocketRepo: WebSocketRepository = mockk()

            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo
            coEvery { integrationRepositoryFactory.create(serverId) } returns integrationRepo
            coEvery { webSocketRepositoryFactory.create(serverId) } returns webSocketRepo
            coEvery { authRepo.deletePreferences() } just Runs
            coEvery { integrationRepo.deletePreferences() } just Runs
            coEvery { prefsRepository.removeServer(serverId) } just Runs
            coEvery { localStorage.getInt("active_server") } returns null
            coEvery { settingsDao.delete(serverId) } just Runs
            coEvery { sensorDao.removeServer(serverId) } just Runs
            coEvery { serverDao.delete(serverId) } just Runs
            coEvery { webSocketRepo.shutdown() } just Runs

            // First access the webSocket to cache it so shutdown gets called
            serverManager.webSocketRepository(serverId)
            serverManager.removeServer(serverId)

            coVerifyOrder {
                authRepo.deletePreferences()
                integrationRepo.deletePreferences()
                prefsRepository.removeServer(serverId)
                webSocketRepo.shutdown()
                settingsDao.delete(serverId)
                sensorDao.removeServer(serverId)
                serverDao.delete(serverId)
            }
        }

        @Test
        fun `Given active server when removeServer then clears active server from localStorage`() = runTest {
            val serverId = 5
            val authRepo: AuthenticationRepositoryImpl = mockk()
            val integrationRepo: IntegrationRepositoryImpl = mockk()

            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo
            coEvery { integrationRepositoryFactory.create(serverId) } returns integrationRepo
            coEvery { authRepo.deletePreferences() } just Runs
            coEvery { integrationRepo.deletePreferences() } just Runs
            coEvery { prefsRepository.removeServer(serverId) } just Runs
            coEvery { localStorage.getInt("active_server") } returns serverId
            coEvery { localStorage.remove("active_server") } just Runs
            coEvery { settingsDao.delete(serverId) } just Runs
            coEvery { sensorDao.removeServer(serverId) } just Runs
            coEvery { serverDao.delete(serverId) } just Runs

            serverManager.removeServer(serverId)

            coVerify { localStorage.remove("active_server") }
        }

        @Test
        fun `Given non-active server when removeServer then does not clear active server`() = runTest {
            val serverId = 5
            val authRepo: AuthenticationRepositoryImpl = mockk()
            val integrationRepo: IntegrationRepositoryImpl = mockk()

            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo
            coEvery { integrationRepositoryFactory.create(serverId) } returns integrationRepo
            coEvery { authRepo.deletePreferences() } just Runs
            coEvery { integrationRepo.deletePreferences() } just Runs
            coEvery { prefsRepository.removeServer(serverId) } just Runs
            coEvery { localStorage.getInt("active_server") } returns 10
            coEvery { settingsDao.delete(serverId) } just Runs
            coEvery { sensorDao.removeServer(serverId) } just Runs
            coEvery { serverDao.delete(serverId) } just Runs

            serverManager.removeServer(serverId)

            coVerify(exactly = 0) { localStorage.remove("active_server") }
        }

        @Test
        fun `Given server with webSocketRepo when removeServer then shuts down webSocket`() = runTest {
            val serverId = 5
            val authRepo: AuthenticationRepositoryImpl = mockk()
            val integrationRepo: IntegrationRepositoryImpl = mockk()
            val webSocketRepo: WebSocketRepository = mockk()

            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo
            coEvery { integrationRepositoryFactory.create(serverId) } returns integrationRepo
            coEvery { webSocketRepositoryFactory.create(serverId) } returns webSocketRepo
            coEvery { authRepo.deletePreferences() } just Runs
            coEvery { integrationRepo.deletePreferences() } just Runs
            coEvery { prefsRepository.removeServer(serverId) } just Runs
            coEvery { localStorage.getInt("active_server") } returns null
            coEvery { settingsDao.delete(serverId) } just Runs
            coEvery { sensorDao.removeServer(serverId) } just Runs
            coEvery { serverDao.delete(serverId) } just Runs
            coEvery { webSocketRepo.shutdown() } just Runs

            // First access the webSocket to cache it
            serverManager.webSocketRepository(serverId)
            serverManager.removeServer(serverId)

            coVerify { webSocketRepo.shutdown() }
        }
    }

    @Nested
    inner class AuthenticationRepositoryTest {

        @Test
        fun `Given valid serverId when authenticationRepository then returns repository`() = runTest {
            val serverId = 3
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo

            val result = serverManager.authenticationRepository(serverId)

            assertEquals(authRepo, result)
        }

        @Test
        fun `Given SERVER_ID_ACTIVE when authenticationRepository then resolves to active server`() = runTest {
            val activeServerId = 7
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { localStorage.getInt("active_server") } returns activeServerId
            coEvery { serverDao.get(activeServerId) } returns createServer(id = activeServerId)
            coEvery { authenticationRepositoryFactory.create(activeServerId) } returns authRepo

            val result = serverManager.authenticationRepository(SERVER_ID_ACTIVE)

            assertEquals(authRepo, result)
        }

        @Test
        fun `Given no server exists when authenticationRepository then throws`() = runTest {
            coEvery { serverDao.get(99) } returns null

            try {
                serverManager.authenticationRepository(99)
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // Expected
            }
        }

        @Test
        fun `Given repository already created when authenticationRepository called again then returns cached instance`() = runTest {
            val serverId = 3
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } returns authRepo

            val result1 = serverManager.authenticationRepository(serverId)
            val result2 = serverManager.authenticationRepository(serverId)

            assertEquals(result1, result2)
            coVerify(exactly = 1) { authenticationRepositoryFactory.create(serverId) }
        }

        @Test
        fun `Given slow factory when concurrent authenticationRepository calls then creates only one instance`() = runTest {
            val serverId = 3
            val authRepo: AuthenticationRepositoryImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { authenticationRepositoryFactory.create(serverId) } coAnswers {
                delay(10)
                authRepo
            }

            // Use real parallelism to test thread-safety
            val results = withContext(Dispatchers.Default) {
                (1..10).map {
                    async { serverManager.authenticationRepository(serverId) }
                }.awaitAll()
            }

            results.forEach { assertEquals(authRepo, it) }
            coVerify(exactly = 1) { authenticationRepositoryFactory.create(serverId) }
        }
    }

    @Nested
    inner class IntegrationRepositoryTest {

        @Test
        fun `Given valid serverId when integrationRepository then returns repository`() = runTest {
            val serverId = 3
            val integrationRepo: IntegrationRepositoryImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { integrationRepositoryFactory.create(serverId) } returns integrationRepo

            val result = serverManager.integrationRepository(serverId)

            assertEquals(integrationRepo, result)
        }

        @Test
        fun `Given no server exists when integrationRepository then throws`() = runTest {
            coEvery { serverDao.get(99) } returns null

            try {
                serverManager.integrationRepository(99)
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // Expected
            }
        }

        @Test
        fun `Given slow factory when concurrent integrationRepository calls then creates only one instance`() = runTest {
            val serverId = 3
            val integrationRepo: IntegrationRepositoryImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { integrationRepositoryFactory.create(serverId) } coAnswers {
                delay(10)
                integrationRepo
            }

            // Use real parallelism to test thread-safety
            val results = withContext(Dispatchers.Default) {
                (1..10).map {
                    async { serverManager.integrationRepository(serverId) }
                }.awaitAll()
            }

            results.forEach { assertEquals(integrationRepo, it) }
            coVerify(exactly = 1) { integrationRepositoryFactory.create(serverId) }
        }
    }

    @Nested
    inner class WebSocketRepositoryTest {

        @Test
        fun `Given valid serverId when webSocketRepository then returns repository`() = runTest {
            val serverId = 3
            val webSocketRepo: WebSocketRepository = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { webSocketRepositoryFactory.create(serverId) } returns webSocketRepo

            val result = serverManager.webSocketRepository(serverId)

            assertEquals(webSocketRepo, result)
        }

        @Test
        fun `Given no server exists when webSocketRepository then throws`() = runTest {
            coEvery { serverDao.get(99) } returns null

            try {
                serverManager.webSocketRepository(99)
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // Expected
            }
        }

        @Test
        fun `Given slow factory when concurrent webSocketRepository calls then creates only one instance`() = runTest {
            val serverId = 3
            val webSocketRepo: WebSocketRepository = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { webSocketRepositoryFactory.create(serverId) } coAnswers {
                delay(10)
                webSocketRepo
            }

            // Use real parallelism to test thread-safety
            val results = withContext(Dispatchers.Default) {
                (1..10).map {
                    async { serverManager.webSocketRepository(serverId) }
                }.awaitAll()
            }

            results.forEach { assertEquals(webSocketRepo, it) }
            coVerify(exactly = 1) { webSocketRepositoryFactory.create(serverId) }
        }
    }

    @Nested
    inner class ConnectionStateProviderTest {

        @Test
        fun `Given valid serverId when connectionStateProvider then returns provider`() = runTest {
            val serverId = 3
            val provider: ServerConnectionStateProviderImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { serverConnectionStateProviderFactory.create(serverId) } returns provider

            val result = serverManager.connectionStateProvider(serverId)

            assertEquals(provider, result)
        }

        @Test
        fun `Given no server exists when connectionStateProvider then throws`() = runTest {
            coEvery { serverDao.get(99) } returns null

            try {
                serverManager.connectionStateProvider(99)
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // Expected
            }
        }

        @Test
        fun `Given slow factory when concurrent connectionStateProvider calls then creates only one instance`() = runTest {
            val serverId = 3
            val provider: ServerConnectionStateProviderImpl = mockk()
            coEvery { serverDao.get(serverId) } returns createServer(id = serverId)
            coEvery { serverConnectionStateProviderFactory.create(serverId) } coAnswers {
                delay(10)
                provider
            }

            // Use real parallelism to test thread-safety
            val results = withContext(Dispatchers.Default) {
                (1..10).map {
                    async { serverManager.connectionStateProvider(serverId) }
                }.awaitAll()
            }

            results.forEach { assertEquals(provider, it) }
            coVerify(exactly = 1) { serverConnectionStateProviderFactory.create(serverId) }
        }
    }
}
