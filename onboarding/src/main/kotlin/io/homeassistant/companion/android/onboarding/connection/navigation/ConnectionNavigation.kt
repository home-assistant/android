package io.homeassistant.companion.android.onboarding.connection.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionScreen
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import kotlinx.serialization.Serializable

@Serializable
internal data class ConnectionRoute(val url: String)

internal fun NavController.navigateToConnection(url: String, navOptions: NavOptions? = null) {
    navigate(route = ConnectionRoute(url), navOptions)
}

internal fun NavGraphBuilder.connectionScreen(
    onAuthenticated: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onBackPressed: () -> Unit,
) {
    composable<ConnectionRoute>(
        deepLinks = listOf(
            navDeepLink<ConnectionRoute>(basePath = "homeassistant") {
                // TODO We have to hack this to make it work for invite
                // Or manage the parsing of the deep link directly from the Launcher
                uriPattern = "homeassistant://invite2?url={url}.*" // TODO update host
            },
        ),
    ) {
        val viewModel: ConnectionViewModel = hiltViewModel()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.navigationEventsFlow.collect {
                when (it) {
                    is ConnectionNavigationEvent.Authenticated -> onAuthenticated()
                    is ConnectionNavigationEvent.URLMalformed -> {
                        // TODO could change the message for a more explicit one
                        onShowSnackbar(context.getString(R.string.error_connection_failed), null)
                        onBackPressed()
                    }
                    is ConnectionNavigationEvent.Error -> {
                        // TODO make a dialog (move this out of the navigation event) or a full screen
                        onShowSnackbar(context.getString(it.resId, it.formatArgs), null)
                        onBackPressed()
                    }
                }
            }
        }
        ConnectionScreen(
            viewModel = viewModel,
            onBackPressed = onBackPressed,
        )
    }
}
