package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivitySensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {

    fun onReceive(intent: Intent) {
        // Noop
    }
    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        // Noop
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        // Noop
    }
}
