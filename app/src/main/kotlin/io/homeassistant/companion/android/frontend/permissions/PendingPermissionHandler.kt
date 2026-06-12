package io.homeassistant.companion.android.frontend.permissions

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable

/**
 * Routes a [PermissionRequest] to the appropriate UI and delivers the result back through the
 * request's own callback. The slot is freed automatically by the manager once the callback is
 * invoked, so this composable doesn't have to clear anything itself.
 *
 * Types with custom UI ([PermissionRequest.Notification], [PermissionRequest.Improv]) are matched
 * first. Remaining types fall through to the system dialog based on their category:
 * [PermissionRequest.MultiplePermissions] or [PermissionRequest.SinglePermission].
 *
 * Adding a new permission type that uses the system dialog requires no changes here.
 */
@Composable
internal fun PendingPermissionHandler(pendingRequest: PermissionRequest?) {
    when (pendingRequest) {
        is PermissionRequest.Notification -> {
            @SuppressLint("InlinedApi")
            NotificationPermissionPrompt(
                onPermissionResult = pendingRequest.onResult,
                onDismiss = pendingRequest.onDismiss,
            )
        }

        is PermissionRequest.Improv -> {
            ImprovPermissionPrompt(request = pendingRequest)
        }

        is PermissionRequest.MultiplePermissions -> {
            MultiplePermissionsEffect(
                pendingRequest = pendingRequest,
                onPermissionResult = pendingRequest.onResult,
            )
        }

        is PermissionRequest.SinglePermission -> {
            SinglePermissionEffect(
                pendingRequest = pendingRequest,
                onPermissionResult = pendingRequest.onResult,
            )
        }

        null -> {
            /* No pending permission */
        }
    }
}
