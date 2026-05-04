package io.homeassistant.companion.android.frontend.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.homeassistant.companion.android.common.util.FailFast

/**
 * Composable effect that requests multiple Android permissions via the system dialog.
 *
 * Used for [PermissionRequest.MultiplePermissions] subclasses (e.g. camera + microphone).
 * Automatically launches when [pendingRequest] is non-null.
 *
 * This composable has no visible UI — it only manages the permission request lifecycle.
 *
 * @param pendingRequest The permission request to launch, or null if none
 * @param onPermissionResult Callback with the per-permission grant results
 */
@Composable
internal fun MultiplePermissionsEffect(
    pendingRequest: PermissionRequest.MultiplePermissions?,
    onPermissionResult: (Map<String, Boolean>) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onPermissionResult,
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            FailFast.failWhen(pendingRequest.permissions.isEmpty()) { "Missing multiple permissions" }
            permissionLauncher.launch(pendingRequest.permissions.toTypedArray())
        }
    }
}

/**
 * Composable effect that requests a single Android permission via the system dialog.
 *
 * Used for [PermissionRequest.SinglePermission] subclasses that don't have custom UI.
 * Automatically launches when [pendingRequest] is non-null.
 *
 * This composable has no visible UI — it only manages the permission request lifecycle.
 *
 * @param pendingRequest The permission request to launch, or null if none
 * @param onPermissionResult Callback with whether the permission was granted
 */
@Composable
internal fun SinglePermissionEffect(
    pendingRequest: PermissionRequest.SinglePermission?,
    onPermissionResult: (Boolean) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult,
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            permissionLauncher.launch(pendingRequest.permissions.first())
        }
    }
}
