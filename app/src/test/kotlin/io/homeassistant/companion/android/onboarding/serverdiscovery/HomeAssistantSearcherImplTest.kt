package io.homeassistant.companion.android.onboarding.serverdiscovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.CapturingSlot
import io.mockk.Ordering
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import timber.log.Timber

@ExtendWith(ConsoleLogExtension::class)
class HomeAssistantSearcherImplTest {

    private lateinit var nsdManager: NsdManager
    private lateinit var wifiManager: WifiManager
    private lateinit var searcher: HomeAssistantSearcherImpl

    @BeforeEach
    fun setup() {
        nsdManager = mockk(relaxed = true)
        wifiManager = mockk(relaxed = true)
        searcher = HomeAssistantSearcherImpl(nsdManager, wifiManager)
        // Reset the singleton for each test
        HomeAssistantSearcherImpl.hasCollector.set(false)
    }

    @Test
    fun `Given one discoverable instance when discoveredInstanceFlow is called then should emit instance`() = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()
        val wifiLock = mockk<WifiManager.MulticastLock>(relaxed = true)
        val serviceInfo = createNsdServiceInfo()

        captureListeners(discoveryListener, resolveListener)
        every { wifiManager.createMulticastLock(LOCK_TAG) } returns wifiLock

        discoveredInstanceFlow {
            discoveryListener.captured.onServiceFound(serviceInfo)
            resolveListener.captured.onServiceResolved(serviceInfo)

            val item = awaitItem()
            assertTrue(HomeAssistantSearcherImpl.hasCollector.get())
            assertEquals(serviceInfo.serviceName, item.name)
            assertEquals(URL("http://localhost:8123"), item.url)
            assertEquals(HomeAssistantVersion.fromString("2025.8.0"), item.version)
            expectNoEvents()
        }

