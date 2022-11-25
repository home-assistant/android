package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.sensors.AppSensorManagerBase

class AppSensorManager : AppSensorManagerBase() {
    override fun updateCurrentVersion(context: Context) {

        if (!isEnabled(context, currentVersion.id))
            return

        val state = BuildConfig.VERSION_NAME

        onSensorUpdated(
            context,
            currentVersion,
            state,
            currentVersion.statelessIcon,
            mapOf()
        )
    }
}
