package io.homeassistant.companion.android.onboarding.serverdiscovery

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
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
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

private val testHAVersion = HomeAssistantVersion(2025, 1, 1)

@OptIn(ExperimentalCoroutinesApi::class)
class ServerDiscoveryViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(UnconfinedTestDispatcher())

    private lateinit var searcher: HomeAssistantSearcher
    private lateinit var viewModel: ServerDiscoveryViewModel

    private val discoveredInstanceFlow = MutableSharedFlow<HomeAssistantInstance>()

    @BeforeEach
    fun setUp() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true

        searcher = mockk()
        every { searcher.discoveredInstanceFlow() } returns discoveredInstanceFlow
    }

    private fun createViewModel() {
        viewModel = ServerDiscoveryViewModel(searcher)
    }

    @Test
    fun `Given view model created then discoveryFlow initially holds Started`() = runTest {
        createViewModel()
        assertEquals(Started, viewModel.discoveryFlow.value)
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
    fun `Given one server discovered before DELAY_BEFORE_DISPLAY_DISCOVERY when collecting from discoveryFlow then discoveryFlow emits Started then ServerDiscovered after DELAY_BEFORE_DISPLAY_DISCOVERY`() = runTest {
        createViewModel()
        val instance = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            discoveredInstanceFlow.emit(instance)
            assertEquals(Started, expectMostRecentItem()) // ServerDiscovered is delayed
            advanceTimeBy(DELAY_BEFORE_DISPLAY_DISCOVERY)
            runCurrent()
            val discoveredState = expectMostRecentItem()
            assertTrue(discoveredState is ServerDiscovered)

            advanceTimeBy(DELAY_AFTER_FIRST_DISCOVERY)
            runCurrent()
            expectNoEvents()
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

            advanceTimeBy(DELAY_AFTER_FIRST_DISCOVERY)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `Given multiple servers discovered when collecting from discoveryFlow then discoveryFlow emits Started then ServerDiscovered then ServersDiscovered after DELAY_AFTER_FIRST_DISCOVERY`() = runTest {
        createViewModel()
        val instance1 = HomeAssistantInstance("Server 1", URL("http://server1.local:8123"), testHAVersion)
        val instance2 = HomeAssistantInstance("Server 2", URL("http://server2.local:8123"), testHAVersion)

        viewModel.discoveryFlow.test {
            assertEquals(Started, awaitItem())

            discoveredInstanceFlow.emit(instance1)
            runCurrent()

            discoveredInstanceFlow.emit(instance2)
            runCurrent()

            val firstDiscoveredState = awaitItem()
            assertTrue(firstDiscoveredState is ServerDiscovered)
            assertEquals(instance1.name, (firstDiscoveredState as ServerDiscovered).name)

            advanceTimeBy(DELAY_AFTER_FIRST_DISCOVERY)
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

            advanceTimeBy(DELAY_AFTER_FIRST_DISCOVERY)
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
}
