package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryMode
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.util.delayFirstThrottle
import java.net.URL
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@VisibleForTesting
val TIMEOUT_NO_SERVER_FOUND = 5.seconds

@VisibleForTesting
val DELAY_BEFORE_DISPLAY_DISCOVERY = 1.5.seconds

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
internal class ServerDiscoveryViewModel @VisibleForTesting constructor(
    private val discoveryMode: ServerDiscoveryMode,
    private val searcher: HomeAssistantSearcher,
    private val serverManager: ServerManager,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        searcher: HomeAssistantSearcher,
        serverManager: ServerManager,
    ) : this(savedStateHandle.toRoute<ServerDiscoveryRoute>().discoveryMode, searcher, serverManager)

    private val _discoveryFlow = MutableStateFlow<DiscoveryState>(Started)

    /**
     * A flow that emits the current [DiscoveryState] of the server discovery process.
     *
     * The flow starts with a delay of [DELAY_BEFORE_DISPLAY_DISCOVERY]
     * before emitting any subsequent states. Once servers are discovered, the flow will emit
     * [ServerDiscovered] for a single server or [ServersDiscovered] for multiple servers.
     *
     * If no server is found after [TIMEOUT_NO_SERVER_FOUND], the state transitions to [NoServerFound].
     */
    val discoveryFlow = _discoveryFlow.delayFirstThrottle(DELAY_BEFORE_DISPLAY_DISCOVERY)

    init {
        discoverInstances()

        watchForNoServerFound()
    }

    private suspend fun serversToIgnore(): List<URL> {
        return if (discoveryMode == ServerDiscoveryMode.HIDE_EXISTING) {
            serverManager.servers()
                .flatMap { server ->
                    with(server.connection) {
                        listOf(internalUrl, externalUrl, cloudUrl)
                    }
                }
                .filterNotNull()
                .mapNotNull { url ->
                    runCatching { URL(url) }
                        .onFailure { Timber.d(it, "Invalid URL for: $url") }
                        .getOrNull()
                }
        } else {
            emptyList()
        }
    }

    private fun discoverInstances() {
        viewModelScope.launch {
            if (discoveryMode == ServerDiscoveryMode.ADD_EXISTING) {
                _discoveryFlow.value = getExistingInstances(serverManager)
            }
            try {
                val serversToIgnore = serversToIgnore()
                searcher.discoveredInstanceFlow()
                    .filter { instanceFound ->
                        serversToIgnore.none { it.isSameServer(instanceFound.url) }
                    }
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

    private fun onServerDiscovered(serverDiscovered: ServerDiscovered) {
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
                    serverDiscovered
                }
            }
        }
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

private suspend fun getExistingInstances(serverManager: ServerManager): DiscoveryState {
    return serverManager.servers()
        .mapNotNull { server ->
            val url =
                serverManager.connectionStateProvider(server.id).getExternalUrl() ?: return@mapNotNull null
            val version = server.version ?: return@mapNotNull null
            ServerDiscovered(server.friendlyName, url, version)
        }
        .takeIf { it.isNotEmpty() }
        ?.let { ServersDiscovered(it) }
        ?: Started
}

private fun URL.isSameServer(other: URL): Boolean =
    protocol == other.protocol && host == other.host && port == other.port
