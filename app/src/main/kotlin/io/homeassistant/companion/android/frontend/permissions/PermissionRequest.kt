package io.homeassistant.companion.android.frontend.permissions

import android.Manifest
import android.os.Build
import android.webkit.PermissionRequest as WebViewPermissionRequest
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * An Android runtime permission request with its own result-handling logic.
 *
 * Organized into two categories based on how many permissions are requested:
 * - [SinglePermission] — requests a single Android permission (e.g. storage, notifications)
 * - [MultiplePermissions] — requests multiple Android permissions at once (e.g. camera + microphone)
 *
 * The composable layer uses this distinction to pick the right system dialog
 * ([SinglePermissionEffect] vs [MultiplePermissionsEffect]), so adding a new permission
 * type only requires extending the appropriate category, no handler changes needed
 * unless the new type requires custom UI.
 *
 * Results use two shapes matching the categories:
 * - [Result.Multiple] — maps each permission to its grant status
 * - [Result.Single] — a single granted/denied boolean
 */
internal sealed class PermissionRequest<T : PermissionRequest.Result> {

    /** The Android runtime permissions to request. */
    abstract val permissions: List<String>

    /** Result of a permission request. */
    sealed interface Result {
        /** Result from a multi-permission system dialog. Maps each permission to its grant status. */
        data class Multiple(val permissions: Map<String, Boolean>) : Result

        /** Result from a single-permission dialog or a custom prompt. */
        data class Single(val granted: Boolean) : Result
    }

    /**
     * Handles the user's response to this permission request.
     *
     * Called by the composable layer after clearing the pending slot. The generic parameter [T]
     * ensures compile-time safety — each subclass receives its exact result type.
     *
     * @param result The typed result from the permission UI
     */
    abstract suspend fun onResult(result: T)

    /**
     * Base class for permission requests that involve a single Android permission.
     *
     * Subclasses go through [SinglePermissionEffect] (system dialog) by default.
     * Override with custom UI by adding a specific branch in the composable handler
     * (e.g. [Notification] uses a bottom sheet).
     */
    sealed class SinglePermission(val permission: String) : PermissionRequest<Result.Single>() {
        override val permissions: List<String> get() = listOf(permission)
    }

    /**
     * Base class for permission requests that involve multiple Android permissions.
     *
     * Subclasses go through [MultiplePermissionsEffect] (system dialog).
     */
    sealed class MultiplePermissions(override val permissions: List<String>) : PermissionRequest<Result.Multiple>()

    /**
     * A WebView permission request (camera, microphone) that maps Android permissions
     * back to WebView resource strings so the original [WebViewPermissionRequest] can be resolved.
     *
     * Combines resources that were already granted at request time with resources for which the
     * user just approved the Android permission, then issues a single [WebViewPermissionRequest.grant]
     * call. If nothing was granted at all, denies the entire request.
     *
     * @param webViewRequest The original [WebViewPermissionRequest] to grant/deny after the user responds
     * @param webViewResourcesByPermission Maps each Android permission to its WebView resource string
     * @param alreadyGrantedResources WebView resources already granted at request time, deferred so
     *        all resources can be granted in a single [WebViewPermissionRequest.grant] call
     */
    class WebView(
        val webViewRequest: WebViewPermissionRequest,
        androidPermissions: List<String>,
        val webViewResourcesByPermission: Map<String, String>,
        val alreadyGrantedResources: List<String> = emptyList(),
    ) : MultiplePermissions(permissions = androidPermissions) {

        override suspend fun onResult(result: Result.Multiple) {
            val newlyGrantedResources = result.permissions
                .filter { (_, granted) -> granted }
                .mapNotNull { (permission, _) -> webViewResourcesByPermission[permission] }

            val allGrantedResources = alreadyGrantedResources + newlyGrantedResources

            if (allGrantedResources.isNotEmpty()) {
                Timber.d("Granting WebView resources: $allGrantedResources")
                webViewRequest.grant(allGrantedResources.toTypedArray())
            } else {
                Timber.d("User denied all requested permissions, denying WebView request")
                webViewRequest.deny()
            }
        }
    }

    /**
     * A request for [Manifest.permission.WRITE_EXTERNAL_STORAGE].
     *
     * When the user grants the permission, [onGranted] is called to proceed with the
     * operation that was blocked (e.g. retrying a file download).
     *
     * @param onGranted Callback invoked after the user grants the permission
     */
    class ExternalStorage(private val onGranted: () -> Unit) :
        SinglePermission(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE) {

        override suspend fun onResult(result: Result.Single) {
            if (result.granted) {
                onGranted()
            }
        }
    }

    /**
     * A notification permission request that shows a bottom sheet prompt before the system dialog.
     *
     * Unlike other [SinglePermission] types, this is rendered as visible UI (a bottom sheet
     * with Allow/Deny buttons) rather than going straight to the system permission dialog.
     * The composable handler checks for this type explicitly before falling through to the
     * default [SinglePermissionEffect].
     *
     * @param serverId The server ID for persisting the user's choice
     * @param persistResult Callback to persist the notification permission choice (websocket
     *        settings on the minimal flavor, and "don't ask again" flag)
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Notification(val serverId: Int, private val persistResult: suspend (granted: Boolean) -> Unit) :
        SinglePermission(permission = Manifest.permission.POST_NOTIFICATIONS) {

        override suspend fun onResult(result: Result.Single) {
            persistResult(result.granted)
        }
    }
}
