package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import java.net.URL
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface DiscoveryState

object NoServerFound : DiscoveryState

data class ServerDiscovered(
    val name: String,
    val url: URL,
    val version: HomeAssistantVersion,
) : DiscoveryState

// This list could grow
data class ServersDiscovered(
    val servers: List<ServerDiscovered>,
) : DiscoveryState

@HiltViewModel
class ServerDiscoveryViewModel @Inject constructor() : ViewModel() {
    /**
     * Logic of the screen:
     * We keep the discovery animation for 1s then
     * - We only have 1 server: we show it in the modal
     * - We have multiple servers: we show the list
     * - We have no servers: we show no item found
     */

    // If not null, we show the one server found modal
    val discoveryState = mutableStateOf<DiscoveryState?>(null)

    init {
        viewModelScope.launch {
            delay(2.seconds)
            discoveryState.value = ServerDiscovered("Mr Green", URL("http://192.168.1.1"), HomeAssistantVersion(2042, 1, 42))
        }
    }

    /**
     * Invoke when the user dismiss the server found.
     * It is going to emit a new [DiscoveryState]
     */
    fun onDismissOneServerFound() {
        discoveryState.value = ServersDiscovered(emptyList())
    }
}
