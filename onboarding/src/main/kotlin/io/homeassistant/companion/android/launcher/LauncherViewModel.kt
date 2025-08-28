package io.homeassistant.companion.android.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sealed interface for navigation events that can occur in the launcher.
 * These events are used to determine where the user should be navigated
 * within the app upon launch.
 */
internal sealed interface LauncherNavigationEvent {
    data object Frontend : LauncherNavigationEvent
    data object Onboarding : LauncherNavigationEvent
}

/**
 * ViewModel for the launcher activity. Upon instantiation, it checks for servers to remove
 * and verifies the presence of an active, registered, and connected server.
 *
 * If no such server is found, or if an error occurs during this check (e.g., network connectivity issues),
 * it emits [LauncherNavigationEvent.Onboarding]. Otherwise, it proceeds to resync all server
 * registrations and emits [LauncherNavigationEvent.Frontend].
 *
 * Call [shouldShowSplashScreen] to determine when to hide the application's splash screen.
 */
@HiltViewModel
internal class LauncherViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
) : ViewModel() {

    private val _navigationEventsFlow = MutableSharedFlow<LauncherNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            cleanupServers()

            try {
                getActiveServerConnectedAndRegistered()?.let { server ->
                    Timber.d("Active server (id=${server.id}) is connected and registered checking network status")

                    networkStatusMonitor.observeNetworkStatus(server.connection)
                        .takeWhile { state ->
                            // Until the network is ready we continue to observe network status changes
                            !handleNetworkState(state)
                        }.collect()
                } ?: navigateToOnboarding()
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Something went wrong while checking if any server are registered with a connected session")
                navigateToOnboarding()
            }
        }
    }

    fun shouldShowSplashScreen(): Boolean = navigationEventsFlow.replayCache.isEmpty()

    private suspend fun getActiveServerConnectedAndRegistered(): Server? {
        return serverManager.getServer(ServerManager.SERVER_ID_ACTIVE)?.takeIf {
            serverManager.isRegistered() &&
                serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
        }
    }

    private suspend fun navigateToOnboarding() {
        _navigationEventsFlow.emit(LauncherNavigationEvent.Onboarding)
    }

    private suspend fun cleanupServers() {
        // Remove any invalid servers (incomplete, partly migrated from another device)
        serverManager.defaultServers
            .filter {
                serverManager.authenticationRepository(it.id)
                    .getSessionState() == SessionState.ANONYMOUS
            }
            .forEach { serverManager.removeServer(it.id) }
    }

    private suspend fun handleNetworkState(state: NetworkState): Boolean {
        Timber.i("Current network state $state")
        return when (state) {
            NetworkState.READY_LOCAL, NetworkState.READY_REMOTE -> {
                resyncRegistration()
                _navigationEventsFlow.emit(LauncherNavigationEvent.Frontend)
                true
            }

            NetworkState.CONNECTING -> {
                false
            }

            NetworkState.UNAVAILABLE -> {
                // TODO Make a page that simply show the message R.string.error_connection_failed_no_network
                false
            }
        }
    }

    private suspend fun resyncRegistration() = coroutineScope {
        serverManager.defaultServers.forEach { server ->
            launch {
                try {
                    val integrationRepository = serverManager.integrationRepository(server.id)
                    integrationRepository.updateRegistration(
                        DeviceRegistration(
                            // TODO when in :app use ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                            "ONBOARDING_WIP",
                            null,
                            null, // TODO when in :app use getMessagingToken()
                        ),
                    )
                    integrationRepository.getConfig() // Update cached data
                    serverManager.webSocketRepository(server.id).getCurrentUser() // Update cached data
                } catch (e: Exception) {
                    Timber.e(e, "Issue updating Registration for server ${server.id}")
                }
            }
        }
    }
}
