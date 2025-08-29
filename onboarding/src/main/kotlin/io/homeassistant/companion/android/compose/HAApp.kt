package io.homeassistant.companion.android.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.loading.LoadingScreen

/**
 * Main composable for the Home Assistant app.
 *
 * This composable sets up the basic structure of the app, including the [Scaffold] for layout,
 * a [SnackbarHost], and the [HANavHost] for handling navigation.
 *
 * It also handles horizontal window insets to ensure content is displayed correctly within the screen's safe areas.
 * Sub composable needs to handle vertical insets themselves.
 *
 * @param navController The NavHostController to use for navigation.
 * @param startDestination The initial destination of the navigation graph. If it is null [LoadingScreen]
 *                         is displayed.
 * @param modifier The modifier to be applied to this composable.
 */
@Composable
internal fun HAApp(
    navController: NavHostController,
    startDestination: HAStartDestinationRoute?,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
        // Delegate the insets handling to content
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            HANavHost(
                navController = navController,
                startDestination = startDestination,
                onShowSnackbar = { message, action ->
                    snackbarHostState.showSnackbar(
                        message,
                        action,
                        duration = SnackbarDuration.Short,
                    ) == ActionPerformed
                },
            )
        }
    }
}
