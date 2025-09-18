package io.homeassistant.companion.android.data.wear

import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.PATH_DNS_LOOKUP
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSResult
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSRequest
import io.homeassistant.companion.android.data.wear.ShadowDnsSystem.Companion.shadowOf
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(shadows = [ShadowDnsSystem::class])
@RunWith(RobolectricTestRunner::class)
class WearDnsRequestListenerTest {
    val homeAssistantLocal: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @Test
    fun `Given a hostname when a DNS request is made then the IP address is returned`() = runTest {
        // Given
        val controller = Robolectric.buildService(WearDnsRequestListener::class.java)
        val service = controller.create().get()

        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        // When
        val response = service.dnsRequest("homeassistant.local".encodeDNSRequest())
        val addresses = response.decodeDNSResult("homeassistant.local")

        // Then
        assertEquals("homeassistant.local", addresses.single().hostName)
        assertEquals("192.168.0.23", addresses.single().hostAddress)
    }

    @Test
    fun `Given a request with a DNS lookup path when a request is made then a task is returned`() {
        // Given
        val controller = Robolectric.buildService(WearDnsRequestListener::class.java)
        val service = controller.create().get()

        // When
        val task = service.onRequest("", PATH_DNS_LOOKUP, "homeassistant.local".encodeDNSRequest())

        // Then
        assertNotNull(task)
    }

    @Test
    fun `Given a request without a DNS lookup path when a request is made then no task is returned`() {
        // Given
        val controller = Robolectric.buildService(WearDnsRequestListener::class.java)
        val service = controller.create().get()

        // When
        val task = service.onRequest("", "", "homeassistant.local".encodeDNSRequest())

        // Then
        assertNull(task)
    }
}
