package io.homeassistant.companion.android.common.data.network

import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalConnectProxyTest {

    @Test
    fun `Given hostname with port when parsing CONNECT target then return host and port`() {
        assertEquals("ha.duscha.dev" to 8123, parseConnectTarget("ha.duscha.dev:8123"))
    }

    @Test
    fun `Given hostname without port when parsing CONNECT target then default to 443`() {
        assertEquals("ha.duscha.dev" to 443, parseConnectTarget("ha.duscha.dev"))
    }

    @Test
    fun `Given localhost with port when parsing CONNECT target then return host and port`() {
        assertEquals("localhost" to 8123, parseConnectTarget("localhost:8123"))
    }

    @Test
    fun `Given bracketed IPv6 literal when parsing CONNECT target then return host and port`() {
        assertEquals(
            "2001:db8::1" to 8123,
            parseConnectTarget("[2001:db8::1]:8123"),
        )
    }

    @Test
    fun `Given bracketed IPv6 without port when parsing CONNECT target then default to 443`() {
        assertEquals("2001:db8::1" to 443, parseConnectTarget("[2001:db8::1]"))
    }

    @Test
    fun `Given unbracketed IPv6 without port when parsing CONNECT target then default to 443`() {
        assertEquals("2001:db8::1" to 443, parseConnectTarget("2001:db8::1"))
    }

    @Test
    fun `Given unbracketed IPv6 with port when parsing CONNECT target then return host and port`() {
        assertEquals("2001:db8::1" to 8123, parseConnectTarget("2001:db8::1:8123"))
    }

    @Test
    fun `Given invalid port when parsing CONNECT target then default to 443`() {
        assertEquals("ha.duscha.dev" to 443, parseConnectTarget("ha.duscha.dev:99999"))
        assertEquals("ha.duscha.dev" to 443, parseConnectTarget("ha.duscha.dev:0"))
        assertEquals("ha.duscha.dev" to 443, parseConnectTarget("ha.duscha.dev:not-a-port"))
    }

    @Test
    fun `Given dual-stack addresses when ordering then IPv6 precedes IPv4`() {
        val ipv4 = InetAddress.getByName("192.0.2.1") as Inet4Address
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address

        val ordered = orderConnectAddresses(listOf(ipv4, ipv6))

        assertEquals(listOf(ipv6, ipv4), ordered)
    }

    @Test
    fun `Given IPv6-only addresses when ordering then order is unchanged`() {
        val ipv6 = InetAddress.getByName("2001:db8::1")

        val ordered = orderConnectAddresses(listOf(ipv6))

        assertEquals(listOf(ipv6), ordered)
    }

    @Test
    fun `Given empty address list when ordering then returns empty list`() {
        assertTrue(orderConnectAddresses(emptyList()).isEmpty())
    }

    @Test
    fun `Given CRLF request line when reading ASCII line then return line without terminator`() {
        val input = ByteArrayInputStream("CONNECT host:443 HTTP/1.1\r\n".encodeToByteArray())
        assertEquals("CONNECT host:443 HTTP/1.1", readConnectProxyAsciiLine(input))
    }

    @Test
    fun `Given LF request line when reading ASCII line then return line without terminator`() {
        val input = ByteArrayInputStream("Host: example.com\n".encodeToByteArray())
        assertEquals("Host: example.com", readConnectProxyAsciiLine(input))
    }

    @Test
    fun `Given empty stream when reading ASCII line then return null`() {
        val input = ByteArrayInputStream(ByteArray(0))
        assertNull(readConnectProxyAsciiLine(input))
    }

    @Test
    fun `Given line exceeding max length when reading ASCII line then throw`() {
        val oversizedLine = "A".repeat(8193) + "\n"
        val input = ByteArrayInputStream(oversizedLine.encodeToByteArray())

        assertThrows(Exception::class.java) {
            readConnectProxyAsciiLine(input)
        }
    }
}
