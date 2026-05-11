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
import io.homeassistant.companion.android.settings.SettingsActivity
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
 * On Automotive OS we open [SettingsActivity] directly: routing through [LaunchActivity] would loop us back to
 * `androidx.car.app.activity.CarAppActivity` because [io.homeassistant.companion.android.launch.LaunchViewModel]
 * forces `AutomotiveRoute` whenever the device is automotive and a server is already registered (Play Store
 * requirement: no WebView on automotive).
 *
 * Sets any values that may be required for compatibility.
 */
fun startNativeActivity(carContext: CarContext) {
    Timber.d("Starting native activity")
    with(carContext) {
        val isAutomotive = isAutomotive()
        // The app must indicate the default display to be used to avoid a SecurityException on newer
        // Android versions. See: https://developer.android.com/training/cars/platforms/releases#android-14
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                .toBundle()
        } else {
            null
        }
        val targetActivity = if (isAutomotive) SettingsActivity::class.java else LaunchActivity::class.java
        startActivity(
            Intent(carContext, targetActivity).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            options,
        )
        if (isAutomotive) {
            finishCarApp()
        }
    }
}
