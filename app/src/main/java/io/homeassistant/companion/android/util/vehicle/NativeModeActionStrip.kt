package io.homeassistant.companion.android.util.vehicle

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.launch.LaunchActivity

private const val TAG = "NativeActionStrip"

fun nativeModeActionStrip(carContext: CarContext): ActionStrip {
    return ActionStrip.Builder().addAction(
        Action.Builder()
            .setTitle(carContext.getString(R.string.aa_launch_native))
            .setOnClickListener {
                startNativeActivity(carContext)
            }.build()
    ).build()
}

fun startNativeActivity(carContext: CarContext) {
    Log.i(TAG, "Starting login activity")
    with(carContext) {
        startActivity(
            Intent(
                carContext,
                LaunchActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        if (carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            finishCarApp()
        }
    }
}
