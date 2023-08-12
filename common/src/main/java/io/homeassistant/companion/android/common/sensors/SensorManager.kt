package io.homeassistant.companion.android.common.sensors

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.core.content.getSystemService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

interface SensorManager {

    companion object {
        const val ENTITY_CATEGORY_DIAGNOSTIC = "diagnostic"
        const val ENTITY_CATEGORY_CONFIG = "config"
        const val STATE_CLASS_MEASUREMENT = "measurement"
        const val STATE_CLASS_TOTAL = "total"
        const val STATE_CLASS_TOTAL_INCREASING = "total_increasing"
        const val SENSOR_LISTENER_TIMEOUT = 60000
    }

    val name: Int

    data class BasicSensor(
        val id: String,
        val type: String,
        val name: Int = commonR.string.sensor,
        val descriptionId: Int = commonR.string.sensor_description_none,
        val statelessIcon: String = "",
        val deviceClass: String? = null,
        val unitOfMeasurement: String? = null,
        val docsLink: String? = null,
        val stateClass: String? = null,
        val entityCategory: String? = null,
        val updateType: UpdateType = UpdateType.WORKER,
        val enabledByDefault: Boolean = false
    ) {
        enum class UpdateType {
            INTENT, WORKER, LOCATION, CUSTOM
        }
    }

