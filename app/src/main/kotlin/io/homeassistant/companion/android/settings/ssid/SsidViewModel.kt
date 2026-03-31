package io.homeassistant.companion.android.settings.ssid

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SsidViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
    application: Application,
) : AndroidViewModel(application) {

    var wifiSsids = mutableStateListOf<String>()
        private set

    var ethernet by mutableStateOf<Boolean?>(null)
        private set

    var vpn by mutableStateOf<Boolean?>(null)
        private set

    var prioritizeInternal by mutableStateOf(false)
        private set

    var usingWifi by mutableStateOf(false)
        private set

    var activeSsid by mutableStateOf<String?>(null)
        private set

    var activeBssid by mutableStateOf<String?>(null)
        private set

    private var serverId = -1

    init {
        state.get<Int>(SsidFragment.EXTRA_SERVER)?.let { serverId = it }
        viewModelScope.launch {
            val server = serverManager.getServer(serverId)
            wifiSsids.clear()
            wifiSsids.addAll(server?.connection?.internalSsids.orEmpty())
            ethernet = server?.connection?.internalEthernet
            vpn = server?.connection?.internalVpn
            server?.connection?.prioritizeInternal?.let { prioritizeInternal = it }

            updateWifiState()
        }
    }

    /**
     * Add a new SSID to the list of SSIDs where the internal connection URL should be used.
     *
     * @return `true` if the SSID was successfully added
     */
    fun addHomeWifiSsid(ssid: String): Boolean {
        if (ssid.isEmpty()) return false
        if (wifiSsids.contains(ssid)) return false
        setHomeWifiSsids((wifiSsids + ssid).sorted())
        return true
    }

    fun removeHomeWifiSsid(ssid: String) = setHomeWifiSsids(wifiSsids - ssid)

    private fun setHomeWifiSsids(ssids: List<String>) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalSsids = ssids,
                        ),
                    ),
                )
            }
            wifiSsids.clear()
            wifiSsids.addAll(ssids)
        }
    }

    fun updateWifiState() {
        try {
            usingWifi = wifiHelper.isUsingWifi()
            activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")
            activeBssid = wifiHelper.getWifiBssid()
        } catch (e: Exception) {
            Timber.w(e, "Unable to update Wi-Fi state")
        }
    }

    fun setInternalWithEthernet(eth: Boolean) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalEthernet = eth,
                        ),
                    ),
                )
                ethernet = eth
            }
        }
    }

    fun setInternalWithVpn(privateNetwork: Boolean) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalVpn = privateNetwork,
                        ),
                    ),
                )
                vpn = privateNetwork
            }
        }
    }

    fun setPrioritize(prioritize: Boolean) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            prioritizeInternal = prioritize,
                        ),
                    ),
                )
                prioritizeInternal = prioritize
            }
        }
    }
}
