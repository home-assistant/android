package io.homeassistant.companion.android.settings.ssid

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SsidViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
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

    init {
        viewModelScope.launch {
            wifiSsids.clear()
            wifiSsids.addAll(urlRepository.getHomeWifiSsids())
            prioritizeInternal = urlRepository.isPrioritizeInternal()
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
            urlRepository.saveHomeWifiSsids(ssids.toSet())
            wifiSsids.clear()
            wifiSsids.addAll(ssids)
        }
    }

    fun setPrioritize(prioritize: Boolean) {
        viewModelScope.launch {
            urlRepository.setPrioritizeInternal(prioritize)
            prioritizeInternal = prioritize
        }
    }
}
