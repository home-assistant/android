package io.homeassistant.companion.android.settings.wear

import app.cash.turbine.turbineScope
import com.google.android.gms.wearable.Node
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.fakes.FakeCapabilityClient
import io.homeassistant.companion.android.fakes.FakeNodeClient
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWearViewModelTest {

    private val serverManager: ServerManager = mockk()
    private lateinit var viewModel: SettingsWearViewModel

    private lateinit var nodeClient: FakeNodeClient
    private lateinit var capabilityClient: FakeCapabilityClient

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
        val context = RuntimeEnvironment.getApplication()
        capabilityClient = FakeCapabilityClient(context)
        capabilityClient.capabilities[CAPABILITY_WEAR_APP] = setOf("1234")
        nodeClient = FakeNodeClient(context)
        nodeClient.setNodes(listOf("1234"))
        viewModel = SettingsWearViewModel(serverManager, context)
    }

    @Test
    fun `given viewModelInitialized when collecting wearNodesWithApp then emits emptySet`() {
        assertEquals(emptySet<Node>(), viewModel.wearNodesWithApp.value)
    }

    @Test
    fun `given viewModelInitialized when collecting allConnectedNodes then emits emptyList`() {
        assertEquals(emptyList<Node>(), viewModel.allConnectedNodes.value)
    }

    @Test
    fun `Given when the view model is initialized, when settingsWearOnboardingViewUiState is created, ensure the initial state is correct`() = runTest {
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
    fun `Given wear nodes with the companion app are present, when findAllWearDevicesWithApp() is called, then ensure wearNodesWithApp flow is updated with those nodes`() = runTest {
        capabilityClient.setNodes(CAPABILITY_WEAR_APP, setOf("1234"))

        val expectedNodes = capabilityClient.nodes[CAPABILITY_WEAR_APP]
        viewModel.findWearDevicesWithApp(capabilityClient)

        assertEquals(expectedNodes, viewModel.wearNodesWithApp.value)
    }

    @Test
    fun `Given wear nodes are present, when findAllWearDevices() is called, then ensure allConnectedNodes flow is updated with those nodes`() = runTest {
        val expectedNodes: List<Node?>? = nodeClient.connectedNodes.result
        viewModel.findAllWearDevices(nodeClient)

        assertEquals(expectedNodes, viewModel.allConnectedNodes.value)
    }

    @Test
    fun `Given wear nodes are present without the companion app installed, when findAllWearDevices() is called, then ensure UI State Flow infoTextResourceId is updated to message_missing_all`() = runTest {
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
    fun `Given all wear nodes have the companion app installed when findAllWearDevices() & findWearDevicesWithApp() are called then installedOnDevices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            viewModel.findAllWearDevices(nodeClient)
            viewModel.findWearDevicesWithApp(capabilityClient)
            val newState = uiState.expectMostRecentItem()
            assertTrue(newState.installedOnDevices)
        }
    }

    @Test
    fun `Given some wear nodes have the companion app installed when findAllWearDevices() & findWearDevicesWithApp() are called then installedOnDevices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            nodeClient.setNodes(listOf("123", "1234"))

            viewModel.findAllWearDevices(nodeClient)
            val expectedNodesWithoutApp = nodeClient.connectedNodes.result
            assertEquals(expectedNodesWithoutApp, viewModel.allConnectedNodes.value)

            capabilityClient.setNodes(CAPABILITY_WEAR_APP, setOf("1234"))
            val expectedNodesWithApp = capabilityClient.nodes[CAPABILITY_WEAR_APP]
            viewModel.findWearDevicesWithApp(capabilityClient)
            assertEquals(expectedNodesWithApp, viewModel.wearNodesWithApp.value)

            val newState = uiState.expectMostRecentItem()
            assertTrue(newState.installedOnDevices)
        }
    }

    companion object {
        private const val CAPABILITY_WEAR_APP = "verify_wear_app"
    }
}
