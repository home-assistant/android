package io.homeassistant.companion.android.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.sensors.LocationBroadcastReceiver

class PermissionManager {

    companion object {
        const val LOCATION_REQUEST_CODE = 1
        const val PHONE_STATE_REQUEST_CODE = 2

        /**
         * Check if the a given permission is granted
         */
        private fun hasPermission(context: Context, permission: String): Boolean {
            return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Returns TRUE if all permissions in the grantResults were granted
         */
        private fun arePermissionsGranted(grantResults: IntArray): Boolean {
            return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }

        /**
         * Check if the required location permissions are granted
         */
        private fun checkLocationPermission(context: Context): Boolean {
            for (permission in getLocationPermissionArray()) {
                if (!hasPermission(context, permission)) {
                    return false
                }
            }
            return true
        }

        private fun checkBluetoothPermission(context: Context): Boolean {
            for (permission in getBluetoohPermissionArray()) {
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
        private fun getLocationPermissionArray(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        private fun getBluetoohPermissionArray(): Array<String> {
            return arrayOf(Manifest.permission.BLUETOOTH)
        }

        private fun validateLocationPermissions(
            requestCode: Int,
            grantResults: IntArray
        ): Boolean {
            return requestCode == LOCATION_REQUEST_CODE && arePermissionsGranted(grantResults)
        }

        private fun requestLocationPermissions(fragment: Fragment) {
            fragment.requestPermissions(getLocationPermissionArray(), LOCATION_REQUEST_CODE)
        }

        private fun restartLocationTracking(context: Context) {
            val intent = Intent(context, LocationBroadcastReceiver::class.java)
            intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

            context.sendBroadcast(intent)
        }

        private fun getPhonePermissionArray(): Array<String> {
            return arrayOf(Manifest.permission.READ_PHONE_STATE)
        }

        private fun requestPhoneStatePermissions(fragment: Fragment) {
            fragment.requestPermissions(getPhonePermissionArray(), PHONE_STATE_REQUEST_CODE)
        }

        private fun checkPhoneStatePermission(context: Context): Boolean {
            return hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        }

        private fun validatePhoneStatePermissions(
            requestCode: Int,
            grantResults: IntArray
        ): Boolean {
            return requestCode == PHONE_STATE_REQUEST_CODE && arePermissionsGranted(grantResults)
        }
    }
}
