package io.homeassistant.companion.android.common.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.di.OkHttpConfigurator
import io.homeassistant.companion.android.util.sensitive
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber

private const val DNS_QUERY_TIMEOUT_SECONDS = 5L
private const val MAIN_THREAD_DISPATCH_TIMEOUT_SECONDS = DNS_QUERY_TIMEOUT_SECONDS * 2 + 1

/**
 * A DNS resolver that uses Android's [ConnectivityManager] to resolve hostnames on the active
 * network, ensuring AAAA (IPv6) records are queried even when the system would otherwise skip
 * them.
 *
 * Android's default [InetAddress.getAllByName] (used by [Dns.SYSTEM]) and [Network.getAllByName]
 * may skip AAAA lookups unless the device has an IPv6 routing-table entry for `2000::`. This
 * causes connection failures for IPv6-only hostnames on otherwise IPv6-capable networks.
 * On API 29+, this resolver uses [DnsResolver] to issue explicit A and AAAA queries, with
 * fallback to [NetworkBoundDnsLookup] when [DnsResolver] fails. On API 23–28 it uses
 * [NetworkBoundDnsLookup] for the same explicit AAAA behavior.
 *
 * DNS must not block the main thread: [DnsResolver] reports I/O readiness through the main looper,
 * so lookups initiated on the UI thread are dispatched to a background worker first.
 *
 * Also implements [OkHttpConfigurator] so it can be injected into the shared [OkHttpClient]
 * builder via the existing multibinding set. Configures both [Dns] and a network-bound
 * [SocketFactory] so TCP connections use the active network after [lookup].
 *
 * WebView traffic does not use this resolver directly. When
 * [androidx.webkit.WebViewFeature.PROXY_OVERRIDE] is available it is routed through
 * [LocalConnectProxy], which calls [lookup]. Otherwise GET and HEAD requests fall back to
 * [HostnameWebViewRequestProxy].
 */
