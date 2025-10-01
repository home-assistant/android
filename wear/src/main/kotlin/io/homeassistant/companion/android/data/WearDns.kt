@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.data

import androidx.annotation.VisibleForTesting
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.CAPABILITY_DNS_VIA_MOBILE
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.PATH_DNS_LOOKUP
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.decodeDNSResult
import io.homeassistant.companion.android.common.util.WearDataMessages.DnsLookup.encodeDNSRequest
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Dns
import timber.log.Timber

private val DefaultCacheLifetime = 5.minutes

/**
 * Wear specific implementation of Dns that defaults to the System DNS,
 * but can fall back to relying on mobile Dns to resolve dns issues.
 *
 * The request is made via the Wearable Data Layer, specifically MessageClient
 * sendRequest with path "/network/dnsLookup"
 *
 * This implementation caches both positive and negative DNS lookup results for 5 minutes
 * to minimize redundant lookups and reduce network traffic, especially when falling back
 * to mobile DNS.
 */
class WearDns @VisibleForTesting constructor(
    private val messageClient: MessageClient,
    private val capabilityClient: CapabilityClient,
    private val clock: Clock,
    private val dns: Dns,
) : Dns {

    @Inject
    constructor(
        messageClient: MessageClient,
        capabilityClient: CapabilityClient,
        clock: Clock,
    ) : this(messageClient, capabilityClient, clock, Dns.SYSTEM)

    private val dnsHelperCache = ConcurrentHashMap<String, CacheResult>()
    val cacheLifetime = DefaultCacheLifetime

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            dns.lookup(hostname)
        } catch (e: UnknownHostException) {
            return runBlocking {
                attemptLookupViaMobile(hostname, e)
            }
        }
    }

    private suspend fun attemptLookupViaMobile(hostname: String, e: Exception): List<InetAddress> {
        val now = clock.now()

        val cached = dnsHelperCache[hostname]
        if (cached != null && cached.storedAt + cacheLifetime > now) {
            when (cached) {
                is NegativeCacheHit -> throw cached.exception
                is PositiveCacheHit -> return cached.value
            }
        }

        try {
            val nodeId = nodeIdWithDns()
                ?: throw UnknownHostException("No Mobile DNS helper registered. Unable to resolve $hostname")
            val addresses = dnsViaMobile(nodeId, hostname)

            dnsHelperCache[hostname] = PositiveCacheHit(addresses, now)

            return addresses
        } catch (e2: Exception) {
            Timber.v(e, "DNS resolution using the system DNS failed, fallback onto mobile DNS lookup.")

            e2.addSuppressed(e)

            dnsHelperCache[hostname] = NegativeCacheHit(e2, now)

            throw e2
        }
    }

    private suspend fun nodeIdWithDns(): String? {
        val capability = capabilityClient
            .getCapability(CAPABILITY_DNS_VIA_MOBILE, CapabilityClient.FILTER_REACHABLE).await()

        return capability.nodes.firstNotNullOfOrNull { it.id }
    }

    private suspend fun dnsViaMobile(nodeId: String, hostname: String): List<InetAddress> {
        val response = messageClient.sendRequest(
            nodeId,
            PATH_DNS_LOOKUP,
            hostname.encodeDNSRequest(),
        ).await()

        if (response.isEmpty()) {
            throw UnknownHostException("Mobile helper unable to resolve $hostname")
        }

        val addresses = response.decodeDNSResult(hostname)

        return addresses
    }

    sealed interface CacheResult {
        val storedAt: Instant
    }

    private data class NegativeCacheHit(val exception: Exception, override val storedAt: Instant) : CacheResult
    private data class PositiveCacheHit(val value: List<InetAddress>, override val storedAt: Instant) : CacheResult
}
