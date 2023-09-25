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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SsidViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
    application: Application
) : AndroidViewModel(application) {

    var wifiSsids = mutableStateListOf<String>()
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
            server?.connection?.prioritizeInternal?.let { prioritizeInternal = it }

            usingWifi = wifiHelper.isUsingWifi()
            activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")
            activeBssid = wifiHelper.getWifiBssid()
        }
    }

    /**
     * Add a new SSID to the list of SSIDs where the internal connection URL should be used.
     *
     * @return `true` if the SSID was successfully added
     */
    fun addHomeWifiSsid(ssid: String): Boolean {
        if (ssid.isBlank() || wifiSsids.any { it == ssid.trim() }) return false
        setHomeWifiSsids((wifiSsids + ssid.trim()).sorted())
        return true
    }

    fun removeHomeWifiSsid(ssid: String) = setHomeWifiSsids(wifiSsids - ssid)

    private fun setHomeWifiSsids(ssids: List<String>) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalSsids = ssids
                        )
                    )
                )
            }
            wifiSsids.clear()
            wifiSsids.addAll(ssids)
        }
    }

    fun setPrioritize(prioritize: Boolean) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            prioritizeInternal = prioritize
                        )
                    )
                )
                prioritizeInternal = prioritize
            }
        }
    }
}
