package io.homeassistant.companion.android.util.vehicle

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.launch.LaunchActivity
import timber.log.Timber

fun nativeModeAction(carContext: CarContext): Action {
    return Action.Builder()
        .setTitle(carContext.getString(R.string.aa_launch_native))
        .setOnClickListener {
            startNativeActivity(carContext)
        }.build()
}

fun startNativeActivity(carContext: CarContext) {
    Timber.i("Starting login activity")
    with(carContext) {
        startActivity(
            Intent(
                carContext,
                LaunchActivity::class.java,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        if (carContext.isAutomotive()) {
            finishCarApp()
        }
    }
}
