package io.homeassistant.companion.android.common.data.network

import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiteralIpAddressParserTest {

    @Test
    fun `Given IPv4 literal when parsing then returns address`() {
        val address = LiteralIpAddressParser.parse("192.0.2.1")

        assertEquals(InetAddress.getByName("192.0.2.1"), address)
    }

    @Test
    fun `Given IPv4 host with port when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("192.168.1.1:8123"))
    }

    @Test
    fun `Given hostname when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("homeassistant.local"))
    }

    @Test
    fun `Given mismatched brackets when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("[2001:db8::1"))
        assertNull(LiteralIpAddressParser.parse("2001:db8::1]"))
    }

    @Test
    fun `Given IPv6 zone index when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("fe80::1%wlan0"))
    }

    @Test
    fun `Given invalid IPv4 octet when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("256.0.0.1"))
    }

    @Test
    fun `Given host with port helper when value contains IPv4 and port then true`() {
        assertTrue(LiteralIpAddressParser.isHostWithPort("192.168.1.1:8123"))
    }

    @Test
    fun `Given unbracketed IPv6 with port helper when value contains port then true`() {
        assertTrue(LiteralIpAddressParser.looksLikeUnbracketedIpv6WithPort("2001:db8::1:8123"))
    }

    @Test
    fun `Given blank value when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse(""))
        assertNull(LiteralIpAddressParser.parse("   "))
    }

    @Test
    fun `Given localhost when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("localhost"))
    }

    @Test
    fun `Given trailing dot hostname when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("homeassistant.local."))
    }

    @Test
    fun `Given host with port helper when value is IPv6 then false`() {
        assertFalse(LiteralIpAddressParser.isHostWithPort("2001:db8::1"))
    }
}
