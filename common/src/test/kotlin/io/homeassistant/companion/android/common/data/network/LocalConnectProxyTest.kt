package io.homeassistant.companion.android.common.data.network

import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `Given IPv6 literal when parsing CONNECT target then return host and port`() {
        assertEquals(
            "2001:db8::1" to 8123,
            parseConnectTarget("[2001:db8::1]:8123"),
        )
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
}
