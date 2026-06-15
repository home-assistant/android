package io.homeassistant.companion.android.util

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Fake [ActivityResultRegistry] that intercepts permission-related activity result contracts
 * and records them for test assertions.
 *
 * Handles:
 * - [ActivityResultContracts.RequestMultiplePermissions] — records requested permissions,
 *   dispatches per-permission grant based on [grantedPermissions]
 * - [ActivityResultContracts.RequestPermission] — records single permission,
 *   dispatches grant based on [grantedPermissions]
 * - [ActivityResultContracts.StartActivityForResult] — tracks battery optimization requests
 *
 * @param grantedPermissions Set of permission strings that should be reported as granted.
 *        Permissions not in this set are reported as denied.
 */
class FakePermissionResultRegistry(
    private val grantedPermissions: Set<String> = emptySet(),
) : ActivityResultRegistry() {

    /**
     * Convenience constructor that grants or denies all requested permissions uniformly.
     *
     * When [grantAll] is `true`, every requested permission will be reported as granted.
     * When `false`, every permission will be denied.
     */
    constructor(grantAll: Boolean) : this(if (grantAll) ALL_PERMISSIONS else emptySet())

    /** All permissions that were requested via any permission contract. */
    val requestedPermissions = mutableListOf<String>()

    /** Whether a battery optimization request was launched. */
    var batteryOptimizationRequested = false
        private set

    @Suppress("UNCHECKED_CAST")
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        when (contract) {
            is ActivityResultContracts.RequestMultiplePermissions -> {
                val permissions = (input as? Array<String>)?.toList().orEmpty()
                requestedPermissions.addAll(permissions)
                val results = permissions.associateWith { it in grantedPermissions }
                dispatchResult(requestCode, results)
            }
            is ActivityResultContracts.RequestPermission -> {
                val permission = input as String
                requestedPermissions.add(permission)
                dispatchResult(requestCode, permission in grantedPermissions)
            }
            is ActivityResultContracts.StartActivityForResult -> {
                val intent = input as? Intent
                if (intent?.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
                    batteryOptimizationRequested = true
                    dispatchResult(requestCode, ActivityResult(Activity.RESULT_OK, null) as O)
                }
            }
        }
    }

    /** Asserts that exactly the given [permissions] were requested, in order. */
    fun assertPermissionsRequested(vararg permissions: String) {
        assertEquals(permissions.toList(), requestedPermissions)
    }

    /** Asserts that no permissions were requested. */
    fun assertNoPermissionsRequested() {
        assertTrue(requestedPermissions.isEmpty(), "Expected no permissions requested but got: $requestedPermissions")
    }

    /** Asserts that a battery optimization request was launched. */
    fun assertBatteryOptimizationRequested() {
        assertTrue(batteryOptimizationRequested)
    }

    /** Asserts that no battery optimization request was launched. */
    fun assertBatteryOptimizationNotRequested() {
        assertFalse(batteryOptimizationRequested)
    }

    /** Asserts that the standard location permissions were requested, optionally with background. */
    fun assertLocationPermissionRequested(requestBackground: Boolean = true) {
        val expected = mutableListOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.BLUETOOTH_CONNECT",
        ).apply { if (requestBackground) add("android.permission.ACCESS_BACKGROUND_LOCATION") }
        assertEquals(expected, requestedPermissions)
    }

    /** Asserts that no location permissions were requested. */
    fun assertLocationPermissionNotRequested() {
        assertNoPermissionsRequested()
    }
}

/** Sentinel set where [contains] always returns true, used to grant all requested permissions. */
private val ALL_PERMISSIONS = object : Set<String> by emptySet() {
    override fun contains(element: String): Boolean = true
}
