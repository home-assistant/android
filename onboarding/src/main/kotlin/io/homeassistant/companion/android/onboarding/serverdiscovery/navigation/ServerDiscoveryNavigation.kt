package io.homeassistant.companion.android.onboarding.serverdiscovery.navigation

import androidx.annotation.Keep
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryScreen
import java.net.URL
import kotlinx.serialization.Serializable

/**
 * Defines the behavior of the server discovery screen.
 */
@Keep
internal enum class ServerDiscoveryMode {
    /** Standard discovery mode showing all discovered servers. */
    NORMAL,

    /** Discovery mode that hides servers already registered in the app. */
    HIDE_EXISTING,

    /** Discovery mode that shows existing registered servers alongside with newly discovered. */
    ADD_EXISTING,
}

@Serializable
internal data class ServerDiscoveryRoute(val discoveryMode: ServerDiscoveryMode)

internal fun NavController.navigateToServerDiscovery(
    discoveryMode: ServerDiscoveryMode = ServerDiscoveryMode.NORMAL,
    navOptions: NavOptions? = null,
) {
    navigate(route = ServerDiscoveryRoute(discoveryMode), navOptions)
}

internal fun NavGraphBuilder.serverDiscoveryScreen(
    onConnectClick: (server: URL) -> Unit,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onManualSetupClick: () -> Unit,
) {
    composable<ServerDiscoveryRoute> {
        ServerDiscoveryScreen(
            onConnectClick = onConnectClick,
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            onManualSetupClick = onManualSetupClick,
            viewModel = hiltViewModel(),
        )
    }
}
