package io.homeassistant.companion.android.frontend.permissions

/**
 * Checks whether the app holds a specific Android runtime permission.
 */
internal fun interface PermissionChecker {

    /**
     * @param permission The Android permission to check (e.g., [android.Manifest.permission.CAMERA])
     * @return `true` if the permission is granted, `false` otherwise
     */
    fun hasPermission(permission: String): Boolean
}
