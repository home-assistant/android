package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.util.delayFirst
import java.net.URL
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@VisibleForTesting
val TIMEOUT_NO_SERVER_FOUND = 5.seconds

@VisibleForTesting
val DELAY_BEFORE_DISPLAY_DISCOVERY = 1.seconds

@VisibleForTesting
val DELAY_AFTER_FIRST_DISCOVERY = 5.seconds

/**
 * Represents the current state of the server discovery process.
 */
sealed interface DiscoveryState

/**
 * Indicates that the server discovery process has started. It doesn't mean
 * that it is running. If the discovery failed to start after [TIMEOUT_NO_SERVER_FOUND]
 * the state will be [NoServerFound].
 */
data object Started : DiscoveryState

/**
 * State display after [TIMEOUT_NO_SERVER_FOUND] without any server found.
 */
data object NoServerFound : DiscoveryState

/**
 * The first server has been fully discovered.
 */
data class ServerDiscovered(val name: String, val url: URL, val version: HomeAssistantVersion) : DiscoveryState

/**
 * Multiple server have been fully discovered.
 */
data class ServersDiscovered(val servers: List<ServerDiscovered>) : DiscoveryState

/**
 * ViewModel responsible for managing the Home Assistant server discovery process.
 * It uses [HomeAssistantSearcher] to find instances on the local network
 * and emits the [DiscoveryState] through [discoveryFlow] state flow.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class ServerDiscoveryViewModel @Inject constructor(private val searcher: HomeAssistantSearcher) : ViewModel() {
    private val _discoveryFlow = MutableStateFlow<DiscoveryState>(Started)

    /**
     * A flow that emits the current [DiscoveryState] of the server discovery process.
     *
     * The discovery process includes the following delays:
     * - [DELAY_BEFORE_DISPLAY_DISCOVERY]: No discovery events are emitted before this delay.
     * - [DELAY_AFTER_FIRST_DISCOVERY]: Applied after the first server is discovered ([ServerDiscovered]),
     * before any subsequent [ServersDiscovered].
     *
     * If no server is found after [TIMEOUT_NO_SERVER_FOUND], the state transitions to [NoServerFound].
     */
    val discoveryFlow = _discoveryFlow.asStateFlow()

    init {
        discoverInstances()

        watchForNoServerFound()
    }

    private fun discoverInstances() {
        viewModelScope.launch {
            try {
                searcher.discoveredInstanceFlow()
                    .delayFirst(DELAY_BEFORE_DISPLAY_DISCOVERY)
                    .collect { instanceFound ->
                        val serverDiscovered = ServerDiscovered(
                            instanceFound.name,
                            instanceFound.url,
                            instanceFound.version,
                        )
                        onServerDiscovered(serverDiscovered)
                    }
            } catch (e: DiscoveryFailedException) {
                Timber.e(e, "Discovery failed and won't find any instances")
            }
        }
    }

    private suspend fun onServerDiscovered(serverDiscovered: ServerDiscovered) {
        var shouldDelayNext = false

        _discoveryFlow.update {
            when (it) {
                is ServersDiscovered -> {
                    // Avoid duplicates if the server was already found
                    if (it.servers.contains(serverDiscovered)) {
                        it
                    } else {
                        it.copy(servers = it.servers + serverDiscovered)
                    }
                }

                is ServerDiscovered -> ServersDiscovered(
                    listOf(
                        it,
                        serverDiscovered,
                    ),
                )

                is Started, is NoServerFound -> {
                    shouldDelayNext = true
                    serverDiscovered
                }
            }
        }
        if (shouldDelayNext) delay(DELAY_AFTER_FIRST_DISCOVERY)
    }

    private fun watchForNoServerFound() {
        _discoveryFlow
            .takeWhile { it is Started }
            .timeout(TIMEOUT_NO_SERVER_FOUND)
            .catch {
                if (it is TimeoutCancellationException) {
                    _discoveryFlow.update { NoServerFound }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Call this function when the user dismisses the dialog that shows a single discovered server.
     * This action transitions the state to show a list containing the dismissed server.
     */
    fun onDismissOneServerFound() {
        _discoveryFlow.update {
            if (it is ServerDiscovered) {
                ServersDiscovered(listOf(it))
            } else {
                it
            }
        }
    }
}
