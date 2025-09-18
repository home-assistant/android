@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.CAPABILITY_DNS_VIA_MOBILE
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSResult
import io.homeassistant.companion.android.shadows.FakeCapabilityClient
import io.homeassistant.companion.android.shadows.FakeClock
import io.homeassistant.companion.android.shadows.FakeMessageClient
import io.homeassistant.companion.android.shadows.ShadowDnsSystem
import io.homeassistant.companion.android.shadows.ShadowDnsSystem.Companion.shadowOf
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import okhttp3.Dns
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowDnsSystem::class])
@ConscryptMode(ConscryptMode.Mode.ON)
class WearDnsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    val messageClient = FakeMessageClient(context)
    val capabilityClient = FakeCapabilityClient(context)
    val clock = FakeClock()
    private val dns = WearDns(messageClient, capabilityClient, clock)

    val homeAssistantLocal: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))
    val homeAssistantLocal2: InetAddress = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 24))

    @Test
    fun `Given a hostname when making DNS lookup then returns DNS entry`() {
        // given
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        // when
        val results = dns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a hostname when making DNS lookup then falls back to mobile dns when present`() {
        // given
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        // when
        val results = dns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a hostname when making DNS lookup then fails when mobile not present`() {
        // given
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf()

        // when
        val exception = assertThrows<UnknownHostException> {
            dns.lookup("homeassistant.local")
        }

        // then
        assertEquals("No Mobile DNS helper registered. Unable to resolve homeassistant.local", exception.message)
    }

    @Test
    fun `Given a hostname when making DNS lookup then fails when mobile fails`() {
        // given
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { byteArrayOf() }

        // when
        val exception = assertThrows<UnknownHostException> {
            dns.lookup("homeassistant.local")
        }

        // then
        assertEquals("Mobile helper unable to resolve homeassistant.local", exception.message)
    }

    @Test
    fun `Given a cached hostname when making DNS lookup then returns cached results`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        dns.lookup("homeassistant.local")

        messageClient.onRequest = { listOf<InetAddress>().encodeDNSResult() }

        // when
        val results = dns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `Given a stale cached hostname when making DNS lookup then fetches fresh results`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        dns.lookup("homeassistant.local")

        messageClient.onRequest = { listOf(homeAssistantLocal2).encodeDNSResult() }
        clock.time = clock.time + dns.cacheLifetime + 1.seconds

        // when
        val results = dns.lookup("homeassistant.local")

        // then
        assertEquals(homeAssistantLocal2, results.single())
    }

    @Test
    fun `Given an UnknownHostException when making DNS lookup then returns cached failure`() {
        // given
        clock.time = Instant.fromEpochSeconds(1757959158)
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())
        capabilityClient.capabilities[CAPABILITY_DNS_VIA_MOBILE] = setOf("1234")
        messageClient.onRequest = { listOf<InetAddress>().encodeDNSResult() }

        try {
            dns.lookup("homeassistant.local")
            fail()
        } catch (e: UnknownHostException) {
            // expected
        }

        messageClient.onRequest = { listOf(homeAssistantLocal).encodeDNSResult() }

        // when
        val exception = assertThrows<UnknownHostException> {
            dns.lookup("homeassistant.local")
        }

        // then
        assertEquals("Mobile helper unable to resolve homeassistant.local", exception.message)
    }
}
