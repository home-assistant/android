package io.homeassistant.companion.android.frontend.improv.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.frontend.improv.ImprovUIState

/**
 * Renders the Improv Wi-Fi onboarding bottom sheet on top of the WebView when an
 * `improv/configure_device` flow is active.
 *
 * Hidden when [state] is `null`. Forwards swipe-to-dismiss to [onDismiss] so the ViewModel can
 * extract the provisioned domain (if any) and trigger the navigate-to-config-flow side effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImprovOverlay(
    state: ImprovUIState?,
    onConnectDevice: (ssid: String, password: String) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state != null) {
        val sheetState = rememberHAModalBottomSheetState()
        HAModalBottomSheet(
            bottomSheetState = sheetState,
            onDismissRequest = onDismiss,
            dragHandle = {},
        ) {
            ImprovSheet(
                screenState = state,
                onConnect = onConnectDevice,
                onRestart = onRestart,
                onDismiss = onDismiss,
            )
        }
    }
}
