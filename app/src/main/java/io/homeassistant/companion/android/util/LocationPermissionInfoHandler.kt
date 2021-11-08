package io.homeassistant.companion.android.util

import android.Manifest
import android.content.Context
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.R

object LocationPermissionInfoHandler {

    fun showLocationPermInfoDialogIfNeeded(context: Context, permissions: Array<String>, continueYesCallback: () -> Unit, continueNoCallback: (() -> Unit)? = null) {
        if (permissions.any {
            it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        ) {
            AlertDialog.Builder(context)
                .setTitle(R.string.location_perm_info_title)
                .setMessage(context.getString(R.string.location_perm_info_message))
                .setPositiveButton(R.string.confirm_positive) { dialog, _ ->
                    dialog.dismiss()
                    continueYesCallback()
                }
                .setNegativeButton(R.string.confirm_negative) { dialog, _ ->
                    dialog.dismiss()
                    if (continueNoCallback != null) continueNoCallback()
                }
                .show()
        } else continueYesCallback()
    }
}