@Singleton
class NetworkAwareDns @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    @ApplicationContext context: Context,
) : Dns,
    OkHttpConfigurator {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    /** Runs blocking DNS work off the main thread. */
    private val dnsWorkerExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NetworkAwareDns-worker").apply { isDaemon = true }
    }

    @VisibleForTesting
    internal var dnsWorkerExecutorForTests: Executor? = null

    @VisibleForTesting
    internal var dnsMainExecutorForTests: Executor? = null

    @VisibleForTesting
    internal var isMainThreadForTests: (() -> Boolean)? = null

    @VisibleForTesting
    internal var useDnsResolverForTests: Boolean? = null

    @VisibleForTesting
    internal var networkLookupOverride: ((Network, String) -> List<InetAddress>)? = null

    /**
     * Resolves [hostname] on the current [ConnectivityManager.getActiveNetwork] snapshot.
     *
     * The returned [NetworkBoundDnsResult.network] is the network used for DNS and must be
     * reused for subsequent socket connections to avoid races when the active network changes.
     *
     * @throws UnknownHostException when resolution fails or no active network is available.
     */
    fun lookupBoundToActiveNetwork(hostname: String): NetworkBoundDnsResult {
        val network = connectivityManager.activeNetwork
            ?: throw UnknownHostException(hostname)

        val literalAddress = LiteralIpAddressParser.parse(hostname)
        if (literalAddress != null) {
            return NetworkBoundDnsResult(network = network, addresses = listOf(literalAddress))
        }

        val addresses = resolveOnNetwork(network, hostname)
        Timber.tag(TAG).d(
            "lookup(%s): resolved on active network -> %s",
            sensitive(hostname),
            sensitive { addresses.joinToString { it.hostAddress ?: it.toString() } },
        )
        return NetworkBoundDnsResult(network = network, addresses = addresses)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val literalAddress = LiteralIpAddressParser.parse(hostname)
        if (literalAddress != null) {
            return listOf(literalAddress)
        }

        val network = connectivityManager.activeNetwork
        if (network != null) {
            return lookupBoundToActiveNetwork(hostname).addresses
        }
        Timber.tag(TAG).w("lookup(%s): no active network, falling back to Dns.SYSTEM", sensitive(hostname))
        return Dns.SYSTEM.lookup(hostname)
    }

    override fun invoke(builder: OkHttpClient.Builder) {
        builder.dns(this)
        builder.socketFactory(ActiveNetworkSocketFactory(connectivityManager))
    }

    private fun resolveOnNetwork(network: Network, hostname: String): List<InetAddress> {
        networkLookupOverride?.let { return it(network, hostname) }
        return runOffMainThread {
            if (shouldUseDnsResolver()) {
                resolveWithDnsResolver(network, hostname)
            } else {
                NetworkBoundDnsLookup.lookup(
                    network = network,
                    connectivityManager = connectivityManager,
                    hostname = hostname,
                )
            }
        }
    }

    private fun resolveWithDnsResolver(network: Network, hostname: String): List<InetAddress> {
        return try {
            queryAllRecordTypes(network, hostname)
        } catch (e: UnknownHostException) {
            Timber.tag(TAG).w(
                e,
                "DnsResolver failed for %s, falling back to NetworkBoundDnsLookup",
                sensitive(hostname),
            )
            NetworkBoundDnsLookup.lookup(
                network = network,
                connectivityManager = connectivityManager,
                hostname = hostname,
            )
        }
    }

    private fun queryAllRecordTypes(network: Network, hostname: String): List<InetAddress> {
        val aaaaAddresses = try {
            queryDnsRecord(
                network = network,
                hostname = hostname,
                recordType = DnsResolver.TYPE_AAAA,
            )
        } catch (e: UnknownHostException) {
            Timber.tag(TAG).d(
                "AAAA lookup failed for %s: %s",
                sensitive(hostname),
                e.message,
            )
            emptyList()
        }
        val aAddresses = try {
            queryDnsRecord(
                network = network,
                hostname = hostname,
                recordType = DnsResolver.TYPE_A,
            )
        } catch (e: UnknownHostException) {
            Timber.tag(TAG).d(
                "A lookup failed for %s: %s",
                sensitive(hostname),
                e.message,
            )
            emptyList()
        }
        val addresses = (aaaaAddresses + aAddresses).distinct()
        if (addresses.isEmpty()) {
            throw UnknownHostException(hostname)
        }
        return addresses
    }

    private fun queryDnsRecord(network: Network, hostname: String, recordType: Int): List<InetAddress> {
        val dnsResolver = DnsResolver.getInstance()
        val latch = CountDownLatch(1)
        val result = mutableListOf<InetAddress>()

        dnsResolver.query(
            network,
            hostname,
            recordType,
            DnsResolver.FLAG_EMPTY,
            dnsMainExecutorForTests ?: mainExecutor,
            null,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    result.addAll(answer)
                    latch.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    Timber.tag(TAG).d(
                        "queryDnsRecord(%s, type=%d): %s",
                        sensitive(hostname),
                        recordType,
                        error.message,
                    )
                    latch.countDown()
                }
            },
        )

        if (!latch.await(DNS_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw UnknownHostException("DNS query timed out for ${sensitive(hostname)}")
        }
        return result
    }

    /**
     * Dispatches [block] to [dnsWorkerExecutor] when called from the main thread so the main looper
     * stays free to deliver [DnsResolver] file-descriptor events.
     */
    private fun <T> runOffMainThread(block: () -> T): T {
        if (!isMainThread()) {
            return block()
        }
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        workerExecutor().execute {
            try {
                result.set(block())
            } catch (throwable: Throwable) {
                error.set(throwable)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(MAIN_THREAD_DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw UnknownHostException("DNS resolution timed out waiting for background worker")
        }
        error.get()?.let { throw it }
        return checkNotNull(result.get()) { "DNS worker returned no result" }
    }

    private fun isMainThread(): Boolean {
        return isMainThreadForTests?.invoke() ?: (Looper.myLooper() == Looper.getMainLooper())
    }

    private fun workerExecutor(): Executor = dnsWorkerExecutorForTests ?: dnsWorkerExecutor

    private fun shouldUseDnsResolver(): Boolean {
        return useDnsResolverForTests ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    private companion object {
        private const val TAG = "NetworkAwareDns"
    }
}
