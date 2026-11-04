package io.homeassistant.companion.android.changelog.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import io.homeassistant.companion.android.changelog.ChangelogShowViewModel
import io.homeassistant.companion.android.changelog.perform
import io.homeassistant.companion.android.changelog.ui.ChangelogScreen
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
internal data object ChangelogRoute

internal fun NavController.navigateToChangelog(navOptions: NavOptions? = null) {
    navigate(ChangelogRoute, navOptions)
}

/**
 * Registers the changelog destination, displaying the changes of the app version currently
 * running. Opening the destination marks the changelog as seen.
 *
 * @param onShowSnackbar Reports that an action of a changelog entry could not be performed.
 */
internal fun NavGraphBuilder.changelogScreen(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    composable<ChangelogRoute> {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        ChangelogScreen(
            viewModel = hiltViewModel(),
            onCloseClick = { navController.popBackStack() },
            onActionClick = { action ->
                coroutineScope.launch {
                    action.perform(context, onShowSnackbar)
                }
            },
        )
    }
}

/**
 * Navigates to the changelog once the frontend is displayed after an app update, unless the user
 * disabled the automatic changelog popup.
 *
 * The decision is delegated to (and consumed from) [ChangelogShowViewModel] so it happens at
 * most once per process and survives configuration changes.
 */
@Composable
internal fun ChangelogAutoShowEffect(
    navController: NavController,
    viewModel: ChangelogShowViewModel = hiltViewModel(),
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isOnFrontend = currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true

    LaunchedEffect(isOnFrontend) {
        if (isOnFrontend && viewModel.consumeShouldShowChangelog()) {
            navController.navigateToChangelog()
        }
    }
}
