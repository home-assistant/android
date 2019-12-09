package io.homeassistant.companion.android.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.background.LocationBroadcastReceiver

class PermissionManager {

    companion object {
        private const val LOCATION_REQUEST_CODE = 1

        fun haveLocationPermissions(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }

        @SuppressLint("InlinedApi")
        fun getLocationPermissionArray(): Array<String> {
            var retVal = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            if (Build.VERSION.SDK_INT >= 21)
                retVal = retVal.plus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            return retVal
        }

        fun validateLocationPermissions(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ): Boolean {
            return requestCode == LOCATION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }

        fun requestLocationPermissions(fragment: Fragment) {
            fragment.requestPermissions(getLocationPermissionArray(), LOCATION_REQUEST_CODE)
        }

        fun restartLocationTracking(context: Context, activity: Activity) {
            val intent = Intent(context, LocationBroadcastReceiver::class.java)
            intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

            activity.sendBroadcast(intent)
        }
    }
}
