package io.homeassistant.companion.android.onboarding.connection.navigation

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.connection.ConnectionErrorScreen
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
    onAuthenticated: (url: String, authCode: String, requiredMTLS: Boolean) -> Unit,
    onBackClick: () -> Unit,
    onOpenExternalLink: (url: Uri) -> Unit,
) {
    composable<ConnectionRoute> {
        val viewModel: ConnectionViewModel = hiltViewModel()

        HandleConnectionNavigationEvents(
            viewModel = viewModel,
            onAuthenticated = onAuthenticated,
            onOpenExternalLink = onOpenExternalLink,
        )

        ConnectionScreen(
            viewModel = viewModel,
            onBackClick = onBackClick,
        )

        ConnectionErrorScreen(
            onOpenExternalLink = onOpenExternalLink,
            onCloseClick = onBackClick,
            viewModel = viewModel,
        )
    }
}

@Composable
@VisibleForTesting
internal fun HandleConnectionNavigationEvents(
    viewModel: ConnectionViewModel,
    onAuthenticated: (url: String, authCode: String, requiredMTLS: Boolean) -> Unit,
    onOpenExternalLink: (url: Uri) -> Unit,
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
