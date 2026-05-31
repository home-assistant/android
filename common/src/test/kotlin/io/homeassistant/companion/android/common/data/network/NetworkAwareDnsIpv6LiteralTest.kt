package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bracketed IPv6 literals require Robolectric because JVM unit tests lack IPv6 [InetAddress] support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [28])
class NetworkAwareDnsIpv6LiteralTest {

    @Test
    fun `Given bracketed IPv6 literal when looking up hostname then returns parsed address`() {
        val networkAwareDns = NetworkAwareDns(
            connectivityManager = mockk<ConnectivityManager>(relaxed = true),
            context = org.robolectric.RuntimeEnvironment.getApplication(),
        )

        val result = networkAwareDns.lookup("[2001:db8::1]")

        assertEquals(1, result.size)
        assertTrue(result[0] is Inet6Address)
        assertNotNull(result[0].hostAddress)
    }
}
