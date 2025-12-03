package io.homeassistant.companion.android.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.automotive.navigation.AutomotiveRoute
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.network.NetworkState
import io.homeassistant.companion.android.common.data.network.NetworkStatusMonitor
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.ResyncRegistrationWorker.Companion.enqueueResyncRegistration
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import io.homeassistant.companion.android.di.qualifiers.LocationTrackingSupport
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Represents the UI state of the launcher screen.
 */
internal sealed interface LauncherUiState {
    /**
     * Initial loading state while determining where to navigate.
     */
    data object Loading : LauncherUiState

    /**
     * The app is ready to navigate to the start destination.
     */
    data class Ready(val startDestination: HAStartDestinationRoute) : LauncherUiState

    /**
     * The network is unavailable and the app cannot connect to the server.
     */
    data object NetworkUnavailable : LauncherUiState

    /**
     * Wear OS onboarding was requested but is not supported in the minimal flavor.
     * Wear OS communication requires Google Play Services which is only available in the full flavor.
     */
    data object WearUnsupported : LauncherUiState
}

/**
 * ViewModel for the launcher activity. Upon instantiation, it checks for servers to remove
 * and verifies the presence of an active, registered, and connected server.
 *
 * If no such server is found, or if an error occurs during this check (e.g., network connectivity issues),
 * it navigates to onboarding. Otherwise, it schedules a resync of all server registrations
 * asynchronously and navigates to the frontend.
 */
@HiltViewModel(assistedFactory = LauncherViewModelFactory::class)
internal class LauncherViewModel @AssistedInject constructor(
    @Assisted initialDeepLink: LauncherActivity.DeepLink?,
    private val workManager: WorkManager,
    private val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
    @param:LocationTrackingSupport private val hasLocationTrackingSupport: Boolean,
    @param:IsAutomotive private val isAutomotive: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LauncherUiState>(LauncherUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cleanupServers()
            handleInitialState(initialDeepLink)
        }
    }

    /**
     * Determine when to hide the application's splash screen.
     */
    fun shouldShowSplashScreen(): Boolean = _uiState.value is LauncherUiState.Loading

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

                networkStatusMonitor.observeNetworkStatus(serverManager.connectionStateProvider(server.id))
                    .takeWhile { state ->
                        // Until the network is ready we continue to observe network status changes
                        !handleNetworkState(state, path, serverId)
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

    private fun navigateToWearOnboarding(wearName: String, urlToOnboard: String? = null) {
        if (!hasLocationTrackingSupport) {
            // Wear OS requires Google Play Services for communication, which is only available in full flavor
            _uiState.value = LauncherUiState.WearUnsupported
            return
        }
        _uiState.value = LauncherUiState.Ready(
            WearOnboardingRoute(wearName = wearName, urlToOnboard = urlToOnboard),
        )
    }

    private fun navigateToOnboarding(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
    ) {
        _uiState.value = LauncherUiState.Ready(
            OnboardingRoute(
                hasLocationTracking = hasLocationTrackingSupport,
                urlToOnboard = urlToOnboard,
                hideExistingServers = hideExistingServers,
                skipWelcome = skipWelcome,
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

    private fun handleNetworkState(state: NetworkState, path: String?, serverId: Int): Boolean {
        Timber.i("Current network state $state")
        return when (state) {
            NetworkState.READY_LOCAL, NetworkState.READY_REMOTE -> {
                workManager.enqueueResyncRegistration()
                _uiState.value = LauncherUiState.Ready(
                    if (isAutomotive) {
                        AutomotiveRoute
                    } else {
                        FrontendRoute(path, serverId)
                    },
                )
                true
            }

            NetworkState.CONNECTING -> {
                false
            }

            NetworkState.UNAVAILABLE -> {
                _uiState.value = LauncherUiState.NetworkUnavailable
                false
            }
        }
    }
}

@AssistedFactory
internal interface LauncherViewModelFactory {
    fun create(initialDeepLink: LauncherActivity.DeepLink?): LauncherViewModel
}
