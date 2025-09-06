package io.homeassistant.companion.android.launcher

import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class LauncherViewModelTest {
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val networkStatusMonitor: NetworkStatusMonitor = mockk(relaxed = true)
    private lateinit var viewModel: LauncherViewModel

    private fun createViewModel() {
        viewModel = LauncherViewModel(serverManager, networkStatusMonitor)
    }

    @BeforeEach
    fun setUp() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given active server connected and registered, when network is READY_LOCAL, then navigate to frontend and resync registration`() = runTest {
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)
        val webSocketRepository = mockk<WebSocketRepository>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        every { serverManager.defaultServers } returns listOf(server)
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_LOCAL)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Frontend, viewModel.navigationEventsFlow.replayCache.first())
        assertEquals(0, networkStateFlow.subscriptionCount.value)

        // verify resync registration
        coVerify(exactly = 1) {
            serverManager.integrationRepository(any())
            integrationRepository.updateRegistration(any())
            integrationRepository.getConfig()
            webSocketRepository.getCurrentUser()
        }
    }

    @Test
    fun `Given active server connected and registered and another one not active, when network is READY_REMOTE, then navigate to frontend and resync registrations`() = runTest {
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)
        val webSocketRepository = mockk<WebSocketRepository>(relaxed = true)
        val activeServer = mockk<Server>(relaxed = true)
        val notActiveServer = mockk<Server>(relaxed = true)
        every { notActiveServer.id } returns 1
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns activeServer
        every { serverManager.defaultServers } returns listOf(activeServer, notActiveServer)
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_REMOTE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Frontend, viewModel.navigationEventsFlow.replayCache.first())
        assertEquals(0, networkStateFlow.subscriptionCount.value)

        // verify resync registration
        coVerify(exactly = 1) {
            serverManager.integrationRepository(0)
            serverManager.integrationRepository(1)
        }
        coVerify(exactly = 2) {
            integrationRepository.updateRegistration(any())
            integrationRepository.getConfig()
            webSocketRepository.getCurrentUser()
        }
    }

    @Test
    fun `Given active server connected and registered, when network is CONNECTING, then do not navigate but continue observing network changes`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.CONNECTING)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.navigationEventsFlow.replayCache.isEmpty())
        assertEquals(1, networkStateFlow.subscriptionCount.value)
    }

    @Test
    fun `Given active server connected and registered, when network is UNAVAILABLE, then do not navigate but continue observing network changes`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.UNAVAILABLE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.navigationEventsFlow.replayCache.isEmpty())
        assertEquals(1, networkStateFlow.subscriptionCount.value)
    }

    @Test
    fun `Given active server connected and registered, when network is CONNECTING then READY, then navigate to frontend`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.CONNECTING)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.navigationEventsFlow.replayCache.isEmpty())
        assertEquals(1, networkStateFlow.subscriptionCount.value)

        networkStateFlow.emit(NetworkState.READY_REMOTE)
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Frontend, viewModel.navigationEventsFlow.replayCache.first())
        assertEquals(0, networkStateFlow.subscriptionCount.value)
    }

    @Test
    fun `Given no active server, when creating viewModel, then navigate to onboarding`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns null

        createViewModel()
        advanceUntilIdle()

        assertEquals(LauncherNavigationEvent.Onboarding, viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given server not registered, when creating viewModel, then navigate to onboarding`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns false

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Onboarding, viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given session not connected, when creating viewModel, then navigate to onboarding`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.ANONYMOUS

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Onboarding, viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given IllegalArgumentException thrown while getting server, when creating viewModel, then navigate to onboarding`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } throws IllegalArgumentException("Wrong server")
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.ANONYMOUS

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Onboarding, viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `given invalid servers, when creating viewModel, then cleanup servers`() = runTest {
        val invalidServer = Server(
            id = 1,
            _name = "Invalid Server",
            connection = ServerConnectionInfo(
                externalUrl = "http://invalid.com",
            ),
            session = ServerSessionInfo(),
            user = ServerUserInfo(
                id = null,
                name = null,
                isOwner = false,
                isAdmin = false,
            ),
        )
        val connectedServer = Server(
            id = 2,
            _name = "Valid Server",
            connection = ServerConnectionInfo(
                externalUrl = "http://valid.com",
            ),
            session = ServerSessionInfo(),
            user = ServerUserInfo(
                id = null,
                name = null,
                isOwner = false,
                isAdmin = false,
            ),
        )
        coEvery { serverManager.defaultServers } returns listOf(invalidServer, connectedServer)
        coEvery { serverManager.authenticationRepository(invalidServer.id).getSessionState() } returns SessionState.ANONYMOUS
        coEvery { serverManager.authenticationRepository(connectedServer.id).getSessionState() } returns SessionState.CONNECTED

        createViewModel()
        advanceUntilIdle()

        coVerify { serverManager.removeServer(1) }
        coVerify(exactly = 1) { serverManager.removeServer(any()) }
    }

    @Test
    fun `Given no navigation event, when calling shouldShowSplashScreen, then return true`() = runTest {
        createViewModel()

        assertTrue(viewModel.shouldShowSplashScreen())
    }

    @Test
    fun `Given a navigation event, when calling shouldShowSplashScreen, then return false`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns null

        createViewModel()
        advanceUntilIdle()

        assertEquals(LauncherNavigationEvent.Onboarding, viewModel.navigationEventsFlow.replayCache.first())
        assertTrue(!viewModel.shouldShowSplashScreen())
    }
}
