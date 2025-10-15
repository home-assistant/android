package io.homeassistant.companion.android.compose

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

private val foregroundLocationPermissions: List<String> = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    // TODO drop this requirement https://github.com/home-assistant/android/issues/5931
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.S
    ) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        Manifest.permission.BLUETOOTH
    },
)
val locationPermissions: List<String> = foregroundLocationPermissions.run {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this + Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else {
        this
    }
}

/**
 * Remembers the state of location permissions and handles requesting them.
 *
 * This function manages both foreground (FINE/COARSE) and background location permissions.
 * It ensures that background permission is requested only after foreground permissions are granted,
 * as required by the Android system.
 *
 * @param onPermissionResult A callback function that is invoked with `true` if all requested
 *                           permissions are granted, and `false` otherwise.
 * @return A [MultiplePermissionsState] object that can be used to observe and manage the
 *         state of the requested location permissions.
 *
 * @see Manifest.permission.ACCESS_FINE_LOCATION
 * @see Manifest.permission.ACCESS_COARSE_LOCATION
 * @see Manifest.permission.ACCESS_BACKGROUND_LOCATION
 * @see MultiplePermissionsState
 * @see rememberPermissionState
 * @see rememberMultiplePermissionsState
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermission(onPermissionResult: (Boolean) -> Unit): MultiplePermissionsState {
    val backgroundPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) {
            onPermissionResult(it)
        }
    } else {
        null
    }

    return rememberMultiplePermissionsState(
        foregroundLocationPermissions,
        onPermissionsResult = { permissionStatus ->
            if (permissionStatus.all { it.value }) {
                backgroundPermissionState?.launchPermissionRequest() ?: onPermissionResult(true)
            } else {
                onPermissionResult(false)
            }
        },
    )
}
