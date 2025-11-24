package io.homeassistant.companion.android.onboarding.locationsharing.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.locationsharing.LocationSharingScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class LocationSharingRoute(val serverId: Int, val hasPlainTextAccess: Boolean)

internal fun NavController.navigateToLocationSharing(
    serverId: Int,
    hasPlainTextAccess: Boolean,
    navOptions: NavOptions? = null,
) {
    navigate(LocationSharingRoute(serverId, hasPlainTextAccess), navOptions = navOptions)
}

internal fun NavGraphBuilder.locationSharingScreen(
    onHelpClick: () -> Unit,
    onGotoNextScreen: (serverId: Int, hasPlainTextAccess: Boolean) -> Unit,
) {
    composable<LocationSharingRoute> {
        LocationSharingScreen(
            onHelpClick = onHelpClick,
            onGoToNextScreen = {
                val route = it.toRoute<LocationSharingRoute>()
                onGotoNextScreen(route.serverId, route.hasPlainTextAccess)
            },
            viewModel = hiltViewModel(),
        )
    }
}
