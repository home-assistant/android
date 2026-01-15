package io.homeassistant.companion.android.launch

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.homeassistant.companion.android.automotive.navigation.AutomotiveRoute
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class LaunchViewModelTest {
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val networkStatusMonitor: NetworkStatusMonitor = mockk(relaxed = true)

    private val workManager: WorkManager = mockk()

    private lateinit var viewModel: LaunchViewModel

    private fun createViewModel(
        initialDeepLink: LaunchActivity.DeepLink? = null,
        hasLocationTrackingSupport: Boolean = false,
        isAutomotive: Boolean = false,
        isFullFlavor: Boolean = true,
    ) {
        viewModel = LaunchViewModel(
            initialDeepLink,
            workManager,
            serverManager,
            networkStatusMonitor,
            hasLocationTrackingSupport,
            isAutomotive,
            isFullFlavor,
        )
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
        coEvery { serverManager.servers() } returns listOf(activeServer, notActiveServer)
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(state)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(FrontendRoute(null, ServerManager.SERVER_ID_ACTIVE)),
            viewModel.uiState.value,
        )
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

        assertEquals(LaunchUiState.Loading, viewModel.uiState.value)
        assertEquals(1, networkStateFlow.subscriptionCount.value)
    }

    @Test
    fun `Given active server connected and registered, when network is UNAVAILABLE, then show network unavailable but continue observing network changes`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.UNAVAILABLE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(LaunchUiState.NetworkUnavailable, viewModel.uiState.value)
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
        assertEquals(LaunchUiState.Loading, viewModel.uiState.value)
        assertEquals(1, networkStateFlow.subscriptionCount.value)

        networkStateFlow.emit(NetworkState.READY_REMOTE)
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(FrontendRoute(null, ServerManager.SERVER_ID_ACTIVE)),
            viewModel.uiState.value,
        )
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

        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = false,
                    urlToOnboard = null,
                    hideExistingServers = false,
                    skipWelcome = false,
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given server not registered, when creating viewModel, then navigate to onboarding`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns false

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = false,
                    urlToOnboard = null,
                    hideExistingServers = false,
                    skipWelcome = false,
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given session not connected, when creating viewModel, then navigate to onboarding`() = runTest {
        val server = mockk<Server>(relaxed = true)
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.ANONYMOUS

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = false,
                    urlToOnboard = null,
                    hideExistingServers = false,
                    skipWelcome = false,
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given IllegalArgumentException thrown while getting server, when creating viewModel, then navigate to onboarding`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } throws IllegalArgumentException("Wrong server")
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.ANONYMOUS

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = false,
                    urlToOnboard = null,
                    hideExistingServers = false,
                    skipWelcome = false,
                ),
            ),
            viewModel.uiState.value,
        )
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
        coEvery { serverManager.servers() } returns listOf(invalidServer, connectedServer)
        coEvery { serverManager.authenticationRepository(invalidServer.id).getSessionState() } returns SessionState.ANONYMOUS
        coEvery { serverManager.authenticationRepository(connectedServer.id).getSessionState() } returns SessionState.CONNECTED

        createViewModel()
        advanceUntilIdle()

        coVerify { serverManager.removeServer(1) }
        coVerify(exactly = 1) { serverManager.removeServer(any()) }
    }

    @Test
    fun `Given Loading state, when calling shouldShowSplashScreen, then return true`() = runTest {
        createViewModel()

        assertTrue(viewModel.shouldShowSplashScreen())
    }

    @Test
    fun `Given Ready state, when calling shouldShowSplashScreen, then return false`() = runTest {
        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns null

        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LaunchUiState.Ready)
        assertFalse(viewModel.shouldShowSplashScreen())
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
            initialDeepLink = LaunchActivity.DeepLink.OpenOnboarding(
                "http://homeassistant.io",
                hideExistingServers = hideExistingServers,
                skipWelcome = skipWelcome,
            ),
            hasLocationTrackingSupport = hasLocationTrackingSupport,
        )
        advanceUntilIdle()
        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = hasLocationTrackingSupport,
                    urlToOnboard = "http://homeassistant.io",
                    hideExistingServers = hideExistingServers,
                    skipWelcome = skipWelcome,
                ),
            ),
            viewModel.uiState.value,
        )
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

        createViewModel(LaunchActivity.DeepLink.NavigateTo("/path", serverId))
        advanceUntilIdle()
        assertEquals(
            LaunchUiState.Ready(FrontendRoute("/path", serverId)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given initial deep link is OpenWearOnboarding and full flavor, when creating viewModel, then navigate to wear onboarding`() = runTest {
        createViewModel(
            initialDeepLink = LaunchActivity.DeepLink.OpenWearOnboarding("ha_wear", "http://ha"),
            hasLocationTrackingSupport = true,
        )
        advanceUntilIdle()
        assertEquals(
            LaunchUiState.Ready(WearOnboardingRoute("ha_wear", "http://ha")),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given initial deep link is OpenWearOnboarding and minimal flavor, when creating viewModel, then show wear unsupported`() = runTest {
        createViewModel(
            initialDeepLink = LaunchActivity.DeepLink.OpenWearOnboarding("ha_wear", "http://ha"),
            hasLocationTrackingSupport = false,
        )
        advanceUntilIdle()
        assertEquals(
            LaunchUiState.WearUnsupported,
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given isAutomotive is true, when network is READY, then navigate to automotive route`() = runTest {
        val server = mockk<Server>(relaxed = true)

        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_REMOTE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel(isAutomotive = true)
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(AutomotiveRoute),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given isAutomotive is true but isFullFlavor is false, when network is READY, then navigate to frontend route`() = runTest {
        val server = mockk<Server>(relaxed = true)

        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_REMOTE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel(isAutomotive = true, isFullFlavor = false)
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(FrontendRoute(null, ServerManager.SERVER_ID_ACTIVE)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given network is UNAVAILABLE then READY, when observing network, then navigate to frontend after recovery`() = runTest {
        val server = mockk<Server>(relaxed = true)

        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.UNAVAILABLE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel()
        advanceUntilIdle()

        assertEquals(LaunchUiState.NetworkUnavailable, viewModel.uiState.value)
        assertEquals(1, networkStateFlow.subscriptionCount.value)

        networkStateFlow.emit(NetworkState.READY_LOCAL)
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(FrontendRoute(null, ServerManager.SERVER_ID_ACTIVE)),
            viewModel.uiState.value,
        )
        assertEquals(0, networkStateFlow.subscriptionCount.value)

        verify(exactly = 1) {
            workManager.enqueue(any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `Given initial deep link is OpenOnboarding with null url, when creating viewModel, then navigate to onboarding without url`() = runTest {
        createViewModel(
            initialDeepLink = LaunchActivity.DeepLink.OpenOnboarding(
                urlToOnboard = null,
                hideExistingServers = false,
                skipWelcome = false,
            ),
            hasLocationTrackingSupport = true,
        )
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(
                OnboardingRoute(
                    hasLocationTracking = true,
                    urlToOnboard = null,
                    hideExistingServers = false,
                    skipWelcome = false,
                ),
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given valid connected servers, when cleaning up servers, then do not remove connected servers`() = runTest {
        val connectedServer1 = Server(
            id = 1,
            _name = "Connected Server 1",
            connection = ServerConnectionInfo(externalUrl = "http://server1.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(id = null, name = null, isOwner = false, isAdmin = false),
        )
        val connectedServer2 = Server(
            id = 2,
            _name = "Connected Server 2",
            connection = ServerConnectionInfo(externalUrl = "http://server2.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(id = null, name = null, isOwner = false, isAdmin = false),
        )

        coEvery { serverManager.servers() } returns listOf(connectedServer1, connectedServer2)
        coEvery { serverManager.authenticationRepository(connectedServer1.id).getSessionState() } returns SessionState.CONNECTED
        coEvery { serverManager.authenticationRepository(connectedServer2.id).getSessionState() } returns SessionState.CONNECTED

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { serverManager.removeServer(any()) }
    }

    @Test
    fun `Given initial deep link is NavigateTo with null path, when server is connected, then navigate to frontend without path`() = runTest {
        val serverId = 5
        val server = mockk<Server>(relaxed = true)
        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk()

        coEvery { serverManager.getServer(serverId) } returns server
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.authenticationRepository().getSessionState() } returns SessionState.CONNECTED
        val networkStateFlow = MutableStateFlow(NetworkState.READY_REMOTE)
        coEvery { networkStatusMonitor.observeNetworkStatus(any()) } returns networkStateFlow

        createViewModel(LaunchActivity.DeepLink.NavigateTo(path = null, serverId = serverId))
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(FrontendRoute(null, serverId)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `Given initial deep link is OpenWearOnboarding with null url, when full flavor, then navigate to wear onboarding without url`() = runTest {
        createViewModel(
            initialDeepLink = LaunchActivity.DeepLink.OpenWearOnboarding(wearName = "Pixel Watch", urlToOnboard = null),
            hasLocationTrackingSupport = true,
        )
        advanceUntilIdle()

        assertEquals(
            LaunchUiState.Ready(WearOnboardingRoute(wearName = "Pixel Watch", urlToOnboard = null)),
            viewModel.uiState.value,
        )
    }
}
