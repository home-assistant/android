package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager

class HealthConnectSensorManager : SensorManager {
    companion object {
        fun getPermissionResultContract(context: Context): ActivityResultContract<Set<String>, Set<String>>? =
            null
    }

    override val name: Int
        get() = R.string.sensor_name_health_connect

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }
}
