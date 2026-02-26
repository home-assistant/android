package io.homeassistant.companion.android.util.vehicle

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.view.Display
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

/**
 * Start the main native (non-vehicle UI) Home Assistant activity on the connected phone (Auto) or default vehicle
 * display (Automotive). This will allow onboarding or using dashboards, depending on the logged in state.
 *
 * Sets any values that may be required for compatibility.
 */
fun startNativeActivity(carContext: CarContext) {
    Timber.d("Starting native activity")
    with(carContext) {
        // The app must indicate the default display to be used to avoid a SecurityException on newer
        // Android versions. See: https://developer.android.com/training/cars/platforms/releases#android-14
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                .toBundle()
        } else {
            null
        }
        startActivity(
            Intent(
                carContext,
                LaunchActivity::class.java,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            options
        )
        if (isAutomotive()) {
            finishCarApp()
        }
    }
}
