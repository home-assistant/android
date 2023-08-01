package io.homeassistant.companion.android.onboarding.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import okio.internal.commonToUtf8String
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.locks.ReentrantLock

class HomeAssistantSearcher constructor(
    private val nsdManager: NsdManager,
    private val wifiManager: WifiManager?,
    private val onInstanceFound: (instance: HomeAssistantInstance) -> Unit,
    private val onError: () -> Unit
) : NsdManager.DiscoveryListener, DefaultLifecycleObserver {

    companion object {
        private const val SERVICE_TYPE = "_home-assistant._tcp"

        private const val TAG = "HomeAssistantSearcher"

        private val lock = ReentrantLock()
    }

    private var isSearching = false

    private var multicastLock: WifiManager.MulticastLock? = null

    private fun beginSearch() {
        if (isSearching) {
            return
        }
        isSearching = true
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this)
        } catch (e: Exception) {
            Log.e(TAG, "Issue starting discover.", e)
            isSearching = false
            onError()
            return
        }
        try {
            if (wifiManager != null && multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock(TAG)
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Issue acquiring multicast lock", e)
            // Discovery might still work so continue
        }
    }

    fun stopSearch() {
        if (!isSearching) {
            return
        }
        isSearching = false
        try {
            nsdManager.stopServiceDiscovery(this)
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Issue stopping discovery", e)
        }
    }

    override fun onResume(owner: LifecycleOwner) = beginSearch()

    override fun onPause(owner: LifecycleOwner) = stopSearch()

    // Called as soon as service discovery begins.
    override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onServiceFound(foundService: NsdServiceInfo) {
        Log.i(TAG, "Service discovery found HA: $foundService")
        lock.lock()
        nsdManager.resolveService(
            foundService,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(failedService: NsdServiceInfo?, errorCode: Int) {
                    // discoveryView.onScanError()
                    Log.w(TAG, "Failed to resolve service: $failedService, error: $errorCode")
                    lock.unlock()
                }

                override fun onServiceResolved(resolvedService: NsdServiceInfo?) {
                    Log.i(TAG, "Service resolved: $resolvedService")
                    resolvedService?.let {
                        val baseUrl = it.attributes["base_url"]
                        val versionAttr = it.attributes["version"]
                        val version = if (versionAttr?.isNotEmpty() == true) HomeAssistantVersion.fromString(versionAttr.commonToUtf8String()) else null
                        if (baseUrl?.isNotEmpty() == true && version != null) {
                            try {
                                val instance = HomeAssistantInstance(
                                    it.serviceName,
                                    URL(baseUrl.commonToUtf8String()),
                                    version
                                )
                                onInstanceFound(instance)
                            } catch (e: MalformedURLException) {
                                Log.w(TAG, "Failed to create instance: ${baseUrl.commonToUtf8String()}")
                            }
                        }
                    }
                    lock.unlock()
                }
            }
        )
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
        onError()
        stopSearch()
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        stopSearch()
    }
}
