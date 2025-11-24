package io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class LocationForSecureConnectionRoute(val serverId: Int)

internal fun NavController.navigateToLocationForSecureConnection(serverId: Int, navOptions: NavOptions? = null) {
    navigate(LocationForSecureConnectionRoute(serverId = serverId), navOptions = navOptions)
}

internal fun NavGraphBuilder.locationForSecureConnectionScreen(
    onHelpClick: () -> Unit,
    onGotoNextScreen: (allowInsecureConnection: Boolean, serverId: Int) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    composable<LocationForSecureConnectionRoute> {
        LocationForSecureConnectionScreen(
            viewModel = hiltViewModel(),
            onHelpClick = onHelpClick,
            onGoToNextScreen = { allowInsecureConnection ->
                onGotoNextScreen(allowInsecureConnection, it.toRoute<LocationForSecureConnectionRoute>().serverId)
            },
            onShowSnackbar = onShowSnackbar,
        )
    }
}
