package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import java.net.URL
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DiscoveryState

data object Scanning : DiscoveryState

data object NoServerFound : DiscoveryState

data class ServerDiscovered(val name: String, val url: URL, val version: HomeAssistantVersion) : DiscoveryState

// This list could grow
data class ServersDiscovered(val servers: List<ServerDiscovered>) : DiscoveryState

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
    private val discoveryStateMutableFlow = MutableStateFlow<DiscoveryState>(Scanning)
    val discoveryStateFlow: StateFlow<DiscoveryState> = discoveryStateMutableFlow

    init {
        viewModelScope.launch {
            delay(1.seconds)
            discoveryStateMutableFlow.update { NoServerFound }
            delay(3.seconds)
            discoveryStateMutableFlow.update {
                ServerDiscovered(
                    "Mr Green",
                    URL("http://192.168.1.1"),
                    HomeAssistantVersion(2042, 1, 42),
                )
            }
            delay(2.seconds)
            discoveryStateMutableFlow.update {
                ServersDiscovered(
                    listOf(
                        ServerDiscovered(
                            "Mr Green",
                            URL("http://192.168.1.1"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "1",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                    ),
                )
            }
            delay(1.seconds)
            discoveryStateMutableFlow.update {
                ServersDiscovered(
                    listOf(
                        ServerDiscovered(
                            "Mr Green",
                            URL("http://192.168.1.1"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "1",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "2",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                    ),
                )
            }
            delay(1.seconds)
            discoveryStateMutableFlow.update {
                ServersDiscovered(
                    listOf(
                        ServerDiscovered(
                            "Mr Green",
                            URL("http://192.168.1.1"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "1",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "2",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                        ServerDiscovered(
                            "3",
                            URL("http://ohf.org"),
                            HomeAssistantVersion(2042, 1, 42),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Invoke when the user dismiss the server found.
     * It is going to emit a new [DiscoveryState]
     */
    fun onDismissOneServerFound() {
        discoveryStateMutableFlow.update { Scanning }
    }
}
