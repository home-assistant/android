package io.homeassistant.companion.android.frontend.permissions

import android.Manifest
import android.os.Build
import android.webkit.PermissionRequest
import androidx.annotation.RequiresApi

/**
 * A permission request waiting for user approval.
 *
 * Each subclass carries the permissions to request and an [onGranted] callback that
 * fires when at least one permission is approved. This allows callers to enqueue
 * follow-up work (e.g. retrying a download) without inspecting the concrete type.
 *
 * Most subclasses go straight to the system dialog via [PermissionEffect].
 * [Notification] is special: it shows a bottom sheet prompt first, then the system dialog.
 *
 * @param permissions The Android runtime permissions to request
 * @param onGranted Callback invoked when at least one requested permission is granted
 */
internal sealed class PendingPermissionRequest(val permissions: List<String>, val onGranted: () -> Unit = {}) {

    /**
     * A WebView permission request (camera, microphone) that maps Android permissions
     * back to WebView resource strings so the original [PermissionRequest] can be resolved.
     *
     * @param webViewRequest The original [PermissionRequest] to grant/deny after the user responds
     * @param webViewResourcesByPermission Maps each Android permission to its WebView resource string
     * @param alreadyGrantedResources WebView resources already granted at request time, deferred so
     *        all resources can be granted in a single [PermissionRequest.grant] call
     */
    class WebView(
        val webViewRequest: PermissionRequest,
        androidPermissions: List<String>,
        val webViewResourcesByPermission: Map<String, String>,
        val alreadyGrantedResources: List<String> = emptyList(),
    ) : PendingPermissionRequest(permissions = androidPermissions)

    /**
     * A storage permission request for downloads on pre-Q devices.
     *
     * The [onGranted] callback retries the download that was blocked by the missing permission.
     */
    class StorageForDownload(onGranted: () -> Unit) :
        PendingPermissionRequest(
            permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            onGranted = onGranted,
        )

    /**
     * A notification permission request that shows a bottom sheet prompt before the system dialog.
     *
     * Unlike other types, this is rendered as visible UI (a bottom sheet with Allow/Deny buttons)
     * rather than going straight to the system permission dialog.
     *
     * @param serverId The server ID for persisting the user's choice
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Notification(val serverId: Int) :
        PendingPermissionRequest(
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        )
}
