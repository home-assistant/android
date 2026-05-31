package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.data.network.orderConnectAddresses
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultConnectivityCheckerAddressOrderTest {

    @Test
    fun `Given dual-stack addresses when ordering for port check then IPv6 precedes IPv4`() {
        val ipv4 = InetAddress.getByName("192.0.2.1") as Inet4Address
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address

        val ordered = orderConnectAddresses(listOf(ipv4, ipv6))

        assertEquals(listOf(ipv6, ipv4), ordered)
    }
}
