package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * [SocketFactory] that creates sockets on [ConnectivityManager.getActiveNetwork].
 *
 * Used by OkHttp for the raw TCP socket before TLS wrapping so connections follow the same
 * network as [NetworkAwareDns] resolution.
 */
internal class ActiveNetworkSocketFactory(private val connectivityManager: ConnectivityManager) : SocketFactory() {

    private val defaultFactory: SocketFactory = getDefault()

    private fun delegateFactory(): SocketFactory {
        return connectivityManager.activeNetwork?.socketFactory ?: defaultFactory
    }

    override fun createSocket(): Socket = delegateFactory().createSocket()

    override fun createSocket(host: String, port: Int): Socket = delegateFactory().createSocket(host, port)

    override fun createSocket(host: String, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegateFactory().createSocket(host, port, localAddress, localPort)

    override fun createSocket(address: InetAddress, port: Int): Socket = delegateFactory().createSocket(address, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegateFactory().createSocket(address, port, localAddress, localPort)
}
