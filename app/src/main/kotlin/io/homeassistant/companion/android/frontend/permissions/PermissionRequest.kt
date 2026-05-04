package io.homeassistant.companion.android.frontend.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * An Android runtime permission request enqueued in [PermissionManager].
 *
 * Subclasses fall into one of three categories the UI dispatches on:
 * - [Notification] — the bottom-sheet prompt, allow/deny + dismiss-without-answering
 * - [SinglePermission] — a single Android permission requested via the system dialog
 * - [MultiplePermissions] — several Android permissions requested at once via the system dialog
 *
 * Each category exposes the result-delivery callback the UI invokes once the user has responded;
 * [PermissionManager] wires those callbacks into [io.homeassistant.companion.android.common.util.SingleSlotQueue.awaitResult]
 * so the slot is freed automatically and the manager can react to the result inline.
 */
internal sealed interface PermissionRequest {

    /** The Android runtime permissions to request. */
    val permissions: List<String>

    /** Base class for requests that involve a single Android permission resolved via the system dialog. */
    sealed class SinglePermission(val permission: String) : PermissionRequest {
        override val permissions: List<String> get() = listOf(permission)

        /** Called by the UI once the user has answered the system dialog. */
        abstract val onResult: (Boolean) -> Unit
    }

    /** Base class for requests that involve multiple Android permissions resolved at once. */
    sealed class MultiplePermissions(override val permissions: List<String>) : PermissionRequest {

        /** Called by the UI once the user has answered the multi-permission system dialog. */
        abstract val onResult: (Map<String, Boolean>) -> Unit
    }

    /**
     * A WebView permission request (camera, microphone) that goes through the system multi-permission
     * dialog. The grant/deny on the original WebView request is handled by [PermissionManager] after
     * [onResult] is called.
     */
    class WebView(androidPermissions: List<String>, override val onResult: (Map<String, Boolean>) -> Unit) :
        MultiplePermissions(permissions = androidPermissions)

    /** A request for [Manifest.permission.WRITE_EXTERNAL_STORAGE]. */
    class ExternalStorage(override val onResult: (Boolean) -> Unit) :
        SinglePermission(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * A notification permission request rendered as a bottom sheet (with an explicit allow/deny)
     * before the system dialog, plus a separate dismiss path when the user closes the sheet without
     * answering. Modelled as its own top-level category — not a [SinglePermission] — because
     * dismiss-without-answering is a third outcome only this prompt has.
     *
     * @param serverId The server the prompt is being shown for
     * @param onResult Called by the UI with `true` (allow) or `false` (deny)
     * @param onDismiss Called by the UI when the user closes the sheet without answering. The
     *        manager treats this as "ask again later" — no preference is persisted.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Notification(val serverId: Int, val onResult: (Boolean) -> Unit, val onDismiss: () -> Unit) :
        PermissionRequest {
        override val permissions: List<String> get() = listOf(Manifest.permission.POST_NOTIFICATIONS)
    }
}
