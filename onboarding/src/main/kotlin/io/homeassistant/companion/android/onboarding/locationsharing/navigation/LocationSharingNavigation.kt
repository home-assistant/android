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
internal data class LocationSharingRoute(val serverId: Int)

internal fun NavController.navigateToLocationSharing(serverId: Int, navOptions: NavOptions? = null) {
    navigate(LocationSharingRoute(serverId), navOptions = navOptions)
}

internal fun NavGraphBuilder.locationSharingScreen(onHelpClick: () -> Unit, onGotoNextScreen: (serverId: Int) -> Unit) {
    composable<LocationSharingRoute> {
        LocationSharingScreen(
            onHelpClick = onHelpClick,
            onGoToNextScreen = {
                onGotoNextScreen(it.toRoute<LocationSharingRoute>().serverId)
            },
            viewModel = hiltViewModel(),
        )
    }
}
