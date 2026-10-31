package io.homeassistant.companion.android.onboarding.connection.navigation

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.connection.ConnectionErrorScreen
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionScreen
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import kotlinx.serialization.Serializable

@Serializable
internal data class ConnectionRoute(val url: String, val internalUrl: String? = null)

/**
 * @param internalUrl the home network URL a discovered server announced, saved with the server
 */
internal fun NavController.navigateToConnection(
    url: String,
    navOptions: NavOptions? = null,
    internalUrl: String? = null,
) {
    navigate(route = ConnectionRoute(url, internalUrl), navOptions)
}

internal fun NavGraphBuilder.connectionScreen(
    onAuthenticated: (url: String, authCode: String, requiredMTLS: Boolean, internalUrl: String?) -> Unit,
    onBackClick: () -> Unit,
    onOpenExternalLink: suspend (url: Uri) -> Unit,
) {
    composable<ConnectionRoute> { backStackEntry ->
        val viewModel: ConnectionViewModel = hiltViewModel()
        val internalUrl = backStackEntry.toRoute<ConnectionRoute>().internalUrl

        HandleConnectionNavigationEvents(
            viewModel = viewModel,
            onAuthenticated = { url, authCode, requiredMTLS ->
                onAuthenticated(url, authCode, requiredMTLS, internalUrl)
            },
            onOpenExternalLink = onOpenExternalLink,
        )

        ConnectionScreen(
            viewModel = viewModel,
            onBackClick = onBackClick,
        )

        ConnectionErrorScreen(
            stateProvider = viewModel,
            onOpenExternalLink = onOpenExternalLink,
            onCloseClick = onBackClick,
        )
    }
}

@Composable
@VisibleForTesting
internal fun HandleConnectionNavigationEvents(
    viewModel: ConnectionViewModel,
    onAuthenticated: (url: String, authCode: String, requiredMTLS: Boolean) -> Unit,
    onOpenExternalLink: suspend (url: Uri) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.navigationEventsFlow.collect {
            when (it) {
                is ConnectionNavigationEvent.Authenticated -> onAuthenticated(it.url, it.authCode, it.requiredMTLS)
                is ConnectionNavigationEvent.OpenExternalLink -> onOpenExternalLink(it.url)
            }
        }
    }
}
