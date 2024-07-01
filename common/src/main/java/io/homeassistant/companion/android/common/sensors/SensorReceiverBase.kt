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
import io.homeassistant.companion.android.common.util.CHANNEL_SENSOR_SYNC
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class SensorReceiverBase : BroadcastReceiver() {
    companion object {
        const val ACTION_UPDATE_SENSOR = "io.homeassistant.companion.android.UPDATE_SENSOR"
        const val ACTION_UPDATE_SENSORS = "io.homeassistant.companion.android.UPDATE_SENSORS"
        const val ACTION_STOP_BEACON_SCANNING = "io.homeassistant.companion.android.STOP_BEACON_SCANNING"
        const val ACTION_STOP_BEACON_TRANSMITTING = "io.homeassistant.companion.android.STOP_BEACON_TRANSMITTING"
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

    protected abstract val skippableActions: Map<String, List<String>>

    protected abstract fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int
    ): PendingIntent?

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "Received intent: ${intent.action}")
        skippableActions[intent.action]?.let { sensors ->
            val noSensorsEnabled = sensors.none {
                isSensorEnabled(it)
            }
            if (noSensorsEnabled) {
                Log.d(
                    tag,
                    String.format(
                        "Sensor(s) %s corresponding to received event %s are disabled, skipping sensors update",
                        sensors.toString(),
                        intent.action
                    )
                )
                return
            }
        }

        if (intent.action == ACTION_STOP_BEACON_SCANNING) {
            BluetoothSensorManager.enableDisableBeaconMonitor(context, false)
            return
        }

        if (intent.action == ACTION_STOP_BEACON_TRANSMITTING) {
            BluetoothSensorManager.enableDisableBLETransmitter(context, false)
            return
        }

        @Suppress("DEPRECATION")
        if (isSensorEnabled(LastUpdateManager.lastUpdate.id)) {
            LastUpdateManager().sendLastUpdate(context, intent.action)
            val allSettings = sensorDao.getSettings(LastUpdateManager.lastUpdate.id)
            for (setting in allSettings) {
                if (setting.value != "" && intent.action == setting.value) {
                    val eventData = intent.extras?.keySet()
                        ?.associate { it.toString() to intent.extras?.get(it).toString() }
                        ?.plus("intent" to intent.action.toString())
                        ?: mapOf("intent" to intent.action.toString())
                    Log.d(tag, "Event data: $eventData")
                    sensorDao.get(LastUpdateManager.lastUpdate.id).forEach { sensor ->
                        if (!sensor.enabled) return@forEach
                        ioScope.launch {
                            try {
                                serverManager.integrationRepository(sensor.serverId).fireEvent(
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
        if (!serverManager.isRegistered()) {
            Log.w(tag, "Device not registered, skipping sensor update/registration")
            return
        }

        managers.forEach { manager ->
            val hasSensor = manager.hasSensor(context)
            if (hasSensor) {
                try {
                    manager.requestSensorUpdate(context, intent)
                } catch (e: Exception) {
                    Log.e(tag, "Issue requesting updates for ${context.getString(manager.name)}", e)
                }
            }
        }

        try {
            serverManager.defaultServers.map { server ->
                ioScope.async { syncSensorsWithServer(context, serverManager, server, sensorDao) }
            }.awaitAll()
            Log.i(tag, "Sensor updates and sync completed")
        } catch (e: Exception) {
            Log.e(tag, "Exception while awaiting sensor updates.", e)
        }
    }

    private suspend fun syncSensorsWithServer(
        context: Context,
        serverManager: ServerManager,
        server: Server,
        sensorDao: SensorDao
    ): Boolean {
        val currentHAversion = serverManager.integrationRepository(server.id).getHomeAssistantVersion()
        val supportsDisabledSensors = serverManager.integrationRepository(server.id).isHomeAssistantVersionAtLeast(2022, 6, 0)
        val serverIsTrusted = serverManager.integrationRepository(server.id).isTrusted()
        val coreSensorStatus: Map<String, Boolean>? =
            if (supportsDisabledSensors && (serverIsTrusted || (sensorDao.getEnabledCount() ?: 0) > 0)) {
                try {
                    val config = serverManager.integrationRepository(server.id).getConfig().entities
                    config
                        ?.filter { it.value["disabled"] != null }
                        ?.mapValues { !(it.value["disabled"] as Boolean) } // Map to sensor id -> enabled
                } catch (e: Exception) {
                    Log.e(tag, "Error while getting core config to sync sensor status", e)
                    null
                }
            } else {
                // Cannot sync disabled, or all sensors disabled and server changes aren't trusted
                null
            }

        var serverIsReachable = true
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        managers.forEach { manager ->
            // Each manager was already asked to update in updateSensors
            val hasSensor = manager.hasSensor(context)

            manager.getAvailableSensors(context).forEach sensorForEach@{ basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id, server.id).toSensorWithAttributes()
                val sensor = fullSensor?.sensor ?: return@sensorForEach
                val sensorCoreEnabled = coreSensorStatus?.get(basicSensor.id)
                val canBeRegistered = hasSensor &&
                    basicSensor.type.isNotBlank() &&
                    basicSensor.statelessIcon.isNotBlank()

                // Register sensor and/or update the sensor enabled state. Priority is:
                // 1. There is a new sensor or change in enabled state according to the app
                // 2. There is a change in enabled state according to core (user changed in frontend)
                // 3. There is no change in enabled state, but app/core version has changed
                if (
                    canBeRegistered &&
                    (
                        (sensor.registered == null && (sensor.enabled || supportsDisabledSensors)) ||
                            (sensor.enabled != sensor.registered && supportsDisabledSensors) ||
                            (sensor.registered != null && coreSensorStatus != null && sensorCoreEnabled == null)
                        )
                ) {
                    // 1. (Re-)register sensors with core when they can be registered and:
                    // - sensor isn't registered, but is enabled or on core >=2022.6
                    // - sensor enabled has changed from registered enabled state on core >=2022.6
                    // - sensor is registered according to database, but core >=2022.6 doesn't know about it
                    try {
                        registerSensor(context, serverManager, fullSensor, basicSensor)
                        sensor.registered = sensor.enabled
                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue registering sensor ${basicSensor.id}", e)
                    }
                } else if (
                    canBeRegistered &&
                    supportsDisabledSensors &&
                    sensorCoreEnabled != null &&
                    sensorCoreEnabled != sensor.registered
                ) {
                    // 2. Try updating the sensor enabled state to match core state when it's different from
                    // the app, if the sensor can be registered, on core >= 2022.6 and server trusted.
                    // If the server isn't trusted, update registered state to match app.
                    try {
                        if (!serverIsTrusted) { // Core changed, but app doesn't trust server so 'override'
                            registerSensor(context, serverManager, fullSensor, basicSensor)
                        } else if (sensorCoreEnabled) { // App disabled, should enable
                            if (manager.checkPermission(context.applicationContext, basicSensor.id)) {
                                sensor.enabled = true
                                sensor.registered = true
                            } else {
                                // Can't enable due to missing permission(s), 'override' core and notify user
                                registerSensor(context, serverManager, fullSensor, basicSensor)

                                context.getSystemService<NotificationManager>()?.let { notificationManager ->
                                    createNotificationChannel(context)
                                    val notificationId = "$CHANNEL_SENSOR_SYNC-${basicSensor.id}".hashCode()
                                    val notificationIntent = getSensorSettingsIntent(context, basicSensor.id, manager.id(), notificationId)
                                    val notification = NotificationCompat.Builder(context, CHANNEL_SENSOR_SYNC)
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

                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue enabling/disabling sensor ${basicSensor.id}", e)
                    }
                } else if (
                    canBeRegistered &&
                    serverIsReachable &&
                    (sensor.enabled || supportsDisabledSensors) &&
                    (currentAppVersion != sensor.appRegistration || currentHAversion != sensor.coreRegistration)
                ) {
                    // 3. Re-register sensors with core when they can be registered and are enabled or on
                    // core >= 2022.6, and app or core version change is detected
                    try {
                        registerSensor(context, serverManager, fullSensor, basicSensor)
                        sensor.registered = sensor.enabled
                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue re-registering sensor ${basicSensor.id}", e)
                        if (e is IntegrationException && (e.cause is ConnectException || e.cause is SocketTimeoutException)) {
                            Log.w(tag, "Server can't be reached, skipping other registrations for sensors due to version change")
                            serverIsReachable = false
                        }
                    }
                }

                if (canBeRegistered && sensor.enabled && sensor.registered != null && (sensor.state != sensor.lastSentState || sensor.icon != sensor.lastSentIcon)) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration(basicSensor))
                }
            }
        }

        var success = true
        if (enabledRegistrations.isNotEmpty()) {
            success = try {
                val serverSuccess = serverManager.integrationRepository(server.id).updateSensors(enabledRegistrations.toTypedArray())
                enabledRegistrations.forEach {
                    sensorDao.updateLastSentStateAndIcon(it.uniqueId, it.serverId, it.state.toString(), it.icon)
                }
                serverSuccess
            } catch (e: Exception) {
                // Don't trigger re-registration when the server is down or job was cancelled
                val exceptionOk = e is IntegrationException &&
                    (e.cause is IOException || e.cause is CancellationException)
                if (exceptionOk) {
                    Log.w(tag, "Exception while updating sensors: ${e::class.java.simpleName}: ${e.cause?.let { it::class.java.name } }")
                } else {
                    Log.e(tag, "Exception while updating sensors.", e)
                }
                exceptionOk
            }

            // We failed to update a sensor, we should re register next time
            if (!success) {
                enabledRegistrations.forEach {
                    val sensor = sensorDao.get(it.uniqueId, it.serverId)
                    if (sensor != null) {
                        sensor.registered = null
                        sensor.lastSentState = null
                        sensor.lastSentIcon = null
                        sensorDao.update(sensor)
                    }
                }
            }
        } else {
            Log.d(tag, "Nothing to update for server ${server.id} (${server.friendlyName})")
        }
        return success
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
                notificationManager.getNotificationChannel(CHANNEL_SENSOR_SYNC)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    CHANNEL_SENSOR_SYNC,
                    CHANNEL_SENSOR_SYNC,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
