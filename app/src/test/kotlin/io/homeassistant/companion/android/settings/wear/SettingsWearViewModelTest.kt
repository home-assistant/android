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
    fun `Given viewModelInitialized when wearNodesWithApp then emits emptySet`() = runTest {
        assertEquals(emptySet<Node>(), viewModel.wearNodesWithApp.value)
    }

    @Test
    fun `Given viewModelInitialized when allConnectedNodes then emits emptyList`() = runTest {
        assertEquals(emptyList<Node>(), viewModel.allConnectedNodes.value)
    }

    @Test
    fun `Given viewModelInitialized ensure UI State is updated to initial state`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            // Initial State, This is from a combined flow
            val initialState = uiState.awaitItem()
            assertEquals(commonR.string.message_no_connected_nodes, initialState.infoTextResourceId)
            assertEquals(true, initialState.shouldShowRemoteInstallButton)
            assertEquals(false, initialState.installedOnDevices)
        }
    }

    @Test
    fun `Given viewModelInitialized and findWearDevicesWithApp() is called, ensure wearNodesWithApp Flow is updated with new nodes`() = runTest {
        assertTrue(viewModel.wearNodesWithApp.value.isEmpty())
        viewModel.findWearDevicesWithApp(capabilityClient)
        assertTrue(viewModel.wearNodesWithApp.value.isNotEmpty())
    }

    @Test
    fun `Given viewModelInitialized and findAllWearDevices() is called, ensure wearNodesWithApp Flow is updated with new nodes`() = runTest {
        assertTrue(viewModel.allConnectedNodes.value.isEmpty())
        viewModel.findAllWearDevices(nodeClient)
        assertTrue(viewModel.allConnectedNodes.value.isNotEmpty())
    }

    @Test
    fun `when findAllWearDevices() is called ensure UI state infoTextResourceId is updated with the correct to the correct resource id`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            viewModel.findAllWearDevices(nodeClient)
            val newState = uiState.expectMostRecentItem()
            assertEquals(commonR.string.message_missing_all, newState.infoTextResourceId)
            assertEquals(true, newState.shouldShowRemoteInstallButton)
            assertEquals(false, newState.installedOnDevices)
        }
    }

    @Test
    fun `when findAllWearDevices() & findWearDevicesWithApp() and app is installed on all wear devices, ensure UI state installed on devices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            viewModel.findAllWearDevices(nodeClient)
            viewModel.findWearDevicesWithApp(capabilityClient)
            val newState = uiState.expectMostRecentItem()
            assertEquals(true, newState.installedOnDevices)
        }
    }

    @Test
    fun `when findAllWearDevices() & findWearDevicesWithApp() and app is installed on some wear devices, ensure UI State installedOnDevices is true`() = runTest {
        turbineScope {
            val uiState = viewModel.settingsWearOnboardingViewUiState.testIn(backgroundScope)
            nodeClient.setNodes(listOf("123", "1234"))
            viewModel.findAllWearDevices(nodeClient)
            viewModel.findWearDevicesWithApp(capabilityClient)
            val newState = uiState.expectMostRecentItem()
            assertEquals(true, newState.installedOnDevices)
        }
    }

    companion object {
        private const val CAPABILITY_WEAR_APP = "verify_wear_app"
    }
}
