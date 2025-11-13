package io.homeassistant.companion.android.settings.wear

import android.app.Application
import app.cash.turbine.turbineScope
import com.google.android.gms.wearable.Node
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.fakes.FakeCapabilityClient
import io.homeassistant.companion.android.fakes.FakeNodeClient
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val CAPABILITY_WEAR_APP = "verify_wear_app"

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class SettingsWearViewModelTest {

    private val serverManager: ServerManager = mockk()
    private lateinit var viewModel: SettingsWearViewModel

    private lateinit var nodeClient: FakeNodeClient
    private lateinit var capabilityClient: FakeCapabilityClient

    @BeforeEach
    fun setup() {
        val application = mockk<Application> {
            every { applicationContext } returns this
            every { packageManager } returns mockk()
        }
        capabilityClient = FakeCapabilityClient(application)
        capabilityClient.capabilities[CAPABILITY_WEAR_APP] = setOf("1234")
        nodeClient = FakeNodeClient(application)
        nodeClient.setNodes(listOf("1234"))
        viewModel = SettingsWearViewModel(serverManager, application)
    }

    @Test
    fun `Given viewModel initialized when collecting wearNodesWithApp then emits empty set`() {
        assertEquals(emptySet<Node>(), viewModel.wearNodesWithApp.value)
    }

    @Test
    fun `Given viewModel initialized when collecting allConnectedNodes then emits empty list`() {
        assertEquals(emptyList<Node>(), viewModel.allConnectedNodes.value)
    }

    @Test
    fun `Given viewModel initialized when collecting settingsWearOnboardingViewUiState then initial state is correct`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            // Initial State, This is from a combined flow
            val initialState = uiState.awaitItem()
            assertEquals(commonR.string.message_no_connected_nodes, initialState.infoTextResourceId)
            assertTrue(initialState.shouldShowRemoteInstallButton)
            assertFalse(initialState.installedOnDevices)
        }
    }

    @Test
    fun `Given wear nodes with app present when findWearDevicesWithApp called then wearNodesWithApp flow emits these nodes`() = runTest {
        capabilityClient.capabilities[CAPABILITY_WEAR_APP] = setOf("1234", "567")

        val expectedNodes = capabilityClient.getNodes(CAPABILITY_WEAR_APP)
        viewModel.findWearDevicesWithApp(capabilityClient)

        assertEquals(expectedNodes, viewModel.wearNodesWithApp.value)
    }

    @Test
    fun `Given wear nodes present when findAllWearDevices called then allConnectedNodes flow emits these nodes`() = runTest {
        nodeClient.setNodes(listOf("1", "2", "3"))

        val expectedNodes: List<Node?>? = nodeClient.connectedNodes.result
        // Verify that the test is properly setup
        assertEquals(3, expectedNodes?.size)
        viewModel.findAllWearDevices(nodeClient)

        assertEquals(expectedNodes, viewModel.allConnectedNodes.value)
    }

    @Test
    fun `Given wear nodes without app when findAllWearDevices called then UI state shows message_missing_all`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            viewModel.findAllWearDevices(nodeClient)

            val nodes = viewModel.allConnectedNodes.value
            assertEquals(nodes, nodeClient.connectedNodes.result)

            val newState = uiState.expectMostRecentItem()
            assertEquals(commonR.string.message_missing_all, newState.infoTextResourceId)
            assertTrue(newState.shouldShowRemoteInstallButton)
            assertFalse(newState.installedOnDevices)
        }
    }

    @Test
    fun `Given all wear nodes have app installed when findAllWearDevices and findWearDevicesWithApp called then installedOnDevices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            viewModel.findAllWearDevices(nodeClient)
            viewModel.findWearDevicesWithApp(capabilityClient)
            val newState = uiState.expectMostRecentItem()
            assertTrue(newState.installedOnDevices)
        }
    }

    @Test
    fun `Given some wear nodes have app installed when findAllWearDevices and findWearDevicesWithApp called then installedOnDevices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            nodeClient.setNodes(listOf("123", "1234"))

            viewModel.findAllWearDevices(nodeClient)
            val expectedNodesWithoutApp = nodeClient.connectedNodes.result
            assertEquals(expectedNodesWithoutApp, viewModel.allConnectedNodes.value)

            capabilityClient.capabilities[CAPABILITY_WEAR_APP] = setOf("1234")
            val expectedNodesWithApp = capabilityClient.getNodes(CAPABILITY_WEAR_APP)
            viewModel.findWearDevicesWithApp(capabilityClient)
            assertEquals(expectedNodesWithApp, viewModel.wearNodesWithApp.value)

            val newState = uiState.expectMostRecentItem()
            assertTrue(newState.installedOnDevices)
        }
    }
}
