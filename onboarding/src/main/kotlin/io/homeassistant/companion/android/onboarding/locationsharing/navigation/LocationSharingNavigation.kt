package io.homeassistant.companion.android.onboarding.locationsharing.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AndroidUriHandler
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
data object LocationSharingRoute

fun NavController.navigateToLocationSharing(navOptions: NavOptions? = null) {
    navigate(LocationSharingRoute, navOptions = navOptions)
}

fun NavController.navigateToLocationSharingHelp() {
    // TODO not sure it's the best way to do this or even the place to do this
    AndroidUriHandler(context).openUri("https://home-assistant.io")
}

fun NavGraphBuilder.locationSharingScreen(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onGotoNextScreen: () -> Unit,
) {
    composable<LocationSharingRoute> {
        val viewModel: LocationSharingViewModel = hiltViewModel()

        LaunchedEffect(viewModel) {
            viewModel.navigationEventFlow.collect { event ->
                when (event) {
                    LocationSharingNavigationEvent.GoToNextScreen -> onGotoNextScreen()
                }
            }
        }

        LocationSharingScreen(
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            viewModel = viewModel,
        )
    }
}
