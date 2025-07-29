package io.homeassistant.companion.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.concurrent.AtomicBoolean
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

sealed interface LauncherNavigationEvent {
    data object Frontend : LauncherNavigationEvent
    data object Onboarding : LauncherNavigationEvent
}

@HiltViewModel
class LauncherViewModel @Inject constructor(private val serverManager: ServerManager) : ViewModel() {

    /**
     * Holds the navigation events for the launcher.
     */
    private val navigationEventsMutableFlow = MutableSharedFlow<LauncherNavigationEvent>(replay = 1)
    val navigationEventsFlow: Flow<LauncherNavigationEvent> = navigationEventsMutableFlow

    private val shouldShowSplashScreen = AtomicBoolean(false)

    fun shouldShowSplashScreen(): Boolean = shouldShowSplashScreen.get()

    init {
        viewModelScope.launch {
            // TODO move this to a UseCase class that retrieve a server
            // Remove any invalid servers (incomplete, partly migrated from another device)
            serverManager.defaultServers
                .filter {
                    serverManager.authenticationRepository(it.id)
                        .getSessionState() == SessionState.ANONYMOUS
                }
                .forEach { serverManager.removeServer(it.id) }

            try {
                // TODO handle deep link
                // TODO handle watch onboarding
                // TODO handle maybe need mTLS
                if (
                    serverManager.isRegistered() &&
                    serverManager.authenticationRepository()
                        .getSessionState() == SessionState.CONNECTED
                ) {
                    // resyncRegistration()
                    navigationEventsMutableFlow.emit(LauncherNavigationEvent.Frontend)
                } else {
                    navigationEventsMutableFlow.emit(LauncherNavigationEvent.Onboarding)
                }
            } catch (e: IllegalArgumentException) { // Server was just removed, nothing is added
                // TODO what can throw here this exception resyncRegistration probably
                navigationEventsMutableFlow.emit(LauncherNavigationEvent.Onboarding)
            }
            shouldShowSplashScreen.set(false)
        }
    }
}
