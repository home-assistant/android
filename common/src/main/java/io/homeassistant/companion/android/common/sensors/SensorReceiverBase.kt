package io.homeassistant.companion.android.common.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.sensorCoreSyncChannel
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

abstract class SensorReceiverBase : BroadcastReceiver() {
    companion object {
        const val ACTION_UPDATE_SENSOR = "io.homeassistant.companion.android.UPDATE_SENSOR"
        const val ACTION_UPDATE_SENSORS = "io.homeassistant.companion.android.UPDATE_SENSORS"
        const val EXTRA_SENSOR_ID = "sensorId"

        fun shouldDoFastUpdates(context: Context): Boolean {
            val settingDao = AppDatabase.getInstance(context).settingsDao().get(0)
            return when (settingDao?.sensorUpdateFrequency) {
                SensorUpdateFrequencySetting.FAST_ALWAYS -> true
                SensorUpdateFrequencySetting.FAST_WHILE_CHARGING -> {
                    val batteryStatusIntent =
                        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    return batteryStatusIntent?.let { BatterySensorManager.getIsCharging(it) } ?: false
                }
                else -> false
            }
        }
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    protected abstract val tag: String
    protected abstract val currentAppVersion: String
    protected abstract val managers: List<SensorManager>

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var sensorDao: SensorDao

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    )

    protected abstract val skippableActions: Map<String, String>

