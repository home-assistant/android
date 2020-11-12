package io.homeassistant.companion.android.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.R

object DisabledLocationHandler {
    fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm: LocationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    fun containsLocationPermission(permissions: Array<String>): Boolean {
        return permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) || permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
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
