package io.homeassistant.companion.android.frontend.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Composable effect that bridges WebView permission requests to the Android runtime permission system.
 *
 * Automatically launches the system permission dialog when [pendingPermission] is non-null.
 * Feeds the result back via [onPermissionResult] which resolves the WebView's
 * [android.webkit.PermissionRequest].
 *
 * This composable has no visible UI — it only manages the permission request lifecycle.
 *
 * @param pendingPermission The current pending permission request, or null if none
 * @param onPermissionResult Callback with the system permission dialog results
 */
@Composable
internal fun WebViewPermissionEffect(
    pendingPermission: PendingWebViewPermissionRequest?,
    onPermissionResult: (Map<String, Boolean>) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onPermissionResult,
    )

    if (pendingPermission != null) {
        LaunchedEffect(pendingPermission) {
            permissionLauncher.launch(pendingPermission.androidPermissions.toTypedArray())
        }
    }
}
