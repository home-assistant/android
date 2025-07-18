package io.homeassistant.companion.android.common.util

import android.Manifest
import android.content.Context
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.common.R as commonR

object LocationPermissionInfoHandler {

    fun showLocationPermInfoDialogIfNeeded(
        context: Context,
        permissions: Array<String>,
        continueYesCallback: () -> Unit,
        continueNoCallback: (() -> Unit)? = null,
    ) {
        if (permissions.any {
                it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }
        ) {
            AlertDialog.Builder(context)
                .setTitle(commonR.string.location_perm_info_title)
                .setMessage(context.getString(commonR.string.location_perm_info_message))
                .setPositiveButton(commonR.string.confirm_positive) { dialog, _ ->
                    dialog.dismiss()
                    continueYesCallback()
                }
                .setNegativeButton(commonR.string.confirm_negative) { dialog, _ ->
                    dialog.dismiss()
                    if (continueNoCallback != null) continueNoCallback()
                }
                .show()
        } else {
            continueYesCallback()
        }
    }
}
