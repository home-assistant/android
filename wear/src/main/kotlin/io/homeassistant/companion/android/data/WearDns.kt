package io.homeassistant.companion.android.data

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Wear specific implementation of Dns that defaults to the System DNS,
 * but can fall back to relying on mobile Dns to resolve dns issues.
 */
class WearDns @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        println("lookup $hostname")
        return try {
            Dns.SYSTEM.lookup(hostname).also {
                println("results $it")
            }
        } catch (e: Exception) {
            println("$e")
            if (couldBeWearDnsIssue(e)) {
                val result = runBlocking { attemptLookupViaMobile(hostname, e) }

                if (result != null) {
                    return result
                }

                // TODO consider requesting wifi instead of relying on Mobile
            }

            throw e
        }
    }

    private suspend fun attemptLookupViaMobile(
        hostname: String,
        e: Exception
    ): List<InetAddress>? {
        try {
            val nodeId = nodeIdWithDns() ?: return null
            return dnsViaMobile(nodeId, hostname)
        } catch (e2: Exception) {
            println("helper $e2")
            throw e2.apply { addSuppressed(e) }
        }
    }

    private suspend fun nodeIdWithDns(): String? {
        val capability = Wearable.getCapabilityClient(appContext).getCapability(CAPABILITY_DNS_VIA_MOBILE, CapabilityClient.FILTER_REACHABLE).await()

        return capability.nodes.firstNotNullOfOrNull { it.id }.also { println("found node $it") }
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
        return listOf(InetAddress.getByAddress(hostname, response)).also { println("dns results $it") }
    }

    private fun couldBeWearDnsIssue(e: Exception): Boolean {
        // TODO define what a Wear Dns BT failure looks like
        return true
    }

    companion object {
        private const val CAPABILITY_DNS_VIA_MOBILE = "mobile_network_helper"
        private const val REQUEST_DNS_VIA_MOBILE = "/dns_lookup"
    }
}