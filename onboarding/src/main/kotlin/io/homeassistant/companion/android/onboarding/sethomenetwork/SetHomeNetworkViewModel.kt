package io.homeassistant.companion.android.onboarding.sethomenetwork

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.SetHomeNetworkRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SetHomeNetworkViewModel @VisibleForTesting constructor(
    private val serverId: Int,
    private val serverManager: ServerManager,
    connectivityManager: ConnectivityManager,
    wifiHelper: WifiHelper,
) : ViewModel() {

    @Inject
    constructor(
        connectivityManager: ConnectivityManager,
        savedStateHandle: SavedStateHandle,
        serverManager: ServerManager,
        wifiHelper: WifiHelper,
    ) : this(
        serverId = savedStateHandle.toRoute<SetHomeNetworkRoute>().serverId,
        serverManager,
        connectivityManager,
        wifiHelper,
    )

    /**
     * We only focus on the SSID here to make things simpler for most of our users. Anyone that wants
     * something more advance like using BSSID should do it later from the settings.
     */
    private val _currentWifiNetwork =
        MutableStateFlow(wifiHelper.getWifiSsid()?.removeSurrounding("\"") ?: "")
    val currentWifiNetwork = _currentWifiNetwork.asStateFlow()

    val hasEthernetConnection: Boolean = connectivityManager.hasEthernetConnection()
    val hasVPNConnection: Boolean = connectivityManager.hasVPNConnection()

    private val _isUsingEthernet = MutableStateFlow(hasEthernetConnection)
    val isUsingEthernet = _isUsingEthernet.asStateFlow()

    private val _isUsingVpn = MutableStateFlow(hasVPNConnection)
    val isUsingVpn = _isUsingVpn.asStateFlow()

    fun onCurrentWifiNetworkChange(value: String) {
        _currentWifiNetwork.value = value
    }

    fun onUsingEthernetChange(value: Boolean) {
        _isUsingEthernet.value = value
    }

    fun onUsingVpnChange(value: Boolean) {
        _isUsingVpn.value = value
    }

    fun onNextClick() {
        viewModelScope.launch {
            val currentWifiNetwork = currentWifiNetwork.value
            serverManager.getServer(serverId)?.let { server ->
                serverManager.updateServer(
                    server.copy(
                        connection = server.connection.copy(
                            internalVpn = isUsingVpn.value,
                            internalEthernet = isUsingEthernet.value,
                            // We don't want to add an empty network in the list
                            internalSsids = currentWifiNetwork.takeIf { it.isNotBlank() }
                                ?.let { listOf(currentWifiNetwork) } ?: emptyList(),
                        ),
                    ),
                )
            } ?: Timber.e("Server not found cannot set the connection information")
        }
    }
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.hasEthernetConnection(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        allNetworks.any {
            getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
    } else {
        getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)?.isConnected == true
    }
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.hasVPNConnection(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        allNetworks.any {
            getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    } else {
        getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected == true
    }
}
