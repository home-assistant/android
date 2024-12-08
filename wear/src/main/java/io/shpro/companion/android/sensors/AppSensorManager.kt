package io.shpro.companion.android.sensors

import io.shpro.companion.android.BuildConfig
import io.shpro.companion.android.common.sensors.AppSensorManagerBase

class AppSensorManager : AppSensorManagerBase() {

    override fun getCurrentVersion(): String = BuildConfig.VERSION_NAME
}
