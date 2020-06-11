package io.homeassistant.companion.android.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.background.LocationBroadcastReceiver

class PermissionManager {

    companion object {
        const val LOCATION_REQUEST_CODE = 1

        /**
         * Check if the a given permission is granted
         */
        fun hasPermission(context: Context, permission: String): Boolean {
            return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Returns TRUE if all permissions in the grantResults were granted
         */
        fun arePermissionsGranted(grantResults: IntArray): Boolean {
            return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }

        /**
         * Check if the required location permissions are granted
         */
        fun checkLocationPermission(context: Context): Boolean {
            for (permission in getLocationPermissionArray()) {
                if (!hasPermission(context, permission)) {
                    return false
                }
            }
            return true
        }

        /**
         * Returns an Array with required location permissions.
         * ACCESS_FINE_LOCATION and, if API level >= 29, ACCESS_BACKGROUND_LOCATION.
         */
        fun getLocationPermissionArray(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun validateLocationPermissions(
            requestCode: Int,
            grantResults: IntArray
        ): Boolean {
            return requestCode == LOCATION_REQUEST_CODE && arePermissionsGranted(grantResults)
        }

        fun requestLocationPermissions(fragment: Fragment) {
            fragment.requestPermissions(getLocationPermissionArray(), LOCATION_REQUEST_CODE)
        }

        fun restartLocationTracking(context: Context) {
            val intent = Intent(context, LocationBroadcastReceiver::class.java)
            intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

            context.sendBroadcast(intent)
        }
    }
}
