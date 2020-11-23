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

    fun containsLocationPermission(permissions: Array<String>, fineLocation: Boolean? = null): Boolean {
        val containsFineLocation = permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
        val containsCoarseLocation = permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)

        return if (fineLocation == null) {
            containsFineLocation && containsCoarseLocation
        } else {
            if (fineLocation) containsFineLocation
            else containsCoarseLocation
        }
    }

    fun showLocationDisabledWarnDialog(activity: Activity, settings: Array<String>, withDisableOption: Boolean = false, callback: (() -> Unit)? = null) {
        var positionTextId = R.string.confirm_positive
        var negativeTextId = R.string.confirm_negative
        if (withDisableOption && callback != null) {
            negativeTextId = R.string.location_disabled_option_disable
        }

        var parameters = ""
        for (setting in settings)
            parameters += "- $setting\n"
        AlertDialog.Builder(activity)
            .setTitle(R.string.location_disabled_title)
            .setMessage(activity.applicationContext.getString(R.string.location_disabled_message, parameters))
            .setPositiveButton(positionTextId) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                activity.applicationContext.startActivity(intent)
            }
            .setNegativeButton(negativeTextId) { _, _ ->
                if (withDisableOption && callback != null) {
                    callback()
                }
            }
            .show()
    }
}
