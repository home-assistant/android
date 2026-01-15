package io.homeassistant.companion.android.launch

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
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
 * Represents the UI state of the launch screen.
 */
internal sealed interface LaunchUiState {
    /**
     * Initial loading state while determining where to navigate.
     */
    data object Loading : LaunchUiState

    /**
     * The app is ready to navigate to the start destination.
     */
    data class Ready(val startDestination: HAStartDestinationRoute) : LaunchUiState

    /**
     * The network is unavailable and the app cannot connect to the server.
     */
    data object NetworkUnavailable : LaunchUiState

    /**
     * Wear OS onboarding was requested but is not supported in the minimal flavor.
     */
    data object WearUnsupported : LaunchUiState
}

/**
 * ViewModel for the launch activity. Upon instantiation, it checks for servers to remove
 * and verifies the presence of an active, registered, and connected server.
 *
 * If no such server is found, or if an error occurs during this check (e.g., network connectivity issues),
 * it navigates to onboarding. Otherwise, it schedules a resync of all server registrations
 * asynchronously and navigates to the frontend.
 */
@HiltViewModel(assistedFactory = LaunchViewModelFactory::class)
internal class LaunchViewModel @VisibleForTesting constructor(
    initialDeepLink: LaunchActivity.DeepLink?,
    private val workManager: WorkManager,
    private val serverManager: ServerManager,
    private val networkStatusMonitor: NetworkStatusMonitor,
    private val hasLocationTrackingSupport: Boolean,
    isAutomotive: Boolean,
    isFullFlavor: Boolean,
) : ViewModel() {

    @AssistedInject
    constructor(
        @Assisted initialDeepLink: LaunchActivity.DeepLink?,
        workManager: WorkManager,
        serverManager: ServerManager,
        networkStatusMonitor: NetworkStatusMonitor,
        @LocationTrackingSupport hasLocationTrackingSupport: Boolean,
        @IsAutomotive isAutomotive: Boolean,
    ) : this(
        initialDeepLink,
        workManager,
        serverManager,
        networkStatusMonitor,
        hasLocationTrackingSupport,
        isAutomotive = isAutomotive,
        isFullFlavor = BuildConfig.FLAVOR == "full",
    )

    /**
     * Indicates whether the app should navigate to the automotive-specific screen.
     *
     * This is only true when running on Android Automotive with the full flavor.
     * The Play Store requires automotive apps to use a dedicated UI instead of a WebView,
     * otherwise the app gets rejected.
     */
    private val shouldNavigateToAutomotive: Boolean = isAutomotive && isFullFlavor

    private val _uiState = MutableStateFlow<LaunchUiState>(LaunchUiState.Loading)
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
    fun shouldShowSplashScreen(): Boolean = _uiState.value is LaunchUiState.Loading

    private suspend fun handleInitialState(initialDeepLink: LaunchActivity.DeepLink?) {
        when (initialDeepLink) {
            is LaunchActivity.DeepLink.OpenOnboarding -> navigateToOnboarding(
                initialDeepLink.urlToOnboard,
                hideExistingServers = initialDeepLink.hideExistingServers,
                skipWelcome = initialDeepLink.skipWelcome,
            )

            is LaunchActivity.DeepLink.NavigateTo,
            -> connectToServer(initialDeepLink.serverId, initialDeepLink.path)

            is LaunchActivity.DeepLink.OpenWearOnboarding -> navigateToWearOnboarding(
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
            _uiState.value = LaunchUiState.WearUnsupported
            return
        }
        _uiState.value = LaunchUiState.Ready(
            WearOnboardingRoute(wearName = wearName, urlToOnboard = urlToOnboard),
        )
    }

    private fun navigateToOnboarding(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
    ) {
        _uiState.value = LaunchUiState.Ready(
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
        serverManager.servers()
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
                _uiState.value = LaunchUiState.Ready(
                    if (shouldNavigateToAutomotive) {
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
                _uiState.value = LaunchUiState.NetworkUnavailable
                false
            }
        }
    }
}

@AssistedFactory
internal interface LaunchViewModelFactory {
    fun create(initialDeepLink: LaunchActivity.DeepLink?): LaunchViewModel
}
