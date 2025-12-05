package io.homeassistant.companion.android.common.data.servers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerDao
import java.net.URL
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import okhttp3.HttpUrl
import timber.log.Timber

class ServerConnectionStateProviderImpl @AssistedInject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
    private val serverDao: ServerDao,
    private val wifiHelper: WifiHelper,
    private val networkHelper: NetworkHelper,
    private val connectivityManager: ConnectivityManager,
    @Assisted private val serverId: Int,
) : ServerConnectionStateProvider {

    private suspend fun connection(): ServerConnectionInfo {
        return checkNotNull(serverManager.getServer(serverId)?.connection) {
            "No server found for id $serverId"
        }
    }

    override suspend fun isInternal(requiresUrl: Boolean): Boolean {
        val connection = connection()

        if (requiresUrl && connection.internalUrl.isNullOrBlank()) return false

        if (connection.internalEthernet == true) {
            val usesEthernet = networkHelper.isUsingEthernet()
            Timber.d("usesEthernet is: $usesEthernet")
            if (usesEthernet) return true
        }

        if (connection.internalVpn == true) {
            val usesVpn = networkHelper.isUsingVpn()
            Timber.d("usesVpn is: $usesVpn")
            if (usesVpn) return true
        }

        return if (connection.internalSsids.isNotEmpty()) {
            val usesInternalSsid = wifiHelper.isUsingSpecificWifi(connection.internalSsids)
            val usesWifi = wifiHelper.isUsingWifi()
            Timber.d("usesInternalSsid is: $usesInternalSsid, usesWifi is: $usesWifi")
            usesInternalSsid && usesWifi
        } else {
            false
        }
    }

    override suspend fun getExternalUrl(): URL? {
        val connection = connection()

        val url = if (connection.useCloud && connection.cloudUrl != null) {
            Timber.d("Using cloud / remote UI URL")
            connection.cloudHttpUrl?.toUrl()
        } else {
            Timber.d("Using external URL")
            connection.externalHttpUrl?.toUrl()
        }

        return url
    }

    override suspend fun getApiUrls(): List<URL> {
        val connection = connection()

        // If we don't have a webhook id we don't have any urls.
        if (connection.webhookId.isNullOrBlank()) {
            return emptyList()
        }

        val retVal = mutableListOf<URL?>()

        // If we are local then add the local URL in the first position, otherwise no reason to try
        if (isInternal() || connection.prioritizeInternal) {
            connection.internalHttpUrl?.let {
                retVal.add(it.buildWebhookUrl(connection.webhookId))
            }
        }

        connection.cloudHttpUrl?.let {
            retVal.add(it.toUrl())
        }

        connection.externalHttpUrl?.let {
            retVal.add(it.buildWebhookUrl(connection.webhookId))
        }

        return retVal.filterNotNull()
    }

    override suspend fun getSecurityInfo(): SecurityInfo {
        val connection = connection()

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val isLocationEnabled = DisabledLocationHandler.isLocationEnabled(context)

        return SecurityInfo(
            isOnInternalNetwork = isInternal(requiresUrl = false),
            hasHomeSetup = connection.hasHomeNetworkSetup,
            locationEnabled = hasLocationPermission && isLocationEnabled,
        )
    }

    override suspend fun canSafelySendCredentials(url: String): Boolean {
        if (url.startsWith("https://")) return true

        val connection = connection()
        if (!connection.isKnownUrl(url)) return false

        // URL is HTTP and belongs to this server - check if it's safe
        val allowInsecure = connection.allowInsecureConnection ?: true
        return allowInsecure || isInternal(requiresUrl = false)
    }

    // If permission is denied then the process restart so initial state is going to be updated

    // TODO this needs to be shared within this class to avoid multiple registration
    private fun observeConnectionInfoChanges(): Flow<ServerConnectionInfo> {
        return serverDao.observeServer(serverId)
            .mapNotNull { it?.connection }
            .distinctUntilChanged()
    }

    // TODO this could be cache too, the idea would be to have better logging experience regarding
    //  which URL is currently being used
    override fun urlFlow(isInternalOverride: ((ServerConnectionInfo) -> Boolean)?): Flow<UrlState> {
        return merge(
            flowOf(Unit), // Used to trigger a getUrl
            observeLocationState(),
            observeHomeNetworkState(),
            observeConnectionInfoChanges(),
        ).map {
            val connection = connection()
            val isInternal =
                isInternalOverride?.invoke(connection) ?: isInternal() ||
                    connection.prioritizeInternal &&
                    !DisabledLocationHandler.isLocationEnabled(
                        context,
                    )
            val potentialUrl = connection.getUrl(isInternal)
            // Null means the user hasn't configured their insecure connection preference yet.
            // This only applies to users who installed before it became mandatory in onboarding.
            // Default to true to maintain backwards compatibility with their existing setup.
            val allowInsecure = connection.allowInsecureConnection ?: true

            if (allowInsecure || potentialUrl?.protocol == "https") {
                UrlState.HasUrl(potentialUrl)
            } else {
                if (isInternal(requiresUrl = false)) {
                    UrlState.HasUrl(potentialUrl)
                } else {
                    UrlState.InsecureState
                }
            }
        }.distinctUntilChanged()
    }

    // TODO this needs to be shared across all the server
    private fun observeLocationState(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Emit initial state
        trySend(Unit)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    // TODO this needs to be shared across all the server
    private fun observeHomeNetworkState(): Flow<Unit> = callbackFlow {
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

        connectivityManager.registerNetworkCallback(networkRequest, callback)

        // Emit initial state
        trySend(Unit)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}

private fun HttpUrl.buildWebhookUrl(webhookId: String): URL {
    return newBuilder()
        .addPathSegments("api/webhook/$webhookId")
        .build()
        .toUrl()
}

private fun ServerConnectionInfo.getUrl(isInternal: Boolean): URL? {
    val url = if (isInternal && internalUrl != null) {
        Timber.d("Using internal URL")
        internalHttpUrl?.toUrl()
    } else if (useCloud && cloudUrl != null) {
        Timber.d("Using cloud / remote UI URL")
        cloudHttpUrl?.toUrl()
    } else {
        Timber.d("Using external URL")
        externalHttpUrl?.toUrl()
    }

    return url
}
