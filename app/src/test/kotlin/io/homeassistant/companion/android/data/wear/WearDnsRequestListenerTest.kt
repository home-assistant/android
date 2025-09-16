package io.homeassistant.companion.android.data.wear

import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSResult
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSRequest
import io.homeassistant.companion.android.data.wear.ShadowDnsSystem.Companion.shadowOf
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(shadows = [ShadowDnsSystem::class])
@RunWith(RobolectricTestRunner::class)
class WearDnsRequestListenerTest {
    val homeAssistantLocal: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @Test
    fun testRequest() {
        // Given
        val controller = Robolectric.buildService(WearDnsRequestListener::class.java)
        val service = controller.create().get()

        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        // When
        val response = runBlocking { service.dnsRequest("homeassistant.local".encodeDNSRequest()) }
        val addresses = response.decodeDNSResult("homeassistant.local")

        // Then
        assertEquals("homeassistant.local", addresses.single().hostName)
        assertEquals("192.168.0.23", addresses.single().hostAddress)
    }
}
