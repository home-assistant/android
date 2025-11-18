package io.homeassistant.companion.android.launcher

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class LauncherViewModelTest {
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val networkStatusMonitor: NetworkStatusMonitor = mockk(relaxed = true)

    private val workManager: WorkManager = mockk()

    private lateinit var viewModel: LauncherViewModel

    private fun createViewModel(
        initialDeepLink: LauncherActivity.DeepLink? = null,
        hasLocationTrackingSupport: Boolean = false,
    ) {
        viewModel = LauncherViewModel(initialDeepLink, workManager, serverManager, networkStatusMonitor, hasLocationTrackingSupport)
    }

    @ParameterizedTest
    @EnumSource(NetworkState::class, names = ["READY_LOCAL", "READY_REMOTE"])
    fun `Given active server connected and registered, when network is READY, then navigate to frontend and resync registration`(
        state: NetworkState,
    ) = runTest {
        val activeServer = mockk<Server>(relaxed = true)
        val notActiveServer = mockk<Server>(relaxed = true)

        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        every { notActiveServer.id } returns 1
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns activeServer
        every { serverManager.defaultServers } returns listOf(activeServer, notActiveServer)
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(state)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Frontend(null, ServerManager.SERVER_ID_ACTIVE), viewModel.navigationEventsFlow.replayCache.first())
        assertEquals(0, networkStateFlow.subscriptionCount.value)

        // verify resync registration
        verify(exactly = 1) {
            workManager.enqueue(any<OneTimeWorkRequest>())
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
    fun `Given active server connected and registered, when network is CONNECTING then READY, then navigate to frontend and resync registrations`() = runTest {
        val server = mockk<Server>(relaxed = true)

        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

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
        assertEquals(LauncherNavigationEvent.Frontend(null, ServerManager.SERVER_ID_ACTIVE), viewModel.navigationEventsFlow.replayCache.first())
        assertEquals(0, networkStateFlow.subscriptionCount.value)

        // verify resync registration
        verify(exactly = 1) {
            workManager.enqueue(any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `Given no active server, when creating viewModel, then navigate to onboarding`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns null

        createViewModel()
        advanceUntilIdle()

        assertEquals(LauncherNavigationEvent.Onboarding(null, hideExistingServers = false, skipWelcome = false, hasLocationTrackingSupport = false), viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given server not registered, when creating viewModel, then navigate to onboarding`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns false

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Onboarding(null, hideExistingServers = false, skipWelcome = false, hasLocationTrackingSupport = false), viewModel.navigationEventsFlow.replayCache.first())
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
        assertEquals(LauncherNavigationEvent.Onboarding(null, hideExistingServers = false, skipWelcome = false, hasLocationTrackingSupport = false), viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given IllegalArgumentException thrown while getting server, when creating viewModel, then navigate to onboarding`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } throws IllegalArgumentException("Wrong server")
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.ANONYMOUS

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.navigationEventsFlow.replayCache.size)
        assertEquals(LauncherNavigationEvent.Onboarding(null, hideExistingServers = false, skipWelcome = false, hasLocationTrackingSupport = false), viewModel.navigationEventsFlow.replayCache.first())
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

        assertEquals(LauncherNavigationEvent.Onboarding(null, hideExistingServers = false, skipWelcome = false, hasLocationTrackingSupport = false), viewModel.navigationEventsFlow.replayCache.first())
        assertTrue(!viewModel.shouldShowSplashScreen())
    }

    @ParameterizedTest
    @CsvSource(
        "false, true, false",
        "true, false, false",
        "false, false, true",
        "true, true, true",
    )
    fun `Given initial deep link is OpenOnboarding when creating viewModel, then navigate to onboarding with the server url`(
        hideExistingServers: Boolean,
        skipWelcome: Boolean,
        hasLocationTrackingSupport: Boolean,
    ) = runTest {
        createViewModel(
            initialDeepLink = LauncherActivity.DeepLink.OpenOnboarding("http://homeassistant.io", hideExistingServers = hideExistingServers, skipWelcome = skipWelcome),
            hasLocationTrackingSupport = hasLocationTrackingSupport,
        )
        advanceUntilIdle()
        assertEquals(LauncherNavigationEvent.Onboarding("http://homeassistant.io", hideExistingServers = hideExistingServers, skipWelcome = skipWelcome, hasLocationTrackingSupport = hasLocationTrackingSupport), viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given initial deep link is NavigateTo when creating viewModel, then navigate to frontend with the server id and path`() = runTest {
        val serverId = 42
        val server = mockk<Server>(relaxed = true)
        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        coEvery { serverManager.getServer(serverId) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_REMOTE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel(LauncherActivity.DeepLink.NavigateTo("/path", serverId))
        advanceUntilIdle()
        assertEquals(LauncherNavigationEvent.Frontend("/path", serverId), viewModel.navigationEventsFlow.replayCache.first())
    }

    @Test
    fun `Given initial deep link is OpenWearOnboarding when creating viewModel, then navigate to wear onboarding with the server url and wear name`() = runTest {
        createViewModel(LauncherActivity.DeepLink.OpenWearOnboarding("ha_wear", "http://ha"))
        advanceUntilIdle()
        assertEquals(LauncherNavigationEvent.WearOnboarding("ha_wear", "http://ha"), viewModel.navigationEventsFlow.replayCache.first())
    }
}
