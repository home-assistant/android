package io.homeassistant.companion.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.data.ShadowDnsSystem.Companion.shadowOf
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// We need Robolectric because of the usage of URI from `android.net.URI`
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowDnsSystem::class])
class WearDnsTest {

    private val dns = WearDns(ApplicationProvider.getApplicationContext<Context>())

    val homeAssistantLocal = InetAddress.getByAddress("homeassistant.local", byteArrayOf(192.toByte(), 168.toByte(), 0, 23))

    @Test
    fun `defaults to system dns`() {
        val shadowDns = shadowOf(Dns.SYSTEM)
        shadowDns.results["homeassistant.local"] = Result.success(listOf(homeAssistantLocal))

        val results = dns.lookup("homeassistant.local")
        assertEquals(homeAssistantLocal, results.single())
    }
}
