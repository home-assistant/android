package io.homeassistant.companion.android.data.wear

import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.PATH_DNS_LOOKUP
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSResult
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSRequest
import java.net.InetAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

class WearDnsRequestListenerTest {
    private val testHostname = "homeassistant.local"

    private val homeAssistantLocal: InetAddress = InetAddress.getByAddress(testHostname, byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Given a request with a DNS lookup path when a request is made the IP address is returned`() = runTest {
        // Given
        val testScope = TestScope(UnconfinedTestDispatcher())
        val fakeDns = Dns {
            if (it == testHostname) {
                listOf(homeAssistantLocal)
            } else {
                throw IllegalArgumentException("hostname not found")
            }
        }
        val service = WearDnsRequestListener(fakeDns, scope = testScope)

        // When
        val task = service.onRequest("", PATH_DNS_LOOKUP, testHostname.encodeDNSRequest())

        // Then
        assertNotNull(task)
        val addresses = task.await().decodeDNSResult(testHostname)
        assertEquals(testHostname, addresses.single().hostName)
        assertEquals("192.168.0.23", addresses.single().hostAddress)
    }

    @Test
    fun `Given a request without a DNS lookup path when a request is made then no task is returned`() {
        // Given
        val testScope = TestScope(StandardTestDispatcher())
        val service = WearDnsRequestListener(scope = testScope)

        // When
        val task = service.onRequest("", "", testHostname.encodeDNSRequest())

        // Then
        assertNull(task)
    }
}
