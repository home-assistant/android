package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetworkAwareDnsTest {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkAwareDns: NetworkAwareDns

    @BeforeEach
    fun setup() {
        connectivityManager = mockk()
        networkAwareDns = NetworkAwareDns(connectivityManager)
    }

    @Test
    fun `Given active network when looking up hostname then uses network DNS resolution`() {
        val hostname = "homeassistant.local"
        val expectedAddress = InetAddress.getByName("192.168.1.100")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(expectedAddress)
        }
        every { connectivityManager.activeNetwork } returns network

        val result = networkAwareDns.lookup(hostname)

        assertEquals(listOf(expectedAddress), result)
        verify(exactly = 1) { network.getAllByName(hostname) }
    }

    @Test
    fun `Given no active network when looking up hostname then falls back to system DNS`() {
        val hostname = "127.0.0.1"
        every { connectivityManager.activeNetwork } returns null

        val result = networkAwareDns.lookup(hostname)

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Given network resolution when hostname has both IPv4 and IPv6 addresses then both are returned`() {
        val hostname = "example.com"
        val ipv4 = InetAddress.getByName("192.0.2.1")
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(ipv4, ipv6)
        }
        every { connectivityManager.activeNetwork } returns network

        val result = networkAwareDns.lookup(hostname)

        assertEquals(2, result.size)
        assertTrue(result.contains(ipv4))
        assertTrue(result.contains(ipv6))
    }

    @Test
    fun `Given network resolution when hostname has only IPv6 address then only IPv6 is returned`() {
        val hostname = "ipv6-only.example.com"
        val ipv6 = InetAddress.getByName("2001:db8::1")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(ipv6)
        }
        every { connectivityManager.activeNetwork } returns network

        val result = networkAwareDns.lookup(hostname)

        assertEquals(1, result.size)
        assertEquals(ipv6, result[0])
    }

    @Test
    fun `Given hostname resolves only to ULA IPv6 address then ULA is returned correctly`() {
        val hostname = "ha.internal"
        val ulaAddress = InetAddress.getByName("fd12:3456:789a::1")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(ulaAddress)
        }
        every { connectivityManager.activeNetwork } returns network

        val result = networkAwareDns.lookup(hostname)

        assertEquals(1, result.size)
        val resultAddr = result[0]
        assertTrue(resultAddr is InetAddress)
        assertTrue(resultAddr.hostAddress!!.startsWith("fd12:"))
        assertEquals(ulaAddress, resultAddr)
    }

    @Test
    fun `Given hostname resolves to both ULA and global IPv6 then both are returned`() {
        val hostname = "dual-v6.internal"
        val ulaAddress = InetAddress.getByName("fd12:3456:789a::1")
        val globalV6Address = InetAddress.getByName("2001:db8::2")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(ulaAddress, globalV6Address)
        }
        every { connectivityManager.activeNetwork } returns network

        val result = networkAwareDns.lookup(hostname)

        assertEquals(2, result.size)
        assertTrue(result.contains(ulaAddress))
        assertTrue(result.contains(globalV6Address))
    }

    @Test
    fun `Given network resolution fails when looking up hostname then exception propagates`() {
        val hostname = "unknown.example.com"
        val network = mockk<Network> {
            every { getAllByName(hostname) } throws UnknownHostException("No such host")
        }
        every { connectivityManager.activeNetwork } returns network

        assertThrows<UnknownHostException> {
            networkAwareDns.lookup(hostname)
        }
    }

    @Test
    fun `Given NetworkAwareDns configures OkHttpClient then client uses custom dns for resolution`() {
        val builder = OkHttpClient.Builder()
        val hostname = "homeassistant.local"
        val expectedAddress = InetAddress.getByName("192.168.1.100")
        val network = mockk<Network> {
            every { getAllByName(hostname) } returns arrayOf(expectedAddress)
        }
        every { connectivityManager.activeNetwork } returns network

        networkAwareDns.invoke(builder)
        val client = builder.build()

        val addresses = client.dns.lookup(hostname)
        assertEquals(listOf(expectedAddress), addresses)
    }
}