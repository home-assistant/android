package io.homeassistant.companion.android.onboarding.locationsharing.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.locationsharing.LocationSharingNavigationEvent
import io.homeassistant.companion.android.onboarding.locationsharing.LocationSharingScreen
import io.homeassistant.companion.android.onboarding.locationsharing.LocationSharingViewModel
import kotlinx.serialization.Serializable

@Serializable
internal data class LocationSharingRoute(val serverId: Int)

internal fun NavController.navigateToLocationSharing(serverId: Int, navOptions: NavOptions? = null) {
    navigate(LocationSharingRoute(serverId), navOptions = navOptions)
}

internal fun NavGraphBuilder.locationSharingScreen(onHelpClick: () -> Unit, onGotoNextScreen: () -> Unit) {
    composable<LocationSharingRoute> {
        val viewModel: LocationSharingViewModel = hiltViewModel()

        LaunchedEffect(viewModel) {
            viewModel.navigationEventsFlow.collect { event ->
                when (event) {
                    LocationSharingNavigationEvent.GoToNextScreen -> onGotoNextScreen()
                }
            }
        }

        LocationSharingScreen(
            onHelpClick = onHelpClick,
            viewModel = viewModel,
        )
    }
}
