package io.homeassistant.companion.android.common.util

/**
 * Checks whether the app holds a specific Android runtime permission.
 */
fun interface PermissionChecker {

    /**
     * @param permission The Android permission to check (e.g., [android.Manifest.permission.CAMERA])
     * @return `true` if the permission is granted, `false` otherwise
     */
    fun hasPermission(permission: String): Boolean
}
