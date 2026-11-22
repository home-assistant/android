package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.lifecycle.compose.dropUnlessResumed
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
            onAcceptClick = dropUnlessResumed { onAcceptClick(route.serverUrl) },
            onRejectClick = dropUnlessResumed(block = onRejectClick),
            onLearnMoreClick = onLearnMoreClick,
        )
    }
}
