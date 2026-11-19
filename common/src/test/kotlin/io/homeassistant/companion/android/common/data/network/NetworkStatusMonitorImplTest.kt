package io.homeassistant.companion.android.common.data.network

import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStatusMonitorImplTest {

    private val networkHelper: NetworkHelper = mockk()
    private val connectionStateProvider: ServerConnectionStateProvider = mockk(relaxed = true)

    private val networkChangedFlow = MutableSharedFlow<Unit>(replay = 1)
    private val networkChangeObserver: NetworkChangeObserver = mockk {
        every { observerNetworkChange } returns networkChangedFlow
    }

    private lateinit var networkMonitor: NetworkStatusMonitor

    @BeforeEach
    fun setup() {
        networkMonitor = NetworkStatusMonitorImpl(networkChangeObserver, networkHelper)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given no active network when monitoring starts then state is UNAVAILABLE`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns false
        every { networkHelper.isNetworkValidated() } returns false
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then
        assertEquals(NetworkState.UNAVAILABLE, result)
    }

    @Test
    fun `Given on local setup when active network then state is READY_INTERNAL`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns true
        every { networkHelper.isNetworkValidated() } returns false
        coEvery { connectionStateProvider.isInternal(false) } returns true
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then
        assertEquals(NetworkState.READY_INTERNAL, result)
    }

    @Test
    fun `Given on observing network on external setup when validated network then state is READY_NET_VALIDATED`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns true
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then
        assertEquals(NetworkState.READY_NET_VALIDATED, result)
    }

    @Test
    fun `Given observing network when network exists but not validated and no external URL then state is CONNECTING`() = runTest {
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false
        coEvery { connectionStateProvider.getExternalUrl() } returns null
        networkChangedFlow.emit(Unit)

        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        assertEquals(NetworkState.CONNECTING, result)
    }

    @Test
    fun `Given LAN-only network when network is not validated but external URL is local IP then state is READY_NET_LOCAL (issue 6099)`() = runTest {
        // Given - Simulating a LAN-only network without internet (like an isolated IoT VLAN)
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false // Network not validated because no internet
        coEvery { connectionStateProvider.getExternalUrl() } returns URL("http://192.168.1.100:8123") // Local IP address
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then - Should be READY_NET_LOCAL instead of stuck in CONNECTING
        assertEquals(NetworkState.READY_NET_LOCAL, result)
    }

    @Test
    fun `Given getExternalUrl throws when network is not validated then state is CONNECTING`() = runTest {
        // Given - getExternalUrl throws an exception
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false
        coEvery { connectionStateProvider.getExternalUrl() } throws RuntimeException("Server not found")
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then - Should fall back to CONNECTING
        assertEquals(NetworkState.CONNECTING, result)
    }

    @Test
    fun `Given public URL when network is not validated then state is CONNECTING`() = runTest {
        // Given - Public URL that requires internet validation
        val url = URL("https://my-ha.duckdns.org") // Public URL
        mockkStatic(InetAddress::class)
        every { InetAddress.getAllByName(url.host) } returns arrayOf(
            mockk<InetAddress>().apply {
                every { isSiteLocalAddress } returns false
                every { isLoopbackAddress } returns false
                every { isLinkLocalAddress } returns false
                every { isAnyLocalAddress } returns false
            },
        )
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false
        coEvery { connectionStateProvider.getExternalUrl() } returns url
        networkChangedFlow.emit(Unit)

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then - Should wait for validation (CONNECTING) for public URLs
        assertEquals(NetworkState.CONNECTING, result)
    }

    @Test
    fun `Given network becomes ready when change emitted then state updates from UNAVAILABLE to READY_INTERNAL`() = runTest {
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)
        coEvery { connectionStateProvider.isInternal(any()) } returns true
        every { networkHelper.isNetworkValidated() } returns false

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(connectionStateProvider).take(2).toList(states)
        }
        networkChangedFlow.emit(Unit)
        advanceUntilIdle()
        networkChangedFlow.emit(Unit)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_INTERNAL), states)
    }

    @Test
    fun `Given network becomes ready when change emitted then state updates from UNAVAILABLE to READY_NET_VALIDATED`() = runTest {
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)
        coEvery { connectionStateProvider.getExternalUrl() } returns null
        every { networkHelper.isNetworkValidated() } returnsMany listOf(false, true)

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(connectionStateProvider).take(2).toList(states)
        }
        networkChangedFlow.emit(Unit)
        advanceUntilIdle()
        networkChangedFlow.emit(Unit)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_NET_VALIDATED), states)
    }
}
