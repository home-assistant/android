package io.homeassistant.companion.android.util.vehicle

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.view.Display
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.settings.SettingsActivity
import timber.log.Timber

fun settingsAction(carContext: CarContext): Action {
    return Action.Builder()
        .setIcon(
            CarIcon.Builder(
                IconicsDrawable(carContext, CommunityMaterial.Icon.cmd_cog).apply {
                    sizeDp = 64
                }.toAndroidIconCompat(),
            )
                .setTint(CarColor.DEFAULT)
                .build(),
        )
        .setOnClickListener {
            startSettingsActivity(carContext)
        }.build()
}

private fun startSettingsActivity(carContext: CarContext) {
    Timber.d("Starting settings activity")
    // The app must indicate the default display to be used to avoid a SecurityException on newer
    // Android versions. See: https://developer.android.com/training/cars/platforms/releases#android-14
    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()
    } else {
        null
    }
    carContext.startActivity(
        SettingsActivity.newInstance(carContext).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        options,
    )
}
