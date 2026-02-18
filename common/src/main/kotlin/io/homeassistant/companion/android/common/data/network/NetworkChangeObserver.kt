package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

/**
 * Observes network connectivity changes using [ConnectivityManager] and exposes them
 * as a [SharedFlow]. The underlying callback is shared across all subscribers.
 *
 * All components that need to react to network changes must use this shared observer
 * instead of registering their own [ConnectivityManager.NetworkCallback].
 * Android enforces a strict per-app limit on registered network callbacks and throws
 * `android.net.ConnectivityManager.TooManyRequestsException` when that limit is exceeded.
 * Sharing a single callback registration avoids hitting this limit.
 */
interface NetworkChangeObserver {
    /**
     * Emits [Unit] whenever the network state changes (available, lost, capabilities changed).
     * Replays the last emission to new subscribers.
     * The [ConnectivityManager] callback is registered while there are active subscribers.
     */
    val observerNetworkChange: Flow<Unit>
}

@Singleton
internal class NetworkChangeObserverImpl @VisibleForTesting constructor(
    private val connectivityManager: ConnectivityManager,
    private val scope: CoroutineScope
) : NetworkChangeObserver {

    @Inject
    constructor(
        connectivityManager: ConnectivityManager,
    ) : this(connectivityManager, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override val observerNetworkChange: Flow<Unit> = callbackFlow {
        val networkRequest = NetworkRequest.Builder().build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(Unit)
            }
        }

        Timber.d("Register network callback")
        connectivityManager.registerNetworkCallback(networkRequest, callback)

        trySend(Unit)

        awaitClose {
            Timber.d("Unregister network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        replay = 1,
    )
}
