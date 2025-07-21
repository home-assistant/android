package io.homeassistant.companion.android.onboarding.connection.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionScreen
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRoute(val url: String)

fun NavController.navigateToConnection(url: String, navOptions: NavOptions? = null) {
    navigate(route = ConnectionRoute(url), navOptions)
}

fun NavGraphBuilder.connectionScreen(onAuthenticated: () -> Unit) {
    composable<ConnectionRoute> {
        val viewModel: ConnectionViewModel = hiltViewModel()
        LaunchedEffect(viewModel) {
            viewModel.navigationEvents.collect {
                when (it) {
                    ConnectionNavigationEvent.Authenticated -> onAuthenticated()
                }
            }
        }
        ConnectionScreen(viewModel = viewModel)
    }
}
