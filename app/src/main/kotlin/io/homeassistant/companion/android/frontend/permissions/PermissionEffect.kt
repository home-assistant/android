package io.homeassistant.companion.android.frontend.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Composable effect that bridges [PendingPermissionRequest] to the Android runtime permission system.
 *
 * Automatically launches the system permission dialog when [pendingRequest] is non-null.
 * Feeds the result back via [onPermissionResult] so the [PermissionManager] can resolve
 * the request based on its concrete type.
 *
 * This composable has no visible UI — it only manages the permission request lifecycle.
 *
 * @param pendingRequest The current pending permission request, or null if none
 * @param onPermissionResult Callback with the system permission dialog results
 */
@Composable
internal fun PermissionEffect(
    pendingRequest: PendingPermissionRequest?,
    onPermissionResult: (Map<String, Boolean>) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onPermissionResult,
    )

    if (pendingRequest != null) {
        LaunchedEffect(pendingRequest) {
            permissionLauncher.launch(pendingRequest.permissions.toTypedArray())
        }
    }
}