        assertFalse(HomeAssistantSearcherImpl.hasCollector.get())

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, resolveListener.captured)

        verify(ordering = Ordering.ORDERED) {
            wifiManager.createMulticastLock(LOCK_TAG)
            wifiLock.setReferenceCounted(true)
            wifiLock.acquire()
            wifiLock.release()
        }
    }

    @Test
    fun `Given two discoverable instance when discoveredInstanceFlow is called then should emit two instances while respecting concurrency`() = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()
        val wifiLock = mockk<WifiManager.MulticastLock>(relaxed = true)
        val serviceInfo = createNsdServiceInfo()
        val serviceInfo2 = createNsdServiceInfo(baseUrl = "https://helloworld.org")

        captureListeners(discoveryListener, resolveListener)
        every { wifiManager.createMulticastLock(LOCK_TAG) } returns wifiLock

        discoveredInstanceFlow {
            discoveryListener.captured.onServiceFound(serviceInfo)
            discoveryListener.captured.onServiceFound(serviceInfo2)

            verify(exactly = 0) {
                // We should only resolve once the first service is resolved
                nsdManager.resolveService(serviceInfo2, resolveListener.captured)
            }

            resolveListener.captured.onServiceResolved(serviceInfo)

            verify(exactly = 1) {
                // Now that service 1 has been resolved, service 2 should have been resolved as well.
                nsdManager.resolveService(serviceInfo2, resolveListener.captured)
            }

            resolveListener.captured.onServiceResolved(serviceInfo2)

            awaitItem().apply {
                assertTrue(HomeAssistantSearcherImpl.hasCollector.get())
                assertEquals(serviceInfo.serviceName, name)
                assertEquals(URL("http://localhost:8123"), url)
                assertEquals(HomeAssistantVersion.fromString("2025.8.0"), version)
            }
            resolveListener.captured.onServiceResolved(serviceInfo2)

            awaitItem().apply {
                assertTrue(HomeAssistantSearcherImpl.hasCollector.get())
                assertEquals(serviceInfo.serviceName, name)
                assertEquals(URL("https://helloworld.org"), url)
                assertEquals(HomeAssistantVersion.fromString("2025.8.0"), version)
            }

            expectNoEvents()
        }

        assertFalse(HomeAssistantSearcherImpl.hasCollector.get())

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, resolveListener.captured)

        verify(ordering = Ordering.ORDERED) {
            wifiManager.createMulticastLock(LOCK_TAG)
            wifiLock.setReferenceCounted(true)
            wifiLock.acquire()
            wifiLock.release()
        }
    }

    @Test
    fun `Given one discoverable instance impossible to resolve when discoveredInstanceFlow is called then nothing is emitted`() = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()
        val serviceInfo = createNsdServiceInfo()

        captureListeners(discoveryListener, resolveListener)

        discoveredInstanceFlow {
            discoveryListener.captured.onServiceFound(serviceInfo)
            resolveListener.captured.onResolveFailed(serviceInfo, 0)

            expectNoEvents()
        }

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, resolveListener.captured)
    }

    @ParameterizedTest
    @CsvSource(
        nullValues = ["null"],
        value = [
            "null,null",
            "http://localhost:8123,null",
            "http://localhost:8123,wrong_version",
            "malformed://localhost:8123,null",
        ],
    )
    fun `Given one discoverable instance with wrong attributes when discoveredInstanceFlow is called then nothing is emitted`(
        baseUrl: String?,
        version: String?,
    ) = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()
        val serviceInfo = createNsdServiceInfo(baseUrl, version)

        captureListeners(discoveryListener, resolveListener)

        discoveredInstanceFlow {
            discoveryListener.captured.onServiceFound(serviceInfo)
            resolveListener.captured.onServiceResolved(serviceInfo)

            expectNoEvents()
        }

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, resolveListener.captured)
    }

    @Test
    fun `Given one discoverable instance with null service when discoveredInstanceFlow is called then nothing is emitted`() = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()

        captureListeners(discoveryListener, resolveListener)

        discoveredInstanceFlow {
            discoveryListener.captured.onServiceFound(null)

            expectNoEvents()
        }

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, null)
    }

    @Test
    fun `Given fail to start discovery when discoveredInstanceFlow is called then throw DiscoveryFailedException`() = runTest {
        val discoveryListener = slot<NsdManager.DiscoveryListener>()
        val resolveListener = slot<NsdManager.ResolveListener>()

        captureListeners(discoveryListener, resolveListener)

        discoveredInstanceFlow {
            discoveryListener.captured.onStartDiscoveryFailed("", 0)
            val error = awaitError()
            assertTrue(error is DiscoveryFailedException)
            assertEquals("Start discovery failed with error code 0", error.message)
        }

        verifyDiscoveryStartedAndStopped(discoveryListener.captured, null)
    }

    @Test
    fun `Given two instance of discoveredInstanceFlow when collecting at the same time then it throws FailFastException`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, _ ->
            Timber.d(exception, "cauth")
            caughtException = exception
        }

        turbineScope {
            searcher.discoveredInstanceFlow().testIn(backgroundScope, name = "1")
            searcher.discoveredInstanceFlow().testIn(backgroundScope, name = "2")
        }
        assertTrue(HomeAssistantSearcherImpl.hasCollector.get())

        assertEquals("Something already started to collect, this flow is designed to only be collected by one collector at the time.", caughtException?.message)
    }

    @Test
    fun `Given collecting discoveredInstanceFlow when getting the flow a second time then it throws FailFastException`() = runTest {
        var caughtException: Throwable? = null
        FailFast.setHandler { exception, _ ->
            Timber.d(exception, "cauth")
            caughtException = exception
        }

        turbineScope {
            searcher.discoveredInstanceFlow().testIn(backgroundScope, name = "1")
        }

        // Getting a flow that is currently being collected should throw an exception
        val ignored = searcher.discoveredInstanceFlow()

        assertTrue(HomeAssistantSearcherImpl.hasCollector.get())

        assertEquals("Something has already called discoveredInstanceFlow() and didn't close the flow yet.", caughtException?.message)
    }

    private fun captureListeners(discoveryListener: CapturingSlot<NsdManager.DiscoveryListener>, resolveListener: CapturingSlot<NsdManager.ResolveListener>) {
        every { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, capture(discoveryListener)) } just Runs
        every { nsdManager.resolveService(any(), capture(resolveListener)) } just Runs
    }

    private suspend fun discoveredInstanceFlow(block: suspend TurbineTestContext<HomeAssistantInstance>.() -> Unit) {
        assertFalse(HomeAssistantSearcherImpl.hasCollector.get())
        searcher.discoveredInstanceFlow().test {
            assertTrue(HomeAssistantSearcherImpl.hasCollector.get())
            block()
        }
        assertFalse(HomeAssistantSearcherImpl.hasCollector.get())
    }

    private fun verifyDiscoveryStartedAndStopped(discoveryListener: NsdManager.DiscoveryListener, resolveListener: NsdManager.ResolveListener? = null) {
        verify(ordering = Ordering.ORDERED) {
            nsdManager.discoverServices(SERVICE_TYPE, any(), discoveryListener)
            resolveListener?.let {
                nsdManager.resolveService(any<NsdServiceInfo>(), resolveListener)
            }
            nsdManager.stopServiceDiscovery(discoveryListener)
        }

        if (resolveListener == null) verify(exactly = 0) { nsdManager.resolveService(any<NsdServiceInfo>(), any()) }
    }
}

private fun createNsdServiceInfo(baseUrl: String? = "http://localhost:8123", version: String? = "2025.8.0"): NsdServiceInfo {
    return mockk<NsdServiceInfo>().apply {
        val attributes = mutableMapOf<String, ByteArray>()
        baseUrl?.let {
            attributes["base_url"] = it.toByteArray(Charsets.UTF_8)
        }
        version?.let {
            attributes["version"] = it.toByteArray(Charsets.UTF_8)
        }
        every { serviceName } returns "Home Assistant"
        every { serviceType } returns SERVICE_TYPE
        every { this@apply.attributes } returns attributes
    }
}
