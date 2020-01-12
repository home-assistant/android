package io.homeassistant.companion.android.onboarding.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.URL
import okio.internal.commonToUtf8String

class HomeAssistantSearcher constructor(
    private val nsdManager: NsdManager
) : NsdManager.DiscoveryListener {

    companion object {
        private const val SERVICE_TYPE = "_home-assistant._tcp."

        private const val TAG = "HomeAssistantSearcher"
    }

    private var isSearching = false

    val foundInstances = arrayListOf<HomeAssistantInstance>()

    fun beginSearch() {
        if (isSearching)
            return
        isSearching = true
        nsdManager.discoverServices("_home-assistant._tcp", NsdManager.PROTOCOL_DNS_SD, this)
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

    override fun onServiceFound(service: NsdServiceInfo) {
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success: $service")

        if (service.serviceType == SERVICE_TYPE) {
            Log.i(TAG, "Service discovery found HA: $service")
//            Thread.sleep(50)
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "Failed to resolve service: $service, error: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "Service resolved: $serviceInfo")
                    serviceInfo?.let {
                        foundInstances.add(HomeAssistantInstance(
                            serviceInfo.serviceName,
                            URL(serviceInfo.attributes["base_url"]!!.commonToUtf8String()),
                            serviceInfo.attributes["version"]!!.commonToUtf8String()))
                    }
                }
            })
        }
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
        stopSearch()
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        stopSearch()
    }
}
