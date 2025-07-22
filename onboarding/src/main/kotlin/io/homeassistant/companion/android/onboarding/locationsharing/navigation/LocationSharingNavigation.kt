package io.homeassistant.companion.android.onboarding.locationsharing.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.locationsharing.LocationSharingScreen
import kotlinx.serialization.Serializable

@Serializable
data object LocationSharingRoute

fun NavController.navigateToLocationSharing(navOptions: NavOptions? = null) {
    navigate(LocationSharingRoute, navOptions = navOptions)
}

fun NavGraphBuilder.locationSharingScreen() {
    composable<LocationSharingRoute> {
        LocationSharingScreen(
            onBackClick = {},
            onHelpClick = {},
            viewModel = hiltViewModel(),
        )
    }
}
