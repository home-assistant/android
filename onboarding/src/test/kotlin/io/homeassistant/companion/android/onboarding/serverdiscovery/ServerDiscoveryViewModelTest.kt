package io.homeassistant.companion.android.onboarding.serverdiscovery

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryMode
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.utils.mockServer
import io.homeassistant.companion.android.utils.testHAVersion
import io.mockk.every
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
private class ServerDiscoveryViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(UnconfinedTestDispatcher())

    private val searcher: HomeAssistantSearcher = mockk()
    private val serverManager: ServerManager = mockk()
    private lateinit var viewModel: ServerDiscoveryViewModel

    private val discoveredInstanceFlow = MutableSharedFlow<HomeAssistantInstance>()

    @BeforeEach
    fun setUp() {
        every { searcher.discoveredInstanceFlow() } returns discoveredInstanceFlow
    }

    private fun createViewModel(discoveryMode: ServerDiscoveryMode = ServerDiscoveryMode.NORMAL) {
        viewModel = ServerDiscoveryViewModel(discoveryMode, searcher, serverManager)
    }

    @Test
    fun `Given view model created then discoveryFlow emits Started after DELAY_BEFORE_DISPLAY_DISCOVERY`() = runTest {
        createViewModel()
        viewModel.discoveryFlow.test {
            expectNoEvents()
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            assertEquals(Started, awaitItem())
        }
    }

    @Test
    fun `Given ADD_EXISTING mode with existing servers when view model created then discoveryFlow emits existing servers after delay`() = runTest {
        val server0 = mockServer("http://ha", haVersion = null, name = "server0")
        val server1 = mockServer("http://ha1.local:8123", name = "server1")
        val server2 = mockServer("http://ha2.local:8123", name = "server2")
        val server3 = mockServer(null, name = "server3")

        every { serverManager.defaultServers } returns listOf(
            server0,
            server1,
            server2,
            server3,
        )
        createViewModel(ServerDiscoveryMode.ADD_EXISTING)

        viewModel.discoveryFlow.test {
            expectNoEvents()
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            runCurrent()

            val discoveredState = awaitItem()
            assertEquals(
                ServersDiscovered(
                    listOf(
                        ServerDiscovered(server1.friendlyName, URL("http://ha1.local:8123"), testHAVersion),
                        ServerDiscovered(server2.friendlyName, URL("http://ha2.local:8123"), testHAVersion),
                    ),
                ),
                discoveredState,
            )
        }
    }

    @Test
    fun `Given ADD_EXISTING mode with no servers when view model created then discoveryFlow emits Started after delay`() = runTest {
        every { serverManager.defaultServers } returns emptyList()

        createViewModel(ServerDiscoveryMode.ADD_EXISTING)

        viewModel.discoveryFlow.test {
            expectNoEvents()
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            runCurrent()
            assertEquals(Started, awaitItem())
        }
    }

    @Test
    fun `Given no servers discovered when collecting from discoveryFlow then discoveryFlow emits Started then NoServerFound after TIMEOUT_NO_SERVER_FOUND`() = runTest {
        createViewModel()

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())
            advanceTimeBy(TIMEOUT_NO_SERVER_FOUND)
            assertEquals(NoServerFound, awaitItem())
        }
    }

    @Test
    fun `Given TIMEOUT_NO_SERVER_FOUND occurs without discoveries when a server is found later then discoveryFlow emits Started then NoServerFound then ServerDiscovered`() = runTest {
        createViewModel()
        val instance = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            advanceTimeBy(TIMEOUT_NO_SERVER_FOUND)

            assertEquals(NoServerFound, awaitItem())

            discoveredInstanceFlow.emit(instance)
            runCurrent()

            val discoveredState = awaitItem()
            assertTrue(discoveredState is ServerDiscovered)
            assertEquals(instance.name, (discoveredState as ServerDiscovered).name)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `Given multiple servers discovered before delay when collecting from discoveryFlow then discoveryFlow emits only ServersDiscovered after delay`() = runTest {
        createViewModel()
        val instance1 = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)
        val instance2 = HomeAssistantInstance("Server 2", URL("http://server2.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            // Both instances discovered before delay
            discoveredInstanceFlow.emit(instance1)
            runCurrent()
            discoveredInstanceFlow.emit(instance2)
            runCurrent()

            // delayFirstThrottle only emits the latest state after delay
            expectNoEvents()
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            runCurrent()

            val discoveredState = awaitItem()
            assertTrue(discoveredState is ServersDiscovered)
            assertEquals(2, (discoveredState as ServersDiscovered).servers.size)
            assertEquals(instance1.name, discoveredState.servers[0].name)
            assertEquals(instance2.name, discoveredState.servers[1].name)

            expectNoEvents()
        }
    }

    @Test
    fun `Given servers discovered after delay when collecting from discoveryFlow then discoveryFlow emits Started then ServerDiscovered then ServersDiscovered`() = runTest {
        createViewModel()
        val instance1 = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)
        val instance2 = HomeAssistantInstance("Server 2", URL("http://server2.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            // First instance discovered after delay
            discoveredInstanceFlow.emit(instance1)
            runCurrent()

            val firstDiscoveredState = awaitItem()
            assertTrue(firstDiscoveredState is ServerDiscovered)
            assertEquals(instance1.name, (firstDiscoveredState as ServerDiscovered).name)
            runCurrent()

            // Second instance discovered well after the first
            discoveredInstanceFlow.emit(instance2)
            runCurrent()

            val secondDiscoveredState = awaitItem()
            assertTrue(secondDiscoveredState is ServersDiscovered)
            assertEquals(2, (secondDiscoveredState as ServersDiscovered).servers.size)
            assertEquals(instance1.name, secondDiscoveredState.servers[0].name)
            assertEquals(instance2.name, secondDiscoveredState.servers[1].name)
        }
    }

    @Test
    fun `Given multiple servers discovered multiple times when collecting from discoveryFlow then discoveryFlow emits ServersDiscovered without duplicates and only once`() = runTest {
        createViewModel()
        val instance1 = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)
        val instance2 = HomeAssistantInstance("Server 2", URL("http://server2.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            discoveredInstanceFlow.emit(instance1)
            runCurrent()

            discoveredInstanceFlow.emit(instance2)
            runCurrent()

            // First item
            awaitItem()
            runCurrent()

            // All the servers
            awaitItem()

            discoveredInstanceFlow.emit(instance1)
            runCurrent()

            discoveredInstanceFlow.emit(instance2)
            runCurrent()

            // Not getting the duplicates
            expectNoEvents()
        }
    }

    @Test
    fun `Given discoveryFlow emitted Started then ServerDiscovered when onDismissOneServerFound is invoked then discoveryFlow emits ServersDiscovered`() = runTest {
        createViewModel()
        val instance = HomeAssistantInstance("Test Server", URL("http://test.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            discoveredInstanceFlow.emit(instance)
            runCurrent()

            val discoveredState = awaitItem()
            assertTrue(discoveredState is ServerDiscovered)

            viewModel.onDismissOneServerFound()
            runCurrent()

            val dismissedState = awaitItem()
            assertTrue(dismissedState is ServersDiscovered)
            assertEquals(1, (dismissedState as ServersDiscovered).servers.size)
            assertEquals(instance.name, dismissedState.servers[0].name)
        }
    }

    @Test
    fun `Given discovery process throws an exception when collecting from discoveryFlow then discoveryFlow emits Started then NoServerFound after TIMEOUT_NO_SERVER_FOUND`() = runTest {
        every { searcher.discoveredInstanceFlow() } returns flow {
            throw DiscoveryFailedException("Test Exception")
        }
        createViewModel()

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            advanceTimeBy(TIMEOUT_NO_SERVER_FOUND)
            runCurrent()

            assertEquals(NoServerFound, awaitItem())
        }
    }

    @Test
    fun `Given HIDE_EXISTING mode with existing server when discovered server matches existing then discoveryFlow filters out matching server`() = runTest {
        val existingServerUrl = "http://server1.local:8123"
        every { serverManager.defaultServers } returns listOf(mockServer(existingServerUrl, name = "Existing Server"), mockServer(url = null, name = "Broken server"))

        createViewModel(ServerDiscoveryMode.HIDE_EXISTING)

        val matchingInstance = HomeAssistantInstance("Server 1", URL(existingServerUrl), testHAVersion)
        val differentInstance = HomeAssistantInstance("Server 2", URL("http://server2.local:8123"), testHAVersion)
        val differentProtocolInstance = HomeAssistantInstance("Server HTTPS", URL(existingServerUrl.replace("http", "https")), testHAVersion)
        val differentPortInstance = HomeAssistantInstance("Server 8124", URL(existingServerUrl.replace("8123", "8124")), testHAVersion)

        viewModel.discoveryFlow.test {
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            assertEquals(Started, awaitItem())

            discoveredInstanceFlow.emit(matchingInstance)
            runCurrent()

            discoveredInstanceFlow.emit(differentInstance)
            runCurrent()

            discoveredInstanceFlow.emit(differentProtocolInstance)
            runCurrent()

            discoveredInstanceFlow.emit(differentPortInstance)
            runCurrent()

            awaitItem()
            awaitItem()
            // Only the different instances should appear
            val discoveredState = awaitItem()
            assertEquals(
                ServersDiscovered(
                    listOf(
                        ServerDiscovered(differentInstance.name, differentInstance.url, testHAVersion),
                        ServerDiscovered(differentProtocolInstance.name, differentProtocolInstance.url, testHAVersion),
                        ServerDiscovered(differentPortInstance.name, differentPortInstance.url, testHAVersion),
                    ),
                ),
                discoveredState,
            )
            expectNoEvents()
        }
    }
}
