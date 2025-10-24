package io.homeassistant.companion.android.onboarding.sethomenetwork

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val MOCK_SERVER_ID = 1
private const val MOCK_INITIAL_WIFI_SSID = "MyHomeWifi"
private const val MOCK_INITIAL_WIFI_SSID_QUOTED = "\"MyHomeWifi\""

@ExtendWith(MainDispatcherJUnit5Extension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SetHomeNetworkViewModelTest {

    private val serverManager: ServerManager = mockk()
    private val networkHelper: NetworkHelper = mockk(relaxed = true)
    private val wifiHelper: WifiHelper = mockk()
    private lateinit var viewModel: SetHomeNetworkViewModel

    @BeforeEach
    fun setup() {
        every { wifiHelper.getWifiSsid() } returns MOCK_INITIAL_WIFI_SSID_QUOTED
        every { serverManager.updateServer(any()) } just Runs
    }

    private fun initializeViewModel() {
        viewModel = SetHomeNetworkViewModel(
            serverId = MOCK_SERVER_ID,
            serverManager = serverManager,
            networkHelper = networkHelper,
            wifiHelper = wifiHelper,
        )
    }

    @Test
    fun `Given view model initialized, when observing current Wi-Fi network, then emits initial value from Wi-Fi helper`() {
        initializeViewModel()
        assertEquals(MOCK_INITIAL_WIFI_SSID, viewModel.currentWifiNetwork.value)
    }

    @Test
    fun `Given view model initialized and Wi-Fi SSID null, when observing current Wi-Fi network, then emits empty string`() {
        every { wifiHelper.getWifiSsid() } returns null
        initializeViewModel()
        assertEquals("", viewModel.currentWifiNetwork.value)
    }

    @Test
    fun `Given view model initialized and ethernet connected, when observing is using ethernet, then emits true`() {
        every { networkHelper.isUsingEthernet() } returns true
        initializeViewModel()
        assertTrue(viewModel.isUsingEthernet.value)
    }

    @Test
    fun `Given view model initialized and no ethernet, when observing is using ethernet, then emits false`() = runTest {
        initializeViewModel()
        assertFalse(viewModel.isUsingEthernet.value)
    }

    @Test
    fun `Given view model initialized and VPN connected, when observing is using VPN, then emits true`() = runTest {
        every { networkHelper.isUsingVpn() } returns true
        initializeViewModel()
        assertTrue(viewModel.isUsingVpn.value)
    }

    @Test
    fun `Given view model initialized and no VPN, when observing is using VPN, then emits false`() {
        initializeViewModel()
        assertFalse(viewModel.isUsingVpn.value)
    }

    @Test
    fun `Given view model, when on current Wi-Fi network change called, then current Wi-Fi network flow is updated`() = runTest {
        initializeViewModel()
        val newSsid = "NewWifiSsid"
        viewModel.currentWifiNetwork.test {
            assertEquals(MOCK_INITIAL_WIFI_SSID, awaitItem())
            viewModel.onCurrentWifiNetworkChange(newSsid)
            assertEquals(newSsid, awaitItem())
        }
    }

    @Test
    fun `Given view model, when on using ethernet change called, then is using ethernet flow is updated`() = runTest {
        initializeViewModel()
        viewModel.isUsingEthernet.test {
            assertFalse(awaitItem())
            viewModel.onUsingEthernetChange(true)
            assertTrue(awaitItem())
            viewModel.onUsingEthernetChange(false)
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `Given view model, when on using VPN change called, then is using VPN flow is updated`() = runTest {
        initializeViewModel()
        viewModel.isUsingVpn.test {
            assertFalse(awaitItem())
            viewModel.onUsingVpnChange(true)
            assertTrue(awaitItem())
            viewModel.onUsingVpnChange(false)
            assertFalse(awaitItem())
        }
    }

    private fun fakeServer(
        initialSsids: List<String> = emptyList(),
        initialVpn: Boolean = false,
        initialEthernet: Boolean = false,
    ): Server {
        val fakeConnectionInfo = ServerConnectionInfo("", internalSsids = initialSsids, internalVpn = initialVpn, internalEthernet = initialEthernet)
        val fakeServer = Server(_name = "", id = MOCK_SERVER_ID, connection = fakeConnectionInfo, session = mockk(), user = mockk())
        return fakeServer
    }

    @Test
    fun `Given Wi-Fi network not empty, no vpn and no ethernet, when on next click, then server is updated with SSID and no VPN no ethernet`() = runTest {
        val fakeServer = fakeServer()
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns fakeServer
        initializeViewModel()

        viewModel.onUsingEthernetChange(false)
        viewModel.onUsingVpnChange(false)
        viewModel.onCurrentWifiNetworkChange(MOCK_INITIAL_WIFI_SSID)

        viewModel.onNextClick()
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify { serverManager.updateServer(capture(serverSlot)) }
        val updatedServer = serverSlot.captured
        assertEquals(listOf(MOCK_INITIAL_WIFI_SSID), updatedServer.connection.internalSsids)
        assertEquals(false, updatedServer.connection.internalVpn)
        assertEquals(false, updatedServer.connection.internalEthernet)
    }

    @Test
    fun `Given server exists and Wi-Fi network empty, when on next click, then server is updated with empty SSID list`() = runTest {
        val fakeServer = fakeServer()
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns fakeServer
        initializeViewModel()
        viewModel.onCurrentWifiNetworkChange("")

        viewModel.onNextClick()
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify { serverManager.updateServer(capture(serverSlot)) }
        assertTrue(serverSlot.captured.connection.internalSsids.isEmpty())
    }

    @Test
    fun `Given server exists and VPN enabled, when on next click, then server is updated with VPN true`() = runTest {
        val fakeServer = fakeServer()
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns fakeServer
        initializeViewModel()
        viewModel.onUsingVpnChange(true)

        viewModel.onNextClick()
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify { serverManager.updateServer(capture(serverSlot)) }
        assertEquals(true, serverSlot.captured.connection.internalVpn)
    }

    @Test
    fun `Given server exists and ethernet enabled, when on next click, then server is updated with ethernet true`() = runTest {
        val fakeServer = fakeServer()
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns fakeServer
        initializeViewModel()
        viewModel.onUsingEthernetChange(true)

        viewModel.onNextClick()
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify { serverManager.updateServer(capture(serverSlot)) }
        assertEquals(true, serverSlot.captured.connection.internalEthernet)
    }

    @Test
    fun `Given server exists and all options enabled, when on next click, then server is updated with all settings`() = runTest {
        val fakeServer = fakeServer()
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns fakeServer
        val customSsid = "CustomNetwork"
        initializeViewModel()

        viewModel.onCurrentWifiNetworkChange(customSsid)
        viewModel.onUsingEthernetChange(true)
        viewModel.onUsingVpnChange(true)

        viewModel.onNextClick()
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify { serverManager.updateServer(capture(serverSlot)) }
        assertEquals(listOf(customSsid), serverSlot.captured.connection.internalSsids)
        assertEquals(true, serverSlot.captured.connection.internalVpn)
        assertEquals(true, serverSlot.captured.connection.internalEthernet)
    }

    @Test
    fun `Given server does not exist, when on next click, then server manager update not called`() = runTest {
        coEvery { serverManager.getServer(MOCK_SERVER_ID) } returns null
        initializeViewModel()

        viewModel.onNextClick()
        runCurrent()

        coVerify(exactly = 0) { serverManager.updateServer(any()) }
    }
}
