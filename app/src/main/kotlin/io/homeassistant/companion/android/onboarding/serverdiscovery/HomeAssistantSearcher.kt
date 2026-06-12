package io.homeassistant.companion.android.onboarding.serverdiscovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.SdkVersion
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

@VisibleForTesting
const val SERVICE_TYPE = "_home-assistant._tcp"

@VisibleForTesting
const val LOCK_TAG = "HomeAssistantSearcher_lock"

internal data class HomeAssistantInstance(val name: String, val url: URL, val version: HomeAssistantVersion?)

/**
 * Interface responsible for discovering Home Assistant instances on the local network.
 */
internal interface HomeAssistantSearcher {
    /**
     * Returns a [Flow] that emits [HomeAssistantInstance] objects as they are discovered.
     *
     * This flow is designed for a single collector. Attempting to collect it multiple
     * times concurrently will lead to unpredictable behavior and crashes in debug builds.
     *
     * The discovery process starts when the flow is collected and stops when the collector
     * is cancelled or completes.
     *
     * @return A [Flow] of [HomeAssistantInstance].
     * @throws DiscoveryFailedException if the discovery process fails to start.
     */
    fun discoveredInstanceFlow(): Flow<HomeAssistantInstance>
}

internal class DiscoveryFailedException(message: String?) : Exception(message)

internal class HomeAssistantSearcherImpl @Inject constructor(
    private val nsdManager: NsdManager,
    private val wifiManager: WifiManager?,
) : HomeAssistantSearcher {

    companion object {
        /**
         * Tracks whether there is an active collector for [HomeAssistantSearcher.discoveredInstanceFlow].
         * This class is designed to be a singleton, instantiated once and injected with Hilt.
         * The static [AtomicBoolean] acts as a safeguard against potential Hilt misconfiguration
         * (hence its static nature) or unintended multiple concurrent collections of the flow.
         * It ensures that only one collector can be active at any given time.
         */
        @VisibleForTesting
        val hasCollector = AtomicBoolean(false)
    }

    override fun discoveredInstanceFlow(): Flow<HomeAssistantInstance> {
        FailFast.failWhen(hasCollector.get()) {
            "Something has already called discoveredInstanceFlow() and didn't close the flow yet."
        }

        return serviceFlow
            .onCompletion {
                hasCollector.set(false)
            }
            .onStart {
                FailFast.failWhen(hasCollector.get()) {
                    "Something already started to collect, this flow is designed to only be collected by one collector at the time."
                }
                hasCollector.set(true)
            }
    }

    /**
     * A [Flow] that emits [NsdServiceInfo] objects as they are discovered on the network.
     *
     * To improve discovery reliability on some devices, this flow acquires a multicast lock
     * from [WifiManager] if available.
     *
     * When this flow is collected, it registers a [NsdManager.DiscoveryListener] and acquires the
     * [WifiManager.MulticastLock].
     *
     * When the collection of this flow is cancelled or completes, it unregisters the
     * [NsdManager.DiscoveryListener] and releases the [WifiManager.MulticastLock].
     */
    private val nsdDiscoveryFlow: Flow<NsdServiceInfo> = callbackFlow {
        val listener = getDiscoveryListener()

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener,
        )

        val multicastLock = wifiManager?.let {
            try {
                it.createMulticastLock(LOCK_TAG)?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to acquire multicast lock")
                null
            }
        }
        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
                Timber.d("Stop discovery")
                multicastLock?.release()
                Timber.d("Release multicast lock")
            } catch (e: Exception) {
                Timber.e(e, "Failed to release resources")
            }
        }
    }

    /**
     * A [Flow] that resolves discovered [NsdServiceInfo] objects into [HomeAssistantInstance] objects.
     *
     * This flow processes each [NsdServiceInfo] emitted by [nsdDiscoveryFlow] one at a time
     * to avoid overwhelming the system with simultaneous resolve requests.
     * For more details, see: https://github.com/home-assistant/android/pull/664.
     *
     * For each [NsdServiceInfo], it attempts to resolve the service using
     * [NsdManager.resolveService].
     *
     * - On successful resolution, it parses the service attributes to create a
     *   [HomeAssistantInstance] and emits it.
     * - If resolution fails or the required attributes (base_url, version) are missing or invalid,
     *   it logs a warning and skips the service.
     *
     * After each resolution (success or failure), it closes the flow and
     * it attempts to stop the service resolution using [NsdManager.stopServiceResolution] (on API 34+).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceFlow: Flow<HomeAssistantInstance> = nsdDiscoveryFlow.flatMapMerge(
        concurrency = 1,
    ) { serviceInfo ->
        Timber.d("Got service info $serviceInfo")

        callbackFlow {
            val listener = getResolvedListener()

            @Suppress("DEPRECATION")
            // We cannot use registerServiceInfoCallback since it is only available in API 34
            nsdManager.resolveService(serviceInfo, listener)

            awaitClose {
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
                    try {
                        nsdManager.stopServiceResolution(listener)
                    } catch (e: IllegalArgumentException) {
                        // On devices with T SDK extension < 22, NsdManager throws when the
                        // listener is already unregistered (which happens automatically after
                        // onServiceResolved/onResolveFailed fires). The call is still needed
                        // for devices where auto-unregistration is not guaranteed, so we keep
                        // it and just downgrade the expected case to a warning without a stack.
                        Timber.w("stopServiceResolution: listener already unregistered: ${e.message}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to stop service resolution")
                    }
                }
                Timber.d("Closing service resolving flow for $serviceInfo")
            }
        }
    }
}

private fun ProducerScope<NsdServiceInfo>.getDiscoveryListener(): NsdManager.DiscoveryListener {
    return object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            Timber.d("Service discovery started")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Timber.d("Service discovery stopped")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            serviceInfo?.let {
                Timber.i("Service discovery found HA: $serviceInfo")
                trySend(serviceInfo)
            } ?: Timber.w("Service found but info is null, skipping")
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Timber.d("Service lost: $serviceInfo")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            close(DiscoveryFailedException("Start discovery failed with error code $errorCode"))
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Timber.e("Stop discovery failed with error code $errorCode")
        }
    }
}

private fun ProducerScope<HomeAssistantInstance>.getResolvedListener(): NsdManager.ResolveListener {
    return object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Timber.w("Failed to resolve information for service: $serviceInfo, error code $errorCode skipping")
            close()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
            Timber.d("Service resolved: $serviceInfo")
            serviceInfo?.toHomeAssistantInstance()?.let { instance ->
                trySend(instance)
            }
            close()
        }
    }
}

private fun NsdServiceInfo.attribute(key: String): String? =
    attributes?.get(key)?.toString(Charsets.UTF_8)?.ifBlank { null }

private fun NsdServiceInfo.toHomeAssistantInstance(): HomeAssistantInstance? {
    val urlString = attribute("external_url") ?: attribute("internal_url")
    if (urlString.isNullOrBlank()) {
        Timber.w("URL is missing or empty in NSD attributes for service: $this")
        return null
    }

    val url = try {
        URL(urlString)
    } catch (e: MalformedURLException) {
        Timber.w(e, "Invalid url format: $urlString for service: $this")
        return null
    }

    // The version is optional: a discovered instance is still usable without it, the instance might simply be on the
    // https://github.com/home-assistant/landingpage/.
    val haVersion = attribute("version")?.let { HomeAssistantVersion.fromString(it) }
    if (haVersion == null) {
        Timber.w("Version is missing in NSD attributes for service: $serviceName")
    }

    return HomeAssistantInstance(
        serviceName,
        url,
        haVersion,
    )
}
