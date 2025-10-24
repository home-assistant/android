package io.homeassistant.companion.android.compose

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Fake activity result registry to invoke the callback of rememberLocationPermission
 */
class LocationPermissionActivityResultRegistry(val granted: Boolean) : ActivityResultRegistry() {
    val permissionRequested = mutableListOf<String>()

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        if (contract is ActivityResultContracts.RequestMultiplePermissions) {
            (input as? Array<String>)?.toList()?.let {
                permissionRequested.addAll(it)
            }
            dispatchResult(requestCode, mapOf("android.permission.ACCESS_FINE_LOCATION" to granted, "android.permission.ACCESS_COARSE_LOCATION" to granted))
        } else if (contract is ActivityResultContracts.RequestPermission) {
            permissionRequested.add(input.toString())
            dispatchResult(requestCode, granted)
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
}
