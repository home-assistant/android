package io.homeassistant.companion.android.widgets.configure

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Drives the state collection and error-to-snackbar handling shared by every widget configuration
 * screen.
 */
@Composable
internal fun <S : WidgetConfigureState<S>> WidgetConfigureScreen(
    viewModel: WidgetConfigureStateProvider<S>,
    content: @Composable (state: S, snackbarHostState: SnackbarHostState) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

    content(state, snackbarHostState)
}
