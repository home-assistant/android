package io.homeassistant.companion.android.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.ResyncRegistrationWorker.Companion.enqueueResyncRegistration
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.di.qualifiers.LocationTrackingSupport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sealed interface for navigation events that can occur in the launcher.
 * These events are used to determine where the app should navigate to
 * after launching.
 */
internal sealed interface LauncherNavigationEvent {
    data class Frontend(val path: String?, val serverId: Int) : LauncherNavigationEvent
    data class Onboarding(
        val urlToOnboard: String?,
        val hideExistingServers: Boolean,
        val skipWelcome: Boolean,
        val hasLocationTrackingSupport: Boolean,
    ) : LauncherNavigationEvent

    data class WearOnboarding(val wearName: String, val urlToOnboard: String?) : LauncherNavigationEvent
}

/**
 * ViewModel for the launcher activity. Upon instantiation, it checks for servers to remove
 * and verifies the presence of an active, registered, and connected server.
 *
 * If no such server is found, or if an error occurs during this check (e.g., network connectivity issues),
 * it emits [LauncherNavigationEvent.Onboarding]. Otherwise, it schedules a resync of all server
 * registrations asynchronously and emits [LauncherNavigationEvent.Frontend].
 */
@HiltViewModel(assistedFactory = LauncherViewModelFactory::class)
internal class LauncherViewModel @AssistedInject constructor(
    @Assisted initialDeepLink: LauncherActivity.DeepLink?,
    private val workManager: WorkManager,
    private val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
    @param:LocationTrackingSupport private val hasLocationTrackingSupport: Boolean,
) : ViewModel() {

    private val _navigationEventsFlow = MutableSharedFlow<LauncherNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            cleanupServers()
            handleInitialState(initialDeepLink)
        }
    }

    /**
     * Determine when to hide the application's splash screen.
     */
    fun shouldShowSplashScreen(): Boolean = navigationEventsFlow.replayCache.isEmpty()

    private suspend fun handleInitialState(initialDeepLink: LauncherActivity.DeepLink?) {
        when (initialDeepLink) {
            is LauncherActivity.DeepLink.OpenOnboarding -> navigateToOnboarding(

                initialDeepLink.urlToOnboard,
                hideExistingServers = initialDeepLink.hideExistingServers,
                skipWelcome = initialDeepLink.skipWelcome,
            )

            is LauncherActivity.DeepLink.NavigateTo,
            -> connectToServer(initialDeepLink.serverId, initialDeepLink.path)

            is LauncherActivity.DeepLink.OpenWearOnboarding -> navigateToWearOnboarding(
                wearName = initialDeepLink.wearName,
                urlToOnboard = initialDeepLink.urlToOnboard,
            )

            null -> connectToServer(ServerManager.SERVER_ID_ACTIVE, null)
        }
    }

    private suspend fun connectToServer(serverId: Int, path: String?) {
        try {
            getServerConnectedAndRegistered(serverId)?.let { server ->
                Timber.d("Server (id=${server.id}) is connected and registered checking network status")

                networkStatusMonitor.observeNetworkStatus(server.connection)
                    .takeWhile { state ->
                        // Until the network is ready we continue to observe network status changes
                        !handleNetworkState(state, LauncherNavigationEvent.Frontend(path, serverId))
                    }.collect()
            } ?: navigateToOnboarding()
        } catch (e: IllegalArgumentException) {
            Timber.e(
                e,
                "Something went wrong while checking if any server are registered with a connected session",
            )
            navigateToOnboarding()
        }
    }

    private suspend fun getServerConnectedAndRegistered(serverId: Int): Server? {
        return serverManager.getServer(serverId)?.takeIf {
            serverManager.isRegistered() &&
                serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
        }
    }

    private suspend fun navigateToWearOnboarding(wearName: String, urlToOnboard: String? = null) {
        _navigationEventsFlow.emit(
            LauncherNavigationEvent.WearOnboarding(wearName = wearName, urlToOnboard = urlToOnboard),
        )
    }

    private suspend fun navigateToOnboarding(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
    ) {
        _navigationEventsFlow.emit(
            LauncherNavigationEvent.Onboarding(
                urlToOnboard,
                hideExistingServers = hideExistingServers,
                skipWelcome = skipWelcome,
                hasLocationTrackingSupport = hasLocationTrackingSupport,
            ),
        )
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

    private suspend fun handleNetworkState(
        state: NetworkState,
        destinationOnReady: LauncherNavigationEvent.Frontend,
    ): Boolean {
        Timber.i("Current network state $state")
        return when (state) {
            NetworkState.READY_LOCAL, NetworkState.READY_REMOTE -> {
                workManager.enqueueResyncRegistration()
                _navigationEventsFlow.emit(destinationOnReady)
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
}

@AssistedFactory
internal interface LauncherViewModelFactory {
    fun create(initialDeepLink: LauncherActivity.DeepLink?): LauncherViewModel
}
