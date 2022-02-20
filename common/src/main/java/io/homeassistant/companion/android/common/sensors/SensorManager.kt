package io.homeassistant.companion.android.common.sensors

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

interface SensorManager {

    companion object {
        const val ENTITY_CATEGORY_DIAGNOSTIC = "diagnostic"
        const val ENTITY_CATEGORY_CONFIG = "config"
        const val STATE_CLASS_MEASUREMENT = "measurement"
        const val STATE_CLASS_TOTAL = "total"
        const val STATE_CLASS_TOTAL_INCREASING = "total_increasing"
    }

    val name: Int
    val enabledByDefault: Boolean

    data class BasicSensor(
        val id: String,
        val type: String,
        val name: Int = commonR.string.sensor,
        val descriptionId: Int = commonR.string.sensor_description_none,
        val deviceClass: String? = null,
        val unitOfMeasurement: String? = null,
        val docsLink: String? = null,
        val stateClass: String? = null,
        val entityCategory: String? = null
    )

    fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors"
    }

    fun requiredPermissions(sensorId: String): Array<String>

    fun checkPermission(context: Context, sensorId: String): Boolean {
        return requiredPermissions(sensorId).all {
            if (sensorId != "last_used_app")
                context.checkPermission(it, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED
            else {
                checkUsageStatsPermission(context)
            }
        }
    }

    fun checkUsageStatsPermission(context: Context): Boolean {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val appOpsManager = context.getSystemService<AppOpsManager>()!!
        val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, appInfo.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isEnabled(context: Context, sensorId: String): Boolean {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        var sensor = sensorDao.get(sensorId)
        val permission = checkPermission(context, sensorId)

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

    // Request to update a sensor, including any broadcast intent which may have triggered the request
    // The intent will be null if the update is being done on a timer, rather than as a result
    // of a broadcast being received.
    fun requestSensorUpdate(context: Context, intent: Intent?) {
        // Few sensors care about the intent, so allow them to just implement the interface that
        // does not get passed that parameter.
        requestSensorUpdate(context)
    }

    fun requestSensorUpdate(context: Context)

    fun getAvailableSensors(context: Context): List<BasicSensor>

    fun hasSensor(context: Context): Boolean {
        return true
    }

    fun addSettingIfNotPresent(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        settingType: String,
        default: String,
        enabled: Boolean = true
    ) {
        getSetting(context, sensor, settingName, settingType, default, enabled)
    }

    fun isSettingEnabled(context: Context, sensor: BasicSensor, settingName: String): Boolean {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val setting = sensorDao
            .getSettings(sensor.id)
            .firstOrNull { it.name == settingName }
        return setting?.enabled ?: false
    }

    fun enableDisableSetting(context: Context, sensor: BasicSensor, settingName: String, enabled: Boolean) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val settingEnabled = isSettingEnabled(context, sensor, settingName)
        if (enabled && !settingEnabled ||
            !enabled && settingEnabled
        ) {
            sensorDao.updateSettingEnabled(sensor.id, settingName, enabled)
        }
    }

    fun getSetting(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        settingType: String,
        default: String,
        enabled: Boolean = true
    ): String {
        return getSetting(context, sensor, settingName, settingType, arrayListOf(), default, enabled)
    }

    fun getSetting(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        settingType: String,
        entries: List<String> = arrayListOf(),
        default: String,
        enabled: Boolean = true
    ): String {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val setting = sensorDao
            .getSettings(sensor.id)
            .firstOrNull { it.name == settingName }
            ?.value
        if (setting == null)
            sensorDao.add(SensorSetting(sensor.id, settingName, default, settingType, entries, enabled))

        return setting ?: default
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
        sensor.stateClass = basicSensor.stateClass
        sensor.entityCategory = basicSensor.entityCategory

        sensorDao.update(sensor)
        sensorDao.clearAttributes(basicSensor.id)

        for (item in attributes) {
            val valueType = when (item.value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Number -> "float"
                else -> "string" // Always default to String for attributes
            }

            sensorDao.add(
                Attribute(
                    basicSensor.id,
                    item.key,
                    item.value.toString(),
                    valueType
                )
            )
        }
    }
}

fun SensorManager.id(): String {
    val simpleName = this::class.simpleName ?: this::class.java.name
    return simpleName.lowercase(Locale.US).replace(" ", "_")
}
