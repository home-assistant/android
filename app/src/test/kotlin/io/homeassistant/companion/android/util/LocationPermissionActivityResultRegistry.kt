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
 * Fake activity result registry to invoke the callback of rememberLocationPermission
 * and rememberBatteryOptimizationLauncher.
 */
class LocationPermissionActivityResultRegistry(val granted: Boolean) : ActivityResultRegistry() {
    val permissionRequested = mutableListOf<String>()
    var batteryOptimizationRequested = false
        private set

    @Suppress("UNCHECKED_CAST")
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        when (contract) {
            is ActivityResultContracts.RequestMultiplePermissions -> {
                (input as? Array<String>)?.toList()?.let {
                    permissionRequested.addAll(it)
                }
                dispatchResult(requestCode, mapOf("android.permission.ACCESS_FINE_LOCATION" to granted, "android.permission.ACCESS_COARSE_LOCATION" to granted))
            }
            is ActivityResultContracts.RequestPermission -> {
                permissionRequested.add(input.toString())
                dispatchResult(requestCode, granted)
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

    fun assertLocationPermissionRequested(requestBackground: Boolean = true) {
        assertEquals(
            mutableListOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.BLUETOOTH_CONNECT",
            ).apply { if (requestBackground) add("android.permission.ACCESS_BACKGROUND_LOCATION") },
            permissionRequested,
        )
    }

    fun assertLocationPermissionNotRequested() {
        assertEquals(emptyList<String>(), permissionRequested)
    }

    fun assertBatteryOptimizationRequested() {
        assertTrue(batteryOptimizationRequested)
    }

    fun assertBatteryOptimizationNotRequested() {
        assertFalse(batteryOptimizationRequested)
    }
}
