package io.homeassistant.companion.android.onboarding.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.lang.Exception
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import okio.internal.commonToUtf8String

class HomeAssistantSearcher constructor(
    private val nsdManager: NsdManager,
    private val discoveryView: DiscoveryView
) : NsdManager.DiscoveryListener {

    companion object {
        private const val SERVICE_TYPE = "_home-assistant._tcp"

        private const val TAG = "HomeAssistantSearcher"

        private val lock = ReentrantLock()
    }

    private var isSearching = false

    fun beginSearch() {
        if (isSearching)
            return
        isSearching = true
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this)
        } catch (e: Exception) {
            Log.e(TAG, "Issue starting discover.", e)
            isSearching = false
            discoveryView.onScanError()
        }
    }

    fun stopSearch() {
        if (!isSearching)
            return
        isSearching = false
        nsdManager.stopServiceDiscovery(this)
    }

    // Called as soon as service discovery begins.
    override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onServiceFound(foundService: NsdServiceInfo) {
        Log.i(TAG, "Service discovery found HA: $foundService")
        lock.lock()
        nsdManager.resolveService(foundService, object : NsdManager.ResolveListener {
            override fun onResolveFailed(failedService: NsdServiceInfo?, errorCode: Int) {
                // discoveryView.onScanError()
                Log.w(TAG, "Failed to resolve service: $failedService, error: $errorCode")
                lock.unlock()
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo?) {
                Log.i(TAG, "Service resolved: $resolvedService")
                resolvedService?.let {
                    val baseUrl = it.attributes["base_url"]
                    val version = it.attributes["version"]
                    if (baseUrl != null && version != null) {
                        discoveryView.onInstanceFound(
                            HomeAssistantInstance(
                                it.serviceName,
                                URL(baseUrl.commonToUtf8String()),
                                version.commonToUtf8String()
                            )
                        )
                    }
                }
                lock.unlock()
            }
        })
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.e(TAG, "service lost: $service")
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Log.i(TAG, "Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        discoveryView.onScanError()
        stopSearch()
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        stopSearch()
    }
}
