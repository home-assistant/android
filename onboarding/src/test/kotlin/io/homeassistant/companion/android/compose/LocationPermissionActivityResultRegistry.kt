package io.homeassistant.companion.android.compose

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat

/**
 * Fake activity result registry to invoke the callback of rememberLocationPermission
 */
class LocationPermissionActivityResultRegistry(val granted: Boolean) : ActivityResultRegistry() {
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        if (contract is ActivityResultContracts.RequestMultiplePermissions) {
            dispatchResult(requestCode, mapOf("android.permission.ACCESS_FINE_LOCATION" to granted, "android.permission.ACCESS_COARSE_LOCATION" to granted))
        } else if (contract is ActivityResultContracts.RequestPermission) {
            dispatchResult(requestCode, granted)
        }
    }
}
