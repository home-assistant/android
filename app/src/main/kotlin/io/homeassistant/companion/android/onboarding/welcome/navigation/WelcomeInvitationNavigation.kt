package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.welcome.WelcomeInvitationScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class WelcomeInvitationRoute(val serverUrl: String)

internal fun NavGraphBuilder.welcomeInvitationScreen(
    onAcceptClick: (serverUrl: String) -> Unit,
    onRejectClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
) {
    composable<WelcomeInvitationRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<WelcomeInvitationRoute>()
        WelcomeInvitationScreen(
            serverUrl = route.serverUrl,
            onAcceptClick = { onAcceptClick(route.serverUrl) },
            onRejectClick = onRejectClick,
            onLearnMoreClick = onLearnMoreClick,
        )
    }
}
