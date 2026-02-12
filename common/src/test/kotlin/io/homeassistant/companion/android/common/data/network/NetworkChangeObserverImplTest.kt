package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.cash.turbine.test
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class NetworkChangeObserverImplTest {

    private val connectivityManager: ConnectivityManager = mockk()
    private val network: Network = mockk()
    private val networkCapabilities: NetworkCapabilities = mockk()

    private lateinit var observer: NetworkChangeObserverImpl
    private lateinit var callbackSlot: CapturingSlot<ConnectivityManager.NetworkCallback>

    @BeforeEach
    fun setup() {
        callbackSlot = slot()

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

    private fun createObserver(testScope: TestScope) {
        observer = NetworkChangeObserverImpl(
            connectivityManager,
            CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler)),
        )
    }

    @Test
    fun `Given observer when first subscriber collects then registers callback and emits initial value`() = runTest {
        createObserver(this)

        observer.observerNetworkChange.test {
            awaitItem()
            verify { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given observer when all subscribers cancel then unregisters callback`() = runTest {
        createObserver(this)

        observer.observerNetworkChange.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
    }

    @Test
    fun `Given active subscription when onAvailable called then emits`() = runTest {
        createObserver(this)

        observer.observerNetworkChange.test {
            awaitItem() // Initial emission
            callbackSlot.captured.onAvailable(network)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given active subscription when onLost called then emits`() = runTest {
        createObserver(this)

        observer.observerNetworkChange.test {
            awaitItem() // Initial emission
            callbackSlot.captured.onLost(network)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given active subscription when onCapabilitiesChanged called then emits`() = runTest {
        createObserver(this)

        observer.observerNetworkChange.test {
            awaitItem() // Initial emission
            callbackSlot.captured.onCapabilitiesChanged(network, networkCapabilities)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
