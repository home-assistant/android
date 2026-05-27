package io.homeassistant.companion.android.common.data.network

import android.net.Network
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * DNS addresses resolved on a specific [Network] snapshot.
 *
 * Used when subsequent socket connections must use the same network as the DNS lookup.
 */
data class NetworkBoundDnsResult(val network: Network, val addresses: List<InetAddress>)

/**
 * Opens a TCP connection to [address]:[port] on [network].
 */
internal fun openSocketOnNetwork(network: Network, address: InetAddress, port: Int, connectTimeoutMs: Int): Socket {
    val socket = network.socketFactory.createSocket()
    socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
    return socket
}
