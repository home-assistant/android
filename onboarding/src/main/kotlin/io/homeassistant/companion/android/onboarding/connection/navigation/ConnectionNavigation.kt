package io.homeassistant.companion.android.onboarding.connection.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
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
    onBack: () -> Unit,
) {
    composable<ConnectionRoute> {
        val viewModel: ConnectionViewModel = hiltViewModel()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.navigationEventsFlow.collect {
                when (it) {
                    is ConnectionNavigationEvent.Authenticated -> onAuthenticated()
                    is ConnectionNavigationEvent.URLMalformed -> {
                        // TODO could change the message for a more explicit one
                        onShowSnackbar(context.getString(R.string.error_connection_failed), null)
                        onBack()
                    }
                    is ConnectionNavigationEvent.Error -> {
                        // TODO make a dialog (move this out of the navigation event) or a full screen
                        onShowSnackbar(context.getString(it.resId, it.formatArgs), null)
                        onBack()
                    }
                }
            }
        }
        ConnectionScreen(viewModel = viewModel)
    }
}
