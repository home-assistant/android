package io.homeassistant.companion.android.frontend.permissions

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.improv.ui.ImprovPermission
import kotlinx.coroutines.launch

/**
 * Renders the Improv permission flow: optional rationale bottom sheet followed by the system
 * multi-permission dialog.
 *
 * Two branches:
 * - When [PermissionRequest.Improv.showRationale] is `true`, the bottom sheet is shown. Continue
 *   asks accompanist's `MultiplePermissionsState` to launch the system dialog; its callback
 *   surfaces the grant map via [PermissionRequest.Improv.onResult] and closes the sheet. Skip /
 *   swipe-to-dismiss calls [PermissionRequest.Improv.onDismiss] instead.
 * - When `showRationale` is `false`, no UI renders — a one-shot `LaunchedEffect` triggers the
 *   system dialog directly, and the same accompanist callback delivers the result.
 *
 * The `isClosed` gate is necessary because Material 3's `ModalBottomSheet` creates a `Dialog`
 * window that, even when hidden, can block touch events on top of the system permission dialog
 * launched immediately afterwards. Removing the composable entirely (`!isClosed`) tears down the
 * Dialog window so the system dialog is fully interactive.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
internal fun ImprovPermissionPrompt(request: PermissionRequest.Improv) {
    val sheetState = rememberHAModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    // Remove the sheet entirely from composition once dismissed so M3's Dialog window stops
    // swallowing touches on top of the system permission dialog launched by accompanist.
    var isClosed by remember { mutableStateOf(false) }

    fun closeSheet() {
        coroutineScope.launch {
            sheetState.hide()
            isClosed = true
        }
    }

    val multiPermissionsState = rememberMultiplePermissionsState(
        permissions = request.permissions,
        onPermissionsResult = { result ->
            request.onResult(result)
            closeSheet()
        },
    )

    if (!request.showRationale) {
        LaunchedEffect(Unit) {
            multiPermissionsState.launchMultiplePermissionRequest()
        }
        return
    }

    if (!isClosed) {
        HAModalBottomSheet(
            bottomSheetState = sheetState,
            onDismissRequest = {
                request.onDismiss()
                closeSheet()
            },
            dragHandle = {},
        ) {
            ImprovPermission(
                needsBluetooth = request.needsBluetooth,
                needsLocation = request.needsLocation,
                onContinue = { multiPermissionsState.launchMultiplePermissionRequest() },
                onSkip = {
                    request.onDismiss()
                    closeSheet()
                },
            )
        }
    }
}

@SuppressLint("InlinedApi")
@Preview
@Composable
private fun PreviewImprovPermissionPrompt() {
    HAThemeForPreview {
        ImprovPermissionPrompt(
            PermissionRequest.Improv(
                permissions = listOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                showRationale = true,
                onResult = {},
                onDismiss = {},
            ),
        )
    }
}