    /**
     * URL to a documentation page that describes this sensor
     */
    fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors"
    }

    /**
     * Get list of Android permissions that are required to use this sensor
     */
    fun requiredPermissions(sensorId: String): Array<String>

    fun checkPermission(context: Context, sensorId: String): Boolean {
        return requiredPermissions(sensorId).all {
            if (sensorId != "last_used_app") {
                context.checkPermission(it, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED
            } else {
                checkUsageStatsPermission(context)
            }
        }
    }

    fun checkUsageStatsPermission(context: Context): Boolean {
        val pm = context.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(context.packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(context.packageName, 0)
        }
        val appOpsManager = context.getSystemService<AppOpsManager>()
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, appInfo.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOpsManager?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, appInfo.uid, appInfo.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** @return `true` if this sensor is enabled on any server */
    fun isEnabled(context: Context, basicSensor: BasicSensor): Boolean {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val permission = checkPermission(context, basicSensor.id)
        return sensorDao.getAnyIsEnabled(
            basicSensor.id,
            serverManager(context).defaultServers.map { it.id },
            permission,
            basicSensor.enabledByDefault
        )
    }

    /** @return `true` if this sensor is enabled for the specified server */
    fun isEnabled(context: Context, basicSensor: BasicSensor, serverId: Int): Boolean {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val permission = checkPermission(context, basicSensor.id)
        return sensorDao.getOrDefault(
            basicSensor.id,
            serverId,
            permission,
            basicSensor.enabledByDefault
        )?.enabled == true
    }

    /** @return Set of server IDs for which this sensor is enabled */
    fun getEnabledServers(context: Context, basicSensor: BasicSensor): Set<Int> {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val permission = checkPermission(context, basicSensor.id)
        return sensorDao.get(basicSensor.id).filter { it.enabled && permission }.map { it.serverId }.toSet()
    }

    /**
     * Request to update a sensor, including any broadcast intent which may have triggered the request
     * The intent will be null if the update is being done on a timer, rather than as a result
     * of a broadcast being received.
     */
    fun requestSensorUpdate(context: Context, intent: Intent?) {
        // Few sensors care about the intent, so allow them to just implement the interface that
        // does not get passed that parameter.
        requestSensorUpdate(context)
    }

    /**
     * Request to update a sensor, without a corresponding broadcast intent.
     */
    fun requestSensorUpdate(context: Context)

    suspend fun getAvailableSensors(context: Context): List<BasicSensor>

    /**
     * Check if the user's device supports this type of sensor
     */
    fun hasSensor(context: Context): Boolean {
        return true
    }

    fun addSettingIfNotPresent(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        settingType: SensorSettingType,
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

    suspend fun enableDisableSetting(context: Context, sensor: BasicSensor, settingName: String, enabled: Boolean) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val settingEnabled = isSettingEnabled(context, sensor, settingName)
        if (enabled && !settingEnabled ||
            !enabled && settingEnabled
        ) {
            sensorDao.updateSettingEnabled(sensor.id, settingName, enabled)
        }
    }

    fun getToggleSetting(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        default: Boolean,
        enabled: Boolean = true
    ): Boolean {
        return getSetting(context, sensor, settingName, SensorSettingType.TOGGLE, default.toString(), enabled).toBoolean()
    }

    fun getNumberSetting(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        default: Int,
        enabled: Boolean = true
    ): Int {
        return getSetting(context, sensor, settingName, SensorSettingType.NUMBER, default.toString(), enabled).toIntOrNull() ?: default
    }

    /**
     * Get the stored setting value for...
     * @param default Value to use if the setting does not exist
     */
    fun getSetting(
        context: Context,
        sensor: BasicSensor,
        settingName: String,
        settingType: SensorSettingType,
        default: String,
        enabled: Boolean = true,
        entries: List<String> = arrayListOf()
    ): String {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val setting = sensorDao
            .getSettings(sensor.id)
            .firstOrNull { it.name == settingName }
            ?.value
        if (setting == null) {
            sensorDao.add(SensorSetting(sensor.id, settingName, default, settingType, enabled, entries = entries))
        }

        return setting ?: default
    }

    fun onSensorUpdated(
        context: Context,
        basicSensor: BasicSensor,
        state: Any,
        mdiIcon: String,
        attributes: Map<String, Any?>,
        forceUpdate: Boolean = false
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensors = sensorDao.get(basicSensor.id)
        if (sensors.isEmpty()) return

        sensors.forEach {
            val sensor = it.copy(
                state = state.toString(),
                stateType = when (state) {
                    is Boolean -> "boolean"
                    is Int -> "int"
                    is Number -> "float"
                    is String -> "string"
                    else -> throw IllegalArgumentException("Unknown Sensor State Type")
                },
                type = basicSensor.type,
                icon = mdiIcon,
                name = basicSensor.name.toString(),
                deviceClass = basicSensor.deviceClass,
                unitOfMeasurement = basicSensor.unitOfMeasurement,
                stateClass = basicSensor.stateClass,
                entityCategory = basicSensor.entityCategory,
                lastSentState = if (forceUpdate) null else it.lastSentState,
                lastSentIcon = if (forceUpdate) null else it.lastSentIcon
            )
            sensorDao.update(sensor)
        }
        sensorDao.replaceAllAttributes(
            basicSensor.id,
            attributes = attributes.map { item ->
                val valueType = when (item.value) {
                    is Boolean -> "boolean"
                    is Int -> "int"
                    is Long -> "long"
                    is Number -> "float"
                    is List<*> -> {
                        when {
                            (item.value as List<*>).all { it is Boolean } -> "listboolean"
                            (item.value as List<*>).all { it is Int } -> "listint"
                            (item.value as List<*>).all { it is Long } -> "listlong"
                            (item.value as List<*>).all { it is Number } -> "listfloat"
                            else -> "liststring"
                        }
                    }
                    else -> "string" // Always default to String for attributes
                }
                val value =
                    when {
                        valueType == "liststring" ->
                            jacksonObjectMapper().writeValueAsString((item.value as List<*>).map { it.toString() })
                        valueType.startsWith("list") ->
                            jacksonObjectMapper().writeValueAsString(item.value)
                        else ->
                            item.value.toString()
                    }

                Attribute(
                    basicSensor.id,
                    item.key,
                    value,
                    valueType
                )
            }
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SensorManagerEntryPoint {
        fun serverManager(): ServerManager
    }

    fun serverManager(context: Context) =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SensorManagerEntryPoint::class.java
        )
            .serverManager()
}

fun SensorManager.id(): String {
    val simpleName = this::class.simpleName ?: this::class.java.name
    return simpleName.lowercase(Locale.US).replace(" ", "_")
}