    protected abstract fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int
    ): PendingIntent?

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "Received intent: ${intent.action}")
        if (skippableActions.containsKey(intent.action)) {
            val sensor = skippableActions[intent.action]
            if (!isSensorEnabled(sensor!!)) {
                Log.d(
                    tag,
                    String.format
                    (
                        "Sensor %s corresponding to received event %s is disabled, skipping sensors update",
                        sensor,
                        intent.action
                    )
                )
                return
            }
        }

        if (isSensorEnabled(LastUpdateManager.lastUpdate.id)) {
            LastUpdateManager().sendLastUpdate(context, intent.action)
            val allSettings = sensorDao.getSettings(LastUpdateManager.lastUpdate.id)
            for (setting in allSettings) {
                if (setting.value != "" && intent.action == setting.value) {
                    val eventData = intent.extras?.keySet()?.map { it.toString() to intent.extras?.get(it).toString() }?.toMap()?.plus("intent" to intent.action.toString())
                        ?: mapOf("intent" to intent.action.toString())
                    Log.d(tag, "Event data: $eventData")
                    serverManager.defaultServers.forEach { server ->
                        ioScope.launch {
                            try {
                                serverManager.integrationRepository(server.id).fireEvent(
                                    "android.intent_received",
                                    eventData as Map<String, Any>
                                )
                                Log.d(tag, "Event successfully sent to Home Assistant")
                            } catch (e: Exception) {
                                Log.e(tag, "Unable to send event data to Home Assistant", e)
                            }
                        }
                    }
                }
            }
        }

        ioScope.launch {
            if (intent.action == Intent.ACTION_TIME_TICK && !shouldDoFastUpdates(context)) {
                Log.i(tag, "Skipping faster update because not charging/different preference")
                return@launch
            }
            if (intent.action == ACTION_UPDATE_SENSOR) {
                val sensorId = intent.getStringExtra(EXTRA_SENSOR_ID)
                if (sensorId != null) {
                    updateSensor(context, sensorId)
                }
            } else {
                updateSensors(context, serverManager, sensorDao, intent)
                if (chargingActions.contains(intent.action)) {
                    // Add a 5 second delay to perform another update so charging state updates completely.
                    // This is necessary as the system needs a few seconds to verify the charger.
                    delay(5000L)
                    updateSensors(context, serverManager, sensorDao, intent)
                }
            }
        }
    }

    private fun isSensorEnabled(id: String): Boolean {
        return sensorDao.get(id).any { it.enabled }
    }

    suspend fun updateSensors(
        context: Context,
        serverManager: ServerManager,
        sensorDao: SensorDao,
        intent: Intent?
    ) {
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        if (!serverManager.isRegistered()) {
            Log.w(tag, "Device not registered, skipping sensor update/registration")
            return
        }

        val serverHAversion = mutableMapOf<Int, String>()
        val serverSupportsDisabled = mutableMapOf<Int, Boolean>()
        val serverSensorStatus = mutableMapOf<Int, Map<String, Boolean>?>()
        serverManager.defaultServers.map { server ->
            ioScope.async {
                withTimeoutOrNull(10_000) {
                    server._version?.let { serverHAversion[server.id] = it } // Cached
                    serverHAversion[server.id] =
                        serverManager.integrationRepository(server.id).getHomeAssistantVersion()
                    serverSupportsDisabled[server.id] =
                        serverManager.integrationRepository(server.id).isHomeAssistantVersionAtLeast(2022, 6, 0)
                    serverSensorStatus[server.id] = if (serverSupportsDisabled[server.id] == true) {
                        try {
                            val config = serverManager.integrationRepository(server.id).getConfig().entities
                            config
                                ?.filter { it.value["disabled"] != null }
                                ?.mapValues { !(it.value["disabled"] as Boolean) } // Map to sensor id -> enabled
                        } catch (e: Exception) {
                            Log.e(tag, "Error while getting core config to sync sensor status", e)
                            null
                        }
                    } else null
                }
            }
        }.awaitAll()

        managers.forEach { manager ->
            // Since we don't have this manager injected it doesn't fulfil its injects, manually
            // inject for now I guess?
            if (manager is LocationSensorManagerBase)
                manager.serverManager = serverManager

            val hasSensor = manager.hasSensor(context)
            if (hasSensor) {
                try {
                    manager.requestSensorUpdate(context, intent)
                } catch (e: Exception) {
                    Log.e(tag, "Issue requesting updates for ${context.getString(manager.name)}", e)
                }
            }
            manager.getAvailableSensors(context).forEach sensorForEach@{ basicSensor ->
                val fullSensors = sensorDao.getFull(basicSensor.id).toSensorsWithAttributes()
                if (fullSensors.isEmpty()) return@sensorForEach
                val canBeRegistered = hasSensor &&
                    basicSensor.type.isNotBlank() &&
                    basicSensor.statelessIcon.isNotBlank()

                // Register sensor and/or update the sensor enabled state. Priority is:
                // 1. There is a new sensor or change in enabled state according to the app
                // 2. There is a change in enabled state according to core (user changed in frontend)
                // 3. There is no change in enabled state, but app/core version has changed
                // Because sensor enabled state is kept in sync across all servers, if one of the
                // first 2 scenarios applies on one server, it will be applied to all servers.

                val coreEnabledDifference: (SensorWithAttributes) -> Boolean = { (sensor, _) ->
                    serverSupportsDisabled[sensor.serverId] == true &&
                        serverSensorStatus[sensor.serverId]?.get(sensor.id) != null &&
                        serverSensorStatus[sensor.serverId]!![sensor.id] != sensor.registered
                }
                val versionDifference: (SensorWithAttributes) -> Boolean = { (sensor, _) ->
                    (sensor.enabled || serverSupportsDisabled[sensor.serverId] == true) &&
                        (currentAppVersion != sensor.appRegistration || serverHAversion[sensor.serverId] != sensor.coreRegistration)
                }
                if (
                    canBeRegistered &&
                    fullSensors.any { (sensor, _) ->
                        (sensor.registered == null && (sensor.enabled || serverSupportsDisabled[sensor.serverId] == true)) ||
                            (sensor.enabled != sensor.registered && serverSupportsDisabled[sensor.serverId] == true) ||
                            (sensor.registered != null && serverSensorStatus[sensor.serverId] != null && serverSensorStatus[sensor.serverId]!![sensor.id] == null)
                    }
                ) {
                    // 1. (Re-)register sensors with core when they can be registered and:
                    // - sensor isn't registered, but is enabled or on core >=2022.6
                    // - sensor enabled has changed from registered enabled state on core >=2022.6
                    // - sensor is registered according to database, but core >=2022.6 doesn't know about it
                    fullSensors.forEach { fullSensor ->
                        val sensor = fullSensor.sensor
                        if (serverSupportsDisabled[sensor.serverId] == true || (sensor.registered == null && sensor.enabled)) {
                            try {
                                registerSensor(context, serverManager, fullSensor, basicSensor)
                                sensor.registered = sensor.enabled
                                sensor.coreRegistration = serverHAversion[sensor.serverId]
                                sensor.appRegistration = currentAppVersion
                                sensorDao.update(sensor)
                            } catch (e: Exception) {
                                Log.e(tag, "Issue registering sensor ${basicSensor.id}", e)
                            }
                        }
                    }
                } else if (canBeRegistered && fullSensors.any(coreEnabledDifference)) {
                    // 2. Try updating the sensor enabled state to match core state when it's different from
                    // the app, if the sensor can be registered and on core >= 2022.6
                    val difference = fullSensors.first(coreEnabledDifference).sensor
                    val sensorCoreEnabled =
                        serverSensorStatus[difference.serverId]?.get(difference.id) == true
                    Log.d(tag, "Sync: ${if (sensorCoreEnabled) "enabling" else "disabling"} ${difference.id}")

                    fullSensors.forEach { fullSensor ->
                        try {
                            val sensor = fullSensor.sensor
                            var appliedDifference = true
                            if (sensorCoreEnabled) { // App disabled, should enable
                                if (manager.checkPermission(context.applicationContext, basicSensor.id)) {
                                    sensor.enabled = true
                                    sensor.registered = true
                                } else {
                                    // Can't enable due to missing permission(s), 'override' core and notify user
                                    if (difference.serverId == sensor.serverId && serverSupportsDisabled[sensor.serverId] == true) {
                                        registerSensor(context, serverManager, fullSensor, basicSensor)
                                        appliedDifference = false
                                    }

                                    context.getSystemService<NotificationManager>()?.let { notificationManager ->
                                        createNotificationChannel(context)
                                        val notificationId = "$sensorCoreSyncChannel-${basicSensor.id}".hashCode()
                                        val notificationIntent = getSensorSettingsIntent(context, basicSensor.id, manager.id(), notificationId)
                                        val notification = NotificationCompat.Builder(context, sensorCoreSyncChannel)
                                            .setSmallIcon(R.drawable.ic_stat_ic_notification)
                                            .setContentTitle(context.getString(basicSensor.name))
                                            .setContentText(context.getString(R.string.sensor_worker_sync_missing_permissions))
                                            .setContentIntent(notificationIntent)
                                            .setAutoCancel(true)
                                            .build()
                                        notificationManager.notify(notificationId, notification)
                                    }
                                }
                            } else { // App enabled, should disable
                                sensor.enabled = false
                                sensor.registered = false
                            }

                            sensor.coreRegistration = serverHAversion[sensor.serverId]
                            sensor.appRegistration = currentAppVersion
                            if (appliedDifference && difference.serverId != sensor.serverId) {
                                // We're applying enabled from a different server, send it to this one as well
                                registerSensor(context, serverManager, fullSensor, basicSensor)
                            }
                            sensorDao.update(sensor)
                        } catch (e: Exception) {
                            Log.e(tag, "Issue enabling/disabling sensor ${basicSensor.id}", e)
                        }
                    }
                } else if (canBeRegistered && fullSensors.any(versionDifference)) {
                    // 3. Re-register sensors with core when they can be registered and are enabled or on
                    // core >= 2022.6, and app or core version change is detected
                    fullSensors.filter(versionDifference).forEach { fullSensor ->
                        try {
                            registerSensor(context, serverManager, fullSensor, basicSensor)
                            val sensor = fullSensor.sensor
                            sensor.registered = sensor.enabled
                            sensor.coreRegistration = serverHAversion[sensor.serverId]
                            sensor.appRegistration = currentAppVersion
                            sensorDao.update(sensor)
                        } catch (e: Exception) {
                            Log.e(tag, "Issue re-registering sensor ${basicSensor.id}", e)
                        }
                    }
                }

                fullSensors.forEach { fullSensor ->
                    val sensor = fullSensor.sensor
                    if (canBeRegistered && sensor.enabled && sensor.registered != null && (sensor.state != sensor.lastSentState || sensor.icon != sensor.lastSentIcon)) {
                        enabledRegistrations.add(fullSensor.toSensorRegistration(basicSensor))
                    }
                }
            }
        }

        if (enabledRegistrations.isNotEmpty()) {
            val jobs = enabledRegistrations.groupBy { it.serverId }.map { (serverId, registrations) ->
                ioScope.async {
                    val success = try {
                        val success = serverManager.integrationRepository(serverId).updateSensors(registrations.toTypedArray())
                        registrations.forEach {
                            sensorDao.updateLastSentStateAndIcon(it.uniqueId, it.serverId, it.state.toString(), it.icon)
                        }
                        success
                    } catch (e: Exception) {
                        // Don't trigger re-registration when the server is down or job was cancelled
                        val exceptionOk = e is IntegrationException &&
                            (e.cause is IOException || e.cause is CancellationException)
                        if (exceptionOk) Log.w(tag, "Exception while updating sensors: ${e::class.java.simpleName}: ${e.cause?.let { it::class.java.name } }")
                        else Log.e(tag, "Exception while updating sensors.", e)
                        exceptionOk
                    }

                    // We failed to update a sensor, we should re register next time
                    if (!success) {
                        registrations.forEach {
                            val sensor = sensorDao.get(it.uniqueId, it.serverId)
                            if (sensor != null) {
                                sensor.registered = null
                                sensor.lastSentState = null
                                sensor.lastSentIcon = null
                                sensorDao.update(sensor)
                            }
                        }
                    }
                }
            }
            try {
                jobs.awaitAll()
            } catch (e: Exception) {
                Log.e(tag, "Exception while awaiting sensor updates.", e)
            }
        } else Log.d(tag, "Nothing to update")
    }

    private suspend fun registerSensor(
        context: Context,
        serverManager: ServerManager,
        fullSensor: SensorWithAttributes,
        basicSensor: SensorManager.BasicSensor
    ) {
        val reg = fullSensor.toSensorRegistration(basicSensor)
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("en"))
        reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

        serverManager.integrationRepository(fullSensor.sensor.serverId).registerSensor(reg)
    }

    private suspend fun updateSensor(
        context: Context,
        sensorId: String
    ) {
        val sensorManager = managers.firstOrNull {
            it.getAvailableSensors(context).any { s -> s.id == sensorId }
        } ?: return
        try {
            sensorManager.requestSensorUpdate(context)
        } catch (e: Exception) {
            Log.e(tag, "Issue requesting updates for ${context.getString(sensorManager.name)}", e)
        }
        val basicSensor = sensorManager.getAvailableSensors(context).firstOrNull { it.id == sensorId } ?: return
        val fullSensors = sensorDao.getFull(sensorId).toSensorsWithAttributes()
        fullSensors.filter {
            it.sensor.enabled && it.sensor.registered == true &&
                (it.sensor.state != it.sensor.lastSentState || it.sensor.icon != it.sensor.lastSentIcon)
        }.forEach { fullSensor ->
            ioScope.launch {
                try {
                    serverManager.integrationRepository(fullSensor.sensor.serverId).updateSensors(arrayOf(fullSensor.toSensorRegistration(basicSensor)))
                    sensorDao.updateLastSentStateAndIcon(
                        basicSensor.id,
                        fullSensor.sensor.serverId,
                        fullSensor.sensor.state,
                        fullSensor.sensor.icon
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Exception while updating individual sensor.", e)
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService<NotificationManager>() ?: return
            var notificationChannel =
                notificationManager.getNotificationChannel(sensorCoreSyncChannel)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    sensorCoreSyncChannel, sensorCoreSyncChannel, NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
