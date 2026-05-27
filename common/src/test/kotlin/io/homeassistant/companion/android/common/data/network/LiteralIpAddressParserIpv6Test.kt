package io.homeassistant.companion.android.common.data.network

import dagger.hilt.android.testing.HiltTestApplication
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IPv6 literal parsing requires Robolectric because JVM unit tests lack IPv6 [InetAddress] support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [28])
class LiteralIpAddressParserIpv6Test {

    @Test
    fun `Given bracketed IPv6 literal when parsing then returns address`() {
        val address = LiteralIpAddressParser.parse("[2001:db8::1]")

        assertNotNull(address)
        assertTrue(address is Inet6Address)
        assertEquals("2001:db8:0:0:0:0:0:1", address!!.hostAddress)
    }

    @Test
    fun `Given IPv6 loopback when parsing then returns address`() {
        val address = LiteralIpAddressParser.parse("::1")

        assertNotNull(address)
        assertTrue(address is Inet6Address)
    }

    @Test
    fun `Given unbracketed IPv6 with port when parsing then returns null`() {
        assertNull(LiteralIpAddressParser.parse("2001:db8::1:8123"))
    }
}
