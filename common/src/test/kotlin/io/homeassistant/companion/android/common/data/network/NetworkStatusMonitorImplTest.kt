package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private val serverConfig: ServerConnectionInfo = mockk(relaxed = true)

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
            networkMonitor.observeNetworkStatus(serverConfig).collect()
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

        // When
        val result = networkMonitor.observeNetworkStatus(serverConfig).first()

        // Then
        assertEquals(NetworkState.UNAVAILABLE, result)
    }

    @Test
    fun `Given on local setup when active network then state is READY_LOCAL`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns true
        every { networkHelper.isNetworkValidated() } returns false
        every { serverConfig.isInternal(false) } returns true

        // When
        val result = networkMonitor.observeNetworkStatus(serverConfig).first()

        // Then
        assertEquals(NetworkState.READY_LOCAL, result)
    }

    @Test
    fun `Given on observing network on external setup when validated network then state is READY_REMOTE`() = runTest {
        // Given
        every { networkHelper.hasActiveNetwork() } returns true
        every { serverConfig.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns true

        // When
        val result = networkMonitor.observeNetworkStatus(serverConfig).first()

        // Then
        assertEquals(NetworkState.READY_REMOTE, result)
    }

    @Test
    fun `Given observing network when network exists but not validated then state is CONNECTING`() = runTest {
        every { networkHelper.hasActiveNetwork() } returns true
        every { serverConfig.isInternal(false) } returns false
        every { networkHelper.isNetworkValidated() } returns false

        val result = networkMonitor.observeNetworkStatus(serverConfig).first()

        assertEquals(NetworkState.CONNECTING, result)
    }

    @Test
    fun `Given network becomes ready when callback triggered then state updates from UNAVAILABLE to READY_LOCAL`() = runTest {
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)
        every { serverConfig.isInternal(any()) } returns true
        every { networkHelper.isNetworkValidated() } returns false

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(serverConfig).take(2).toList(states)
        }
        advanceUntilIdle()
        callbackSlot.captured.onAvailable(network)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_LOCAL), states)
    }

    @Test
    fun `Given network becomes ready when callback triggered then state updates from UNAVAILABLE to READY_REMOTE`() = runTest {
        every { serverConfig.isInternal(false) } returns false
        every { networkHelper.hasActiveNetwork() } returnsMany listOf(false, true)

        val states = mutableListOf<NetworkState>()
        val job = launch {
            networkMonitor.observeNetworkStatus(serverConfig).take(2).toList(states)
        }
        advanceUntilIdle()

        every { networkHelper.isNetworkValidated() } returns true
        callbackSlot.captured.onAvailable(network)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(NetworkState.UNAVAILABLE, NetworkState.READY_REMOTE), states)
    }
}
