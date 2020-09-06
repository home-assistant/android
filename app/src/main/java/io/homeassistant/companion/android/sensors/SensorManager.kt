package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process.myPid
import android.os.Process.myUid
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor

interface SensorManager {

    val name: Int
    val availableSensors: List<BasicSensor>
    val enabledByDefault: Boolean

    data class BasicSensor(
        val id: String,
        val type: String,
        val name: Int = R.string.sensor,
        val descriptionId: Int = R.string.sensor_description_none,
        val deviceClass: String? = null,
        val unitOfMeasurement: String? = null
    )

    fun requiredPermissions(): Array<String>

    fun checkPermission(context: Context): Boolean {
        return requiredPermissions().all {
            context.checkPermission(it, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isEnabled(context: Context, sensorId: String): Boolean {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        var sensor = sensorDao.get(sensorId)
        val permission = checkPermission(context)

        // If we haven't created the entity yet do so and default to enabled if required
        if (sensor == null) {
            sensor = Sensor(sensorId, permission && enabledByDefault, false, "")
            sensorDao.add(sensor)
        }

        // If we don't have permission but we are still enabled then we aren't really enabled.
            if (sensor.enabled && !permission) {
                sensor.enabled = false
                sensorDao.update(sensor)
            }

        return sensor.enabled
    }

    fun requestSensorUpdate(context: Context)

    fun hasSensor(context: Context): Boolean {
        return true
    }

    fun onSensorUpdated(
        context: Context,
        basicSensor: BasicSensor,
        state: Any,
        mdiIcon: String,
        attributes: Map<String, Any?>
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensor = sensorDao.get(basicSensor.id) ?: return

        sensor.id = basicSensor.id
        sensor.state = state.toString()
        sensor.stateType = when (state) {
            is Boolean -> "boolean"
            is Int -> "int"
            is Number -> "float"
            is String -> "string"
            else -> throw IllegalArgumentException("Unknown Sensor State Type")
        }
        sensor.type = basicSensor.type
        sensor.icon = mdiIcon
        sensor.name = basicSensor.name.toString()
        sensor.deviceClass = basicSensor.deviceClass
        sensor.unitOfMeasurement = basicSensor.unitOfMeasurement

        sensorDao.update(sensor)

        sensorDao.clearAttributes(basicSensor.id)
        attributes.entries.forEach { entry ->
            sensorDao.add(Attribute(basicSensor.id, entry.key, entry.value.toString()))
        }
    }
}
