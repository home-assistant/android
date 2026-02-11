package io.homeassistant.companion.android.frontend.url

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.SecurityState
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@ExtendWith(ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FrontendUrlManagerTest {

    private val serverManager: ServerManager = mockk()
    private val sessionManager: ServerSessionManager = mockk()
    private val connectionStateProvider: ServerConnectionStateProvider = mockk()
    private lateinit var urlManager: FrontendUrlManager

    @BeforeEach
    fun setup() {
        urlManager = FrontendUrlManager(serverManager, sessionManager)
    }

    @Test
    fun `Given server not found when serverUrlFlow then returns ServerNotFound`() = runTest {
        coEvery { serverManager.getServer(1) } returns null

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.ServerNotFound)
            assertEquals(1, (result as UrlLoadResult.ServerNotFound).serverId)
            awaitComplete()
        }
    }

    @Test
    fun `Given session not connected when serverUrlFlow then returns SessionNotConnected`() = runTest {
        val server = createTestServer(id = 1)
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns false

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.SessionNotConnected)
            assertEquals(1, (result as UrlLoadResult.SessionNotConnected).serverId)
            awaitComplete()
        }
    }

    @Test
    fun `Given valid URL when serverUrlFlow then returns Success with external_auth parameter`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            val success = result as UrlLoadResult.Success
            assertEquals(1, success.serverId)
            assertEquals("https://home.example.com/?external_auth=1", success.url)
            awaitComplete()
        }
    }

    @Test
    fun `Given URL with path when serverUrlFlow then appends path to URL`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = "/dashboard").test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            val success = result as UrlLoadResult.Success
            assertEquals("https://home.example.com/dashboard?external_auth=1", success.url)
            awaitComplete()
        }
    }

    @Test
    fun `Given path with entityId prefix when serverUrlFlow then skips path handling`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = "entityId:light.living_room").test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            val success = result as UrlLoadResult.Success
            // Should not contain the entityId path in URL
            assertEquals("https://home.example.com/?external_auth=1", success.url)
            awaitComplete()
        }
    }

    @ParameterizedTest(name = "hasHomeSetup={0}, locationEnabled={1}")
    @CsvSource(
        "false, false",
        "false, true",
        "true, false",
        "true, true",
    )
    fun `Given insecure state when serverUrlFlow then returns InsecureBlocked with correct flags`(
        hasHomeSetup: Boolean,
        locationEnabled: Boolean,
    ) = runTest {
        val server = createTestServer(id = 1, externalUrl = "http://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.InsecureState)
        coEvery { connectionStateProvider.getSecurityState() } returns SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = hasHomeSetup,
            locationEnabled = locationEnabled,
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.InsecureBlocked)
            val blocked = result as UrlLoadResult.InsecureBlocked
            assertEquals(1, blocked.serverId)
            assertEquals(!hasHomeSetup, blocked.missingHomeSetup)
            assertEquals(!locationEnabled, blocked.missingLocation)
            awaitComplete()
        }
    }

    @Test
    fun `Given null URL when serverUrlFlow then returns NoUrlAvailable`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.HasUrl(null))

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.NoUrlAvailable)
            assertEquals(1, (result as UrlLoadResult.NoUrlAvailable).serverId)
            awaitComplete()
        }
    }

    @Test
    fun `Given SERVER_ID_ACTIVE when serverUrlFlow then resolves to actual server ID`() = runTest {
        val server = createTestServer(id = 42, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.getServer(42) } returns server
        coEvery { sessionManager.isSessionConnected(42) } returns true
        coEvery { serverManager.activateServer(42) } just runs
        coEvery { serverManager.connectionStateProvider(42) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = ServerManager.SERVER_ID_ACTIVE, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            assertEquals(42, (result as UrlLoadResult.Success).serverId)
            awaitComplete()
        }

        coVerify { serverManager.activateServer(42) }
        coVerify { serverManager.connectionStateProvider(42) }
    }

    @Test
    fun `Given plain text URL with null allowInsecureConnection when serverUrlFlow then returns SecurityLevelRequired`() = runTest {
        val connection = ServerConnectionInfo(
            externalUrl = "http://home.example.com",
            allowInsecureConnection = null,
        )
        val server = createTestServer(id = 1, connectionInfo = connection)
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("http://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.SecurityLevelRequired)
            assertEquals(1, (result as UrlLoadResult.SecurityLevelRequired).serverId)
            awaitComplete()
        }
    }

    @Test
    fun `Given security level already shown when serverUrlFlow then returns Success`() = runTest {
        val connection = ServerConnectionInfo(
            externalUrl = "http://home.example.com",
            allowInsecureConnection = null,
        )
        val server = createTestServer(id = 1, connectionInfo = connection)
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("http://home.example.com")),
        )

        urlManager.onSecurityLevelShown(serverId = 1)

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            awaitComplete()
        }
    }

    @Test
    fun `Given HTTPS URL with null allowInsecureConnection when serverUrlFlow then returns Success`() = runTest {
        val connection = ServerConnectionInfo(
            externalUrl = "https://home.example.com",
            allowInsecureConnection = null,
        )
        val server = createTestServer(id = 1, connectionInfo = connection)
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            awaitComplete()
        }
    }

    @Test
    fun `Given plain text URL with allowInsecureConnection true when serverUrlFlow then returns Success`() = runTest {
        val connection = ServerConnectionInfo(
            externalUrl = "http://home.example.com",
            allowInsecureConnection = true,
        )
        val server = createTestServer(id = 1, connectionInfo = connection)
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("http://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val result = awaitItem()
            assertTrue(result is UrlLoadResult.Success)
            awaitComplete()
        }
    }

    @Test
    fun `Given URL state changes to InsecureState and back to HasUrl when serverUrlFlow collecting then emits correct results`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
            UrlState.InsecureState,
            UrlState.HasUrl(URL("https://home.example.com")),
        )
        coEvery { connectionStateProvider.getSecurityState() } returns SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = true,
            locationEnabled = false,
        )

        urlManager.serverUrlFlow(serverId = 1, path = null).test {
            val first = awaitItem()
            assertTrue(first is UrlLoadResult.Success)

            val second = awaitItem()
            assertTrue(second is UrlLoadResult.InsecureBlocked)

            val third = awaitItem()
            assertTrue(third is UrlLoadResult.Success)
            awaitComplete()
        }
    }

    @Test
    fun `Given multiple URL emissions when serverUrlFlow then path only applied to first`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.HasUrl(URL("https://home.example.com")),
            UrlState.HasUrl(URL("https://home.example.com")),
        )

        urlManager.serverUrlFlow(serverId = 1, path = "/dashboard").test {
            val first = awaitItem() as UrlLoadResult.Success
            assertTrue(first.url.contains("/dashboard"), "First emission should contain path")

            val second = awaitItem() as UrlLoadResult.Success
            assertFalse(second.url.contains("/dashboard"), "Second emission should not contain path")
            awaitComplete()
        }
    }

    @Test
    fun `Given path with InsecureState first when serverUrlFlow then path is preserved for subsequent HasUrl`() = runTest {
        val server = createTestServer(id = 1, externalUrl = "https://home.example.com")
        coEvery { serverManager.getServer(1) } returns server
        coEvery { sessionManager.isSessionConnected(1) } returns true
        coEvery { serverManager.activateServer(1) } just runs
        coEvery { serverManager.connectionStateProvider(1) } returns connectionStateProvider
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(
            UrlState.InsecureState,
            UrlState.HasUrl(URL("https://home.example.com")),
        )
        coEvery { connectionStateProvider.getSecurityState() } returns SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = false,
            locationEnabled = false,
        )

        urlManager.serverUrlFlow(serverId = 1, path = "/dashboard").test {
            val first = awaitItem()
            assertTrue(first is UrlLoadResult.InsecureBlocked)

            val second = awaitItem() as UrlLoadResult.Success
            assertTrue(second.url.contains("/dashboard"), "Path should be preserved after InsecureState")
            awaitComplete()
        }
    }

    private fun createTestServer(
        id: Int,
        externalUrl: String = "https://home.example.com",
        connectionInfo: ServerConnectionInfo? = null,
    ): Server {
        val connection = connectionInfo ?: ServerConnectionInfo(externalUrl = externalUrl)
        return Server(
            id = id,
            _name = "Test Server",
            connection = connection,
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
    }
}
