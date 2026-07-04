package io.homeassistant.companion.android.sensors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.AppSensorManagerBase
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSensorManager @Inject constructor(
    @ApplicationContext applicationContext: Context,
    sensorRepository: SensorRepository,
    serverManager: ServerManager,
) : AppSensorManagerBase(applicationContext, sensorRepository, serverManager) {

    override fun getCurrentVersion(): String = BuildConfig.VERSION_NAME
}
