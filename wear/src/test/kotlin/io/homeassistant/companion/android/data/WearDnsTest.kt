@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.data

import android.content.Context
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.CAPABILITY_DNS_VIA_MOBILE
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSResult
import io.homeassistant.companion.android.fakes.FakeCapabilityClient
import io.homeassistant.companion.android.fakes.FakeClock
import io.homeassistant.companion.android.fakes.FakeDns
import io.homeassistant.companion.android.fakes.FakeMessageClient
import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class WearDnsTest {
    private val context = mockk<Context> {
        every { applicationContext } returns this
        every { packageManager } returns mockk()
    }
    val messageClient = FakeMessageClient(context)
    val capabilityClient = FakeCapabilityClient(context)
    val clock = FakeClock()
    val dns = FakeDns()
    private val wearDns = WearDns(messageClient, capabilityClient, clock, dns)

    val homeAssistantLocal: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))
    val homeAssistantLocal2: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 24))

    @Test
    fun `Given a hostname when making DNS lookup then returns DNS entry`() {
        // given
        dns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        // when
        val results = wearDns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a hostname when making DNS lookup then falls back to mobile dns when present`() {
        // given
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        // when
        val results = wearDns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a hostname when making DNS lookup then fails when mobile not present`() {
        // given
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf()

        try {
            // when
            wearDns.lookup("homeassistant.local")
            fail { "Lookup should throw" }
        } catch (exception: UnknownHostException) {
            // then
            assertEquals("No Mobile DNS helper registered. Unable to resolve homeassistant.local", exception.message)
        }
    }

    @Test
    fun `Given a hostname when making DNS lookup then fails when mobile fails`() {
        // given
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { byteArrayOf() }

        try {
            // when
            wearDns.lookup("homeassistant.local")
            fail { "Lookup should throw" }
        } catch (exception: UnknownHostException) {
            // then
            assertEquals("Mobile helper unable to resolve homeassistant.local", exception.message)
        }
    }

    @Test
    fun `Given a cached hostname when making DNS lookup then returns cached results`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        wearDns.lookup("homeassistant.local")

        messageClient.onRequest = { listOf<InetAddress>().encodeDNSResult() }

        // when
        val results = wearDns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a stale cached hostname when making DNS lookup then fetches fresh results`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        wearDns.lookup("homeassistant.local")

        messageClient.onRequest = { listOf(homeAssistantLocal2).encodeDNSResult() }
        clock.time = clock.time + wearDns.cacheLifetime + 1.seconds

        // when
        val results = wearDns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal2, results.single())
    }

    @Test
    fun `Given an UnknownHostException when making DNS lookup then returns cached failure`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        dns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf<InetAddress>().encodeDNSResult() }

        try {
            wearDns.lookup("homeassistant.local")
            fail()
        } catch (e: UnknownHostException) {
            // expected
        }

        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        try {
            // when
            wearDns.lookup("homeassistant.local")
            fail { "Lookup should throw" }
        } catch (exception: UnknownHostException) {
            // then
            assertEquals("Mobile helper unable to resolve homeassistant.local", exception.message)
        }
    }
}
