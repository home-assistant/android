package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process.myPid
import android.os.Process.myUid
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {

    val name: String
    val availableSensors: List<BasicSensor>

    data class BasicSensor(
        val id: String,
        val type: String,
        val name: String,
        val deviceClass: String? = null,
        val unitOfMeasurement: String? = null
    ) {
        fun toSensorRegistration(
            state: Any,
            mdiIcon: String,
            attributes: Map<String, Any>
        ): SensorRegistration<Any> {
            return SensorRegistration(
                id,
                state,
                type,
                mdiIcon,
                attributes,
                name,
                deviceClass,
                unitOfMeasurement
            )
        }
    }

    fun requiredPermissions(): Array<String>

    fun checkPermission(context: Context): Boolean {
        return requiredPermissions().all {
            context.checkPermission(it, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getSensorData(context: Context, sensorId: String): SensorRegistration<Any>

    fun getEnabledSensorData(context: Context, sensorId: String): SensorRegistration<Any>? {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val hasPermission = checkPermission(context)

        var sensor = sensorDao.get(sensorId)

        if (sensor == null) {
            sensor = Sensor(
                sensorId,
                hasPermission,
                false,
                ""
            )
            sensorDao.add(sensor)
        } else {
            sensor.enabled = sensor.enabled && hasPermission
        }

        var sensorData: SensorRegistration<Any>? = null
        if (sensor.enabled)
            sensorData = getSensorData(context, sensorId)
        sensor.state = sensorData?.state?.toString() ?: ""
        sensorDao.update(sensor)
        return sensorData
    }
}
