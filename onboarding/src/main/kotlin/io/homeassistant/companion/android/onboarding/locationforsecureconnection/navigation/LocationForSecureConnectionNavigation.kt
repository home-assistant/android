package io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object LocationForSecureConnectionRoute

internal fun NavController.navigateToLocationForSecureConnection(navOptions: NavOptions? = null) {
    navigate(LocationForSecureConnectionRoute, navOptions = navOptions)
}

internal fun NavGraphBuilder.locationForSecureConnectionScreen(onHelpClick: () -> Unit, onGotoNextScreen: () -> Unit) {
    composable<LocationForSecureConnectionRoute> {
        LocationForSecureConnectionScreen(
            viewModel = hiltViewModel(),
            onHelpClick = onHelpClick,
        )
    }
}
