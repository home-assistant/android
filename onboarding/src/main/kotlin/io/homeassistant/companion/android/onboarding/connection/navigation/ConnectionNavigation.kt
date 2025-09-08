package io.homeassistant.companion.android.onboarding.connection.navigation

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
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
internal data class ConnectionRoute(val url: String)

internal fun NavController.navigateToConnection(url: String, navOptions: NavOptions? = null) {
    navigate(route = ConnectionRoute(url), navOptions)
}

internal fun NavGraphBuilder.connectionScreen(
    onAuthenticated: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onBackPressed: () -> Unit,
) {
    composable<ConnectionRoute> {
        val viewModel: ConnectionViewModel = hiltViewModel()

        HandleConnectionNavigationEvents(
            viewModel = viewModel,
            onAuthenticated = onAuthenticated,
            onShowSnackbar = onShowSnackbar,
            onBackPressed = onBackPressed,
        )

        ConnectionScreen(
            viewModel = viewModel,
            onBackPressed = onBackPressed,
        )
    }
}

@Composable
@VisibleForTesting
internal fun HandleConnectionNavigationEvents(
    viewModel: ConnectionViewModel,
    onAuthenticated: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.navigationEventsFlow.collect {
            when (it) {
                is ConnectionNavigationEvent.Authenticated -> onAuthenticated()
                is ConnectionNavigationEvent.Error -> {
                    // TODO use full screen error when available
                    onShowSnackbar(
                        if (it.formatArgs.isNotEmpty()) {
                            context.getString(
                                it.resId,
                                *it.formatArgs,
                            )
                        } else {
                            context.getString(it.resId)
                        },
                        null,
                    )
                    onBackPressed()
                }
            }
        }
    }
}
