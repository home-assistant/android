package io.homeassistant.companion.android.common.data.network

import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkBoundDnsLookupTest {

    @Test
    fun `Given hostname when building DNS query then encodes question section`() {
        val query = NetworkBoundDnsLookup.buildDnsQuery(
            hostname = "ha.example.com",
            recordType = 28,
        )

        assertTrue(query.size > 12)
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
        assertEquals(0x00.toByte(), query[4])
        assertEquals(0x01.toByte(), query[5])
    }

    @Test
    fun `Given AAAA DNS response when parsing then returns IPv6 address`() {
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val response = buildAaaaResponse(ipv6)

        val addresses = NetworkBoundDnsLookup.parseDnsResponse(response, recordType = 28)

        assertEquals(listOf(ipv6), addresses)
    }

    @Test
    fun `Given A DNS response when parsing then returns IPv4 address`() {
        val ipv4 = InetAddress.getByName("192.0.2.1")
        val response = buildAResponse(ipv4)

        val addresses = NetworkBoundDnsLookup.parseDnsResponse(response, recordType = 1)

        assertEquals(listOf(ipv4), addresses)
    }

    private fun buildAaaaResponse(address: InetAddress): ByteArray {
        val rdata = address.address
        return byteArrayOf(
            0x00, 0x01,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x1C,
            0x00, 0x01,
            0xC0.toByte(), 0x0C,
            0x00, 0x1C,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x3C,
            0x00, 0x10,
            *rdata,
        )
    }

    private fun buildAResponse(address: InetAddress): ByteArray {
        val rdata = address.address
        return byteArrayOf(
            0x00, 0x01,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x01,
            0x00, 0x01,
            0xC0.toByte(), 0x0C,
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x3C,
            0x00, 0x04,
            *rdata,
        )
    }
}
