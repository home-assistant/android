package io.homeassistant.companion.android.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.R

object DisabledLocationHandler {
    fun isLocationEnabled(context: Context, fineLocation: Boolean): Boolean {
        val lm: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return if (VERSION.SDK_INT >= VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            (!fineLocation && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || (fineLocation && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)))
        }
    }

    fun containsFineLocationPermission(permissions: Array<String>): Boolean {
        return permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun containsCoarseLocationPermission(permissions: Array<String>): Boolean {
        return permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun showLocationDisabledWarnDialog(activity: Activity, context: Context, message: String, withDisableOption: Boolean = false, callback: (() -> Unit)? = null) {
        var positionTextId = R.string.confirm_positive
        var negativeTextId = R.string.confirm_negative
        if (withDisableOption && callback != null) {
            positionTextId = R.string.settings
            negativeTextId = R.string.location_disabled_option_disable
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.location_disabled_title)
            .setMessage(message)
            .setPositiveButton(positionTextId) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                context.startActivity(intent)
            }
            .setNegativeButton(negativeTextId) { _, _ ->
                if (withDisableOption && callback != null) {
                    callback()
                }
            }
            .show()
    }
}
