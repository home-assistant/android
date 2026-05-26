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
 * A DNS resolver that uses Android's [ConnectivityManager.activeNetwork] to resolve
 * hostnames, ensuring both A and AAAA (IPv6) record queries are performed.
 *
 * Android's default [InetAddress.getAllByName] (used by [Dns.SYSTEM]) may skip AAAA
 * lookups based on a routing-table probe for the `2000::` prefix. This causes
 * connection failures on IPv6-mostly or DNS64 networks, especially during network
 * transitions. This resolver bypasses that heuristic by using the [ConnectivityManager]
 * API which resolves addresses through an independent code path.
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