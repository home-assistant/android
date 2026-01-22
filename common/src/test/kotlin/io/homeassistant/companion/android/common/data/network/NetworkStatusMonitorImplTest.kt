package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStatusMonitorImplTest {

    private val connectivityManager: ConnectivityManager = mockk()
    private val networkHelper: NetworkHelper = mockk()
    private val network: Network = mockk()
    private val connectionStateProvider: ServerConnectionStateProvider = mockk(relaxed = true)

    private lateinit var networkMonitor: NetworkStatusMonitor
    private lateinit var callbackSlot: CapturingSlot<ConnectivityManager.NetworkCallback>

    @BeforeEach
    fun setup() {
        callbackSlot = slot()
        networkMonitor = NetworkStatusMonitorImpl(connectivityManager, networkHelper)

        every {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(callbackSlot),
            )
        } just Runs

        every {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } just Runs
    }

    @Test
    fun `Given monitoring flow when monitoring is canceled then flow unregisters callback`() = runTest {
        every { networkHelper.hasActiveNetwork() } returns true
        every { networkHelper.isNetworkValidated() } returns true
        val job = launch {
            networkMonitor.observeNetworkStatus(connectionStateProvider).collect()
        }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        verify { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
    }

    @Test
    fun `Given no active network when monitoring starts then state is UNAVAILABLE`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns false
        every { networkHelper.isNetworkValidated() } returns false

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

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then - Should be READY_NET_LOCAL instead of stuck in CONNECTING
        assertEquals(NetworkState.READY_NET_LOCAL, result)
    }

    @Test
    fun `Given public URL when network is not validated then state is CONNECTING`() = runTest {
        // Given - Public URL that requires internet validation
        every { networkHelper.hasActiveNetwork() } returns true
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false
        coEvery { connectionStateProvider.getExternalUrl() } returns URL("https://my-ha.duckdns.org") // Public URL

        // When
        val result = networkMonitor.observeNetworkStatus(connectionStateProvider).first()

        // Then - Should wait for validation (CONNECTING) for public URLs
        assertEquals(NetworkState.CONNECTING, result)
    }

    @Test
    fun `Given network becomes ready when callback triggered then state updates from UNAVAILABLE to READY_INTERNAL`() = runTest {
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)
        coEvery { connectionStateProvider.isInternal(any()) } returns true
        every { networkHelper.isNetworkValidated() } returns false

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(connectionStateProvider).take(2).toList(states)
        }
        advanceUntilIdle()
        callbackSlot.captured.onAvailable(network)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_INTERNAL), states)
    }

    @Test
    fun `Given network becomes ready when callback triggered then state updates from UNAVAILABLE to READY_NET_VALIDATED`() = runTest {
        coEvery { connectionStateProvider.isInternal(false) } returns false
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)
        coEvery { connectionStateProvider.getExternalUrl() } returns null
        every { networkHelper.isNetworkValidated() } returnsMany listOf(false, true)

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(connectionStateProvider).take(2).toList(states)
        }
        advanceUntilIdle()

        callbackSlot.captured.onAvailable(network)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_NET_VALIDATED), states)
    }
}
