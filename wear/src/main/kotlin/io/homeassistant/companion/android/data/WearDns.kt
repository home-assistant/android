package io.homeassistant.companion.android.data

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Dns

/**
 * Wear specific implementation of Dns that defaults to the System DNS,
 * but can fall back to relying on mobile Dns to resolve dns issues.
 */
class WearDns @Inject constructor(
    @ApplicationContext private val appContext: Context
) : Dns {
    private val dnsHelperCache = ConcurrentHashMap<String, CacheResult>()

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            if (couldBeWearDnsIssue(e)) {
                val result = runBlocking { attemptLookupViaMobile(hostname, e) }

                if (result != null) {
                    return result
                }
            }

            throw e
        }
    }

    private suspend fun attemptLookupViaMobile(
        hostname: String,
        e: Exception
    ): List<InetAddress>? {
        val now = Instant.now()

        val cached = dnsHelperCache[hostname]
        if (cached != null && cached.storedAt.plus(5L, ChronoUnit.MINUTES) > now) {
            when (cached) {
                is NegativeCacheHit -> throw cached.exception
                is PositiveCacheHit -> return cached.value
            }
        }

        try {
            val nodeId = nodeIdWithDns() ?: return null
            val addresses = dnsViaMobile(nodeId, hostname)

            dnsHelperCache[hostname] = PositiveCacheHit(addresses, now)

            return addresses
        } catch (e2: Exception) {
            e2.addSuppressed(e)

            dnsHelperCache[hostname] = NegativeCacheHit(e2, now)

            throw e2
        }
    }

    private suspend fun nodeIdWithDns(): String? {
        val capability = Wearable.getCapabilityClient(appContext).getCapability(CAPABILITY_DNS_VIA_MOBILE, CapabilityClient.FILTER_REACHABLE).await()

        return capability.nodes.firstNotNullOfOrNull { it.id }
    }

    private suspend fun dnsViaMobile(nodeId: String, hostname: String): List<InetAddress> {
        val response = Wearable.getMessageClient(appContext).sendRequest(
            nodeId,
            REQUEST_DNS_VIA_MOBILE,
            hostname.toByteArray()
        ).await()

        if (response.isEmpty()) {
            throw UnknownHostException("Mobile helper unable to resolve $hostname")
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        return listOf(InetAddress.getByAddress(hostname, response))
    }

    private fun couldBeWearDnsIssue(e: Exception): Boolean {
        return e is UnknownHostException
    }

    sealed interface CacheResult {
        val storedAt: Instant
    }

    data class NegativeCacheHit(val exception: Exception, override val storedAt: Instant) : CacheResult
    data class PositiveCacheHit(val value: List<InetAddress>, override val storedAt: Instant) : CacheResult

    companion object {
        private const val CAPABILITY_DNS_VIA_MOBILE = "mobile_network_helper"
        private const val REQUEST_DNS_VIA_MOBILE = "/dnsLookup"
    }
}
