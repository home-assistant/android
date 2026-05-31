package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.net.SocketFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ActiveNetworkSocketFactoryTest {

    @Test
    fun `Given active network when creating socket then uses network socket factory`() {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val networkSocketFactory = mockk<SocketFactory>(relaxed = true)
        every { connectivityManager.activeNetwork } returns network
        every { network.socketFactory } returns networkSocketFactory
        every { networkSocketFactory.createSocket() } returns mockk(relaxed = true)

        ActiveNetworkSocketFactory(connectivityManager).createSocket()

        verify { networkSocketFactory.createSocket() }
    }

    @Test
    fun `Given no active network when creating socket then creates socket via default factory`() {
        val connectivityManager = mockk<ConnectivityManager>()
        every { connectivityManager.activeNetwork } returns null

        ActiveNetworkSocketFactory(connectivityManager).createSocket().use { socket ->
            assertNotNull(socket)
        }
    }
}
