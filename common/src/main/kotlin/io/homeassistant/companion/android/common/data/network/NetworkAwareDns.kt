package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import io.homeassistant.companion.android.di.OkHttpConfigurator
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * A DNS resolver that queries both A and AAAA (IPv6) records when resolving hostnames.
 *
 * Android's default DNS resolver may skip AAAA lookups under certain network conditions,
 * causing connection failures on IPv6-mostly or DNS64 networks. This resolver uses an
 * alternative API path ([Network.getAllByName]) that performs both query types reliably.
 *
 * Falls back to [Dns.SYSTEM] when no active network is available.
 *
 * Also implements [OkHttpConfigurator] so it can be injected into the shared
 * [OkHttpClient] builder via the existing multibinding set.
 */
@Singleton
class NetworkAwareDns @Inject constructor(
    private val connectivityManager: ConnectivityManager
) : Dns, OkHttpConfigurator {

    override fun lookup(hostname: String): List<InetAddress> {
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val addresses = network.getAllByName(hostname).toList()
            Timber.d("lookup(%s): using Network.getAllByName() -> %s", hostname, addresses.joinToString { it.hostAddress ?: it.toString() })
            return addresses
        }
        Timber.w("lookup(%s): no active network, falling back to Dns.SYSTEM", hostname)
        return Dns.SYSTEM.lookup(hostname)
    }

    override fun invoke(builder: OkHttpClient.Builder) {
        builder.dns(this)
    }
}