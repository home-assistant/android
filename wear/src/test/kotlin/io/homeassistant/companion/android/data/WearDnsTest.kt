package io.homeassistant.companion.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.data.ShadowDnsSystem.Companion.shadowOf
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowDnsSystem::class])
@ConscryptMode(ConscryptMode.Mode.ON)
class WearDnsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dns = WearDns(FakeMessageClient(context), FakeCapabilityClient(context))

    val homeAssistantLocal = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @Test
    fun `defaults to system dns`() {
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        val results = dns.lookup("homeassistant.local")
        assertEquals(homeAssistantLocal, results.single())
    }

    @Test
    fun `falls back to mobile dns`() {
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.failure(UnknownHostException())

        val results = dns.lookup("homeassistant.local")
        assertEquals(homeAssistantLocal, results.single())
    }
}
