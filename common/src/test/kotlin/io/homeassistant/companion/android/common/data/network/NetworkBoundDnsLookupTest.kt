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

        assertTrue(query.packet.size > 12)
        assertEquals(0x01.toByte(), query.packet[2])
        assertEquals(0x00.toByte(), query.packet[3])
        assertEquals(0x00.toByte(), query.packet[4])
        assertEquals(0x01.toByte(), query.packet[5])
    }

    @Test
    fun `Given AAAA DNS response when parsing then returns IPv6 address`() {
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val response = buildAaaaResponse(ipv6)

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 28,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Matched(listOf(ipv6)), result)
    }

    @Test
    fun `Given A DNS response when parsing then returns IPv4 address`() {
        val ipv4 = InetAddress.getByName("192.0.2.1")
        val response = buildAResponse(ipv4)

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 1,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Matched(listOf(ipv4)), result)
    }

    @Test
    fun `Given mismatched transaction id when parsing DNS response then ignores packet`() {
        val ipv4 = InetAddress.getByName("192.0.2.1")
        val response = buildAResponse(ipv4)

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 1,
            expectedTransactionId = 99,
        )

        assertEquals(DnsResponseParseResult.Ignored, result)
    }

    @Test
    fun `Given matching transaction id and NXDOMAIN rcode when parsing then returns matched empty list`() {
        val response = buildNxDomainResponse()

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 1,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Matched(emptyList()), result)
    }

    @Test
    fun `Given matching transaction id and NOERROR with zero answers when parsing then returns matched empty list`() {
        val response = buildNoAnswersResponse()

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 28,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Matched(emptyList()), result)
    }

    @Test
    fun `Given AAAA record in additional section when parsing then returns IPv6 address`() {
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val response = buildAaaaAdditionalOnlyResponse(ipv6)

        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = response,
            recordType = 28,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Matched(listOf(ipv6)), result)
    }

    @Test
    fun `Given truncated DNS packet when parsing then ignores packet`() {
        val result = NetworkBoundDnsLookup.parseDnsResponse(
            response = byteArrayOf(0x00, 0x01, 0x81.toByte()),
            recordType = 1,
            expectedTransactionId = 1,
        )

        assertEquals(DnsResponseParseResult.Ignored, result)
    }

    private fun buildAaaaAdditionalOnlyResponse(address: InetAddress): ByteArray {
        val rdata = address.address
        return byteArrayOf(
            0x00, 0x01,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x01,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x1C,
            0x00, 0x01,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x1C,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x3C,
            0x00, 0x10,
            *rdata,
        )
    }

    private fun buildNoAnswersResponse(): ByteArray {
        return byteArrayOf(
            0x00, 0x01,
            0x81.toByte(), 0x80.toByte(),
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x1C,
            0x00, 0x01,
        )
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

    private fun buildNxDomainResponse(): ByteArray {
        return byteArrayOf(
            0x00, 0x01,
            0x81.toByte(), 0x83.toByte(),
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            0x02, 'h'.code.toByte(), 'a'.code.toByte(), 0x00,
            0x00, 0x01,
            0x00, 0x01,
        )
    }
}
