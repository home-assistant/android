package io.homeassistant.companion.android.data.wear

import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.PATH_DNS_LOOKUP
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSResult
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSRequest
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class WearDnsRequestListenerTest {
    private val testHostname = "homeassistant.local"

    private val homeAssistantLocal: InetAddress = InetAddress.getByAddress(testHostname, byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @Test
    fun `Given a hostname when a DNS request is made then the IP address is returned`() = runTest {
        // Given
        val fakeDns = Dns {
            if (it == testHostname) {
                listOf(homeAssistantLocal)
            } else {
                throw IllegalArgumentException("hostname not found")
            }
        }
        val service = WearDnsRequestListener(fakeDns)

        // When
        val response = service.dnsRequest(testHostname.encodeDNSRequest())
        val addresses = response.decodeDNSResult(testHostname)

        // Then
        assertEquals(testHostname, addresses.single().hostName)
        assertEquals("192.168.0.23", addresses.single().hostAddress)
    }

    @Test
    fun `Given a request with a DNS lookup path when a request is made then a task is returned`() {
        // Given
        val service = WearDnsRequestListener()

        // When
        val task = service.onRequest("", PATH_DNS_LOOKUP, "homeassistant.local".encodeDNSRequest())

        // Then
        assertNotNull(task)
    }

    @Test
    fun `Given a request without a DNS lookup path when a request is made then no task is returned`() {
        // Given
        val service = WearDnsRequestListener()

        // When
        val task = service.onRequest("", "", "homeassistant.local".encodeDNSRequest())

        // Then
        assertNull(task)
    }
}
