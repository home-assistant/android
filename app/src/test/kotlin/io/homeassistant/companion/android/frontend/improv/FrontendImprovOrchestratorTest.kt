package io.homeassistant.companion.android.frontend.improv

import app.cash.turbine.test
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDeviceSetupDoneMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDiscoveredDeviceMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendImprovOrchestratorTest {

    /** Backing flow returned by `scanDevices()` — test drives it by emitting. */
    private val scanFlow = MutableStateFlow<List<ImprovDevice>>(emptyList())

    /** Backing flow returned by `provisionDevice()` — test drives by emitting ProvisioningEvents. */
    private val provisionFlow = MutableSharedFlow<ProvisioningEvent>(extraBufferCapacity = 16)

    private val improvRepository: ImprovRepository = mockk(relaxed = true) {
        every { scanDevices() } returns scanFlow
        every { provisionDevice(any(), any(), any()) } returns provisionFlow
        every { hasPermissions() } returns true
        every { requiredPermissions } returns listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_FINE_LOCATION",
        )
    }
    private val bluetoothCapabilities = BluetoothCapabilities { true }
    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true) {
        coEvery { checkImprovPermissions(any()) } returns true
    }
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val wifiHelper: WifiHelper = mockk(relaxed = true) {
        every { getWifiSsid() } returns "\"My SSID\""
    }

    private fun createOrchestrator() = FrontendImprovOrchestrator(
        improvRepository = improvRepository,
        bluetoothCapabilities = bluetoothCapabilities,
        externalBusRepository = externalBusRepository,
        permissionManager = permissionManager,
        serverManager = serverManager,
        wifiHelper = wifiHelper,
    )

    /**
     * Drives the orchestrator to a ConfiguringDevice variant whose deviceAddress has been
     * resolved by an in-flight scan, the realistic precondition for [FrontendImprovOrchestrator.onConnectDevice].
     * Advances the test scheduler at each step so the launched scan collector subscribes before
     * we emit, and so the emission is processed before the helper returns. Returns the scan
     * collector [Job] so callers can cancel it at the end of the test — the underlying
     * `collectLatest` on `_scanRequested` never completes on its own.
     */
    private suspend fun TestScope.configureWithResolvedAddress(
        orchestrator: FrontendImprovOrchestrator,
        deviceName: String = "Smart Plug",
        deviceAddress: String = "AA:BB",
    ): Job {
        orchestrator.onStartImprovScan()
        val scanJob = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        orchestrator.onConfigureImprovDevice(deviceName = deviceName)
        scanFlow.emit(listOf(ImprovDevice(deviceName, deviceAddress)))
        advanceUntilIdle()
        return scanJob
    }

    @Test
    fun `Given device without BLE when onStartImprovScan then scanRequested stays false`() = runTest {
        val orchestrator = FrontendImprovOrchestrator(
            improvRepository = improvRepository,
            bluetoothCapabilities = { false },
            externalBusRepository = externalBusRepository,
            permissionManager = permissionManager,
            serverManager = serverManager,
            wifiHelper = wifiHelper,
        )

        orchestrator.onStartImprovScan()
        advanceUntilIdle()

        assertFalse(orchestrator.scanRequested.value)
    }

    @Test
    fun `Given permissions granted when onStartImprovScan then scanRequested flips true`() = runTest {
        val orchestrator = createOrchestrator()

        orchestrator.onStartImprovScan()

        assertTrue(orchestrator.scanRequested.value)
    }

    @Test
    fun `Given scanRequested true when scan emits then device name is forwarded to frontend`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()

        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        advanceUntilIdle()

        val slot = slot<OutgoingExternalBusMessage>()
        coVerify { externalBusRepository.send(capture(slot)) }
        assertEquals(slot.captured, ImprovDiscoveredDeviceMessage("Smart Plug"))
        job.cancel()
    }

    @Test
    fun `Given scanRequested false when processImprovScanRequests then nothing is sent to frontend`() = runTest {
        val orchestrator = createOrchestrator()

        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        // This won't ever happen in reality, but it is the only way to test that we are not subscribing to scanDevices
        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        advanceUntilIdle()

        coVerify(exactly = 0) { externalBusRepository.send(any()) }
        job.cancel()
    }

    @Test
    fun `Given missing permissions when onStartImprovScan then delegates to permissionManager`() = runTest {
        every { improvRepository.hasPermissions() } returns false
        val expectedPermissions = improvRepository.requiredPermissions

        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        advanceUntilIdle()

        coVerify { permissionManager.checkImprovPermissions(expectedPermissions) }
    }

    @Test
    fun `Given permissionManager denies improv permissions when onStartImprovScan then scanRequested stays false`() = runTest {
        every { improvRepository.hasPermissions() } returns false
        coEvery { permissionManager.checkImprovPermissions(any()) } returns false

        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        advanceUntilIdle()

        assertFalse(orchestrator.scanRequested.value)
    }

    @Test
    fun `Given scan is running when devices are discovered then forwards each new name once`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()

        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB"), ImprovDevice("Lamp", "CC:DD")))
        advanceUntilIdle()

        coVerify(exactly = 2) { externalBusRepository.send(any<OutgoingExternalBusMessage>()) }

        job.cancel()
    }

    @Test
    fun `Given scan collector cancelled and resubscribed while session still active then previously sent device names are not re-sent`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()

        // First lifecycle-bound collect — simulates the foreground RESUMED effect.
        val firstJob = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        advanceUntilIdle()
        coVerify(exactly = 1) { externalBusRepository.send(any<OutgoingExternalBusMessage>()) }

        // Simulate app backgrounding — lifecycle cancels processImprovScanRequests. The session
        // is still active (no onDismissed, _scanRequested stays true).
        firstJob.cancel()

        // Foreground again: the lifecycle effect re-runs processImprovScanRequests. The repository's
        // scanFlow still holds the previously discovered device (replay semantics), so without
        // session-level dedup the orchestrator re-sends it to the frontend.
        val secondJob = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()

        coVerify(exactly = 1) { externalBusRepository.send(any<OutgoingExternalBusMessage>()) }
        secondJob.cancel()
    }

    @Test
    fun `Given onConfigureImprovDevice when handled then uiState is initialised to SearchingDevice`() = runTest {
        val orchestrator = createOrchestrator()

        orchestrator.uiState.test {
            assertNull(awaitItem())
            orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")
            advanceUntilIdle()
            val state = assertInstanceOf(ImprovUIState.SearchingDevice::class.java, awaitItem())
            assertEquals("Smart Plug", state.deviceName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given scan emits matching device when SearchingDevice then state promotes to ConfiguringDevice`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")

        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        advanceUntilIdle()

        val state = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.ConfiguringDevice::class.java, state)
        val configuring = state as ImprovUIState.ConfiguringDevice
        assertEquals("Smart Plug", configuring.deviceName)
        assertEquals("AA:BB", configuring.deviceAddress)
        job.cancel()
    }

    @Test
    fun `Given device already discovered before onConfigureImprovDevice then state initialises directly to ConfiguringDevice`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        scanFlow.emit(listOf(ImprovDevice("Smart Plug", "AA:BB")))
        advanceUntilIdle()

        orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")

        val state = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.ConfiguringDevice::class.java, state)
        val configuring = state as ImprovUIState.ConfiguringDevice
        assertEquals("Smart Plug", configuring.deviceName)
        assertEquals("AA:BB", configuring.deviceAddress)
        job.cancel()
    }

    @Test
    fun `Given onConnectDevice and PROVISIONED event then sends device_setup_done`() = runTest {
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())

        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()

        provisionFlow.emit(ProvisioningEvent.StateChanged(DeviceState.PROVISIONING))
        provisionFlow.emit(ProvisioningEvent.Provisioned(domain = "acme"))
        advanceUntilIdle()

        coVerify(exactly = 1) { externalBusRepository.send(ImprovDeviceSetupDoneMessage) }
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given Errored state when late Provisioned event arrives then device_setup_done is not sent`() = runTest {
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())
        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()

        // First an error transitions UI to Errored.
        provisionFlow.emit(ProvisioningEvent.ErrorOccurred(ErrorState.UNABLE_TO_CONNECT))
        advanceUntilIdle()
        assertInstanceOf(ImprovUIState.Errored::class.java, orchestrator.uiState.value)

        // Then a late Provisioned event from the BLE stack. The UI must stay Errored AND no
        // setup-done message should be forwarded — otherwise the frontend would think setup
        // succeeded while the sheet still shows the error.
        provisionFlow.emit(ProvisioningEvent.Provisioned(domain = "acme"))
        advanceUntilIdle()

        assertInstanceOf(ImprovUIState.Errored::class.java, orchestrator.uiState.value)
        coVerify(exactly = 0) { externalBusRepository.send(ImprovDeviceSetupDoneMessage) }
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given onConnectDevice while still SearchingDevice then provisionDevice is not called`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")

        orchestrator.onConnectDevice(scope = backgroundScope, ssid = "wifi", password = "pwd")

        verify(exactly = 0) { improvRepository.provisionDevice(any(), any(), any()) }
    }

    @Test
    fun `Given provisioning emits state changes then uiState mirrors them`() = runTest {
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())
        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()

        provisionFlow.emit(ProvisioningEvent.StateChanged(DeviceState.AUTHORIZATION_REQUIRED))
        advanceUntilIdle()
        val provisioning = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.Provisioning::class.java, provisioning)
        assertEquals(DeviceState.AUTHORIZATION_REQUIRED, (provisioning as ImprovUIState.Provisioning).state)

        provisionFlow.emit(ProvisioningEvent.ErrorOccurred(ErrorState.UNABLE_TO_CONNECT))
        advanceUntilIdle()
        val errored = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.Errored::class.java, errored)
        val erroredState = errored as ImprovUIState.Errored
        assertEquals(ErrorState.UNABLE_TO_CONNECT, erroredState.error)
        assertEquals("Smart Plug", erroredState.deviceName)
        assertEquals("AA:BB", erroredState.deviceAddress)
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given onDismissed without provisioned domain then clears uiState`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")
        advanceUntilIdle()

        orchestrator.onDismissed(serverId = 1)
        advanceUntilIdle()

        assertNull(orchestrator.uiState.value)
    }

    @Test
    fun `Given onDismissed with provisioned domain on new HA then sends NavigateToMessage`() = runTest {
        coEvery { serverManager.getServer(1) } returns mockk(relaxed = true) {
            every { version } returns HomeAssistantVersion(year = 2025, month = 6, release = 0)
        }
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())
        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()
        provisionFlow.emit(ProvisioningEvent.Provisioned(domain = "acme"))
        advanceUntilIdle()

        val captured = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(captured)) } returns Unit

        orchestrator.onDismissed(serverId = 1)

        assertInstanceOf(OutgoingExternalBusMessage::class.java, captured.captured)
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given onDismissed with provisioned domain on old HA then emits ReloadAtPath event`() = runTest {
        coEvery { serverManager.getServer(1) } returns mockk(relaxed = true) {
            every { version } returns HomeAssistantVersion(year = 2025, month = 5, release = 0)
        }
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())
        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()
        provisionFlow.emit(ProvisioningEvent.Provisioned(domain = "acme"))
        advanceUntilIdle()

        orchestrator.events.test {
            orchestrator.onDismissed(serverId = 1)

            val event = awaitItem()
            assertInstanceOf(FrontendImprovOrchestrator.Event.ReloadAtPath::class.java, event)
            val reload = event as FrontendImprovOrchestrator.Event.ReloadAtPath
            assertEquals("/_my_redirect/config_flow_start?domain=acme", reload.path)
            assertEquals(1, reload.serverId)
            cancelAndIgnoreRemainingEvents()
        }
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given active scan when onDismissed then scanRequested flips false`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onStartImprovScan()
        val job = launch { orchestrator.processImprovScanRequests() }
        advanceUntilIdle()
        assertEquals(true, orchestrator.scanRequested.value)

        orchestrator.onDismissed(serverId = 1)

        assertEquals(false, orchestrator.scanRequested.value)
        job.cancel()
    }

    @Test
    fun `Given onRestart from Errored then reverts to ConfiguringDevice with same device`() = runTest {
        val orchestrator = createOrchestrator()
        val scanJob = configureWithResolvedAddress(orchestrator)
        val provisioningScope = CoroutineScope(coroutineContext + Job())
        orchestrator.onConnectDevice(scope = provisioningScope, ssid = "wifi", password = "pwd")
        advanceUntilIdle()
        provisionFlow.emit(ProvisioningEvent.ErrorOccurred(ErrorState.UNABLE_TO_CONNECT))
        advanceUntilIdle()

        orchestrator.onRestart()

        val state = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.ConfiguringDevice::class.java, state)
        val configuring = state as ImprovUIState.ConfiguringDevice
        assertEquals("Smart Plug", configuring.deviceName)
        assertEquals("AA:BB", configuring.deviceAddress)
        scanJob.cancel()
        provisioningScope.cancel()
    }

    @Test
    fun `Given onRestart while still SearchingDevice then state is unchanged`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.onConfigureImprovDevice(deviceName = "Smart Plug")

        orchestrator.onRestart()

        // Try-again is only exposed in Errored; from SearchingDevice it's effectively a no-op.
        val state = orchestrator.uiState.value
        assertInstanceOf(ImprovUIState.SearchingDevice::class.java, state)
        assertEquals("Smart Plug", (state as ImprovUIState.SearchingDevice).deviceName)
    }
}
