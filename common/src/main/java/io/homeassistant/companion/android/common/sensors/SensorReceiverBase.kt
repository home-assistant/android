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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.util.sensorCoreSyncChannel
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

abstract class SensorReceiverBase : BroadcastReceiver() {
    companion object {
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
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var sensorDao: SensorDao

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    )

    protected abstract val skippableActions: Map<String, String>

    protected abstract fun getSensorSettingsIntent(context: Context, id: String): Intent?

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
                    ioScope.launch {
                        try {
                            integrationUseCase.fireEvent(
                                "android.intent_received",
                                eventData as Map<String, Any>
                            )
                            Log.d(tag, "Event successfully sent to Home Assistant")
                        } catch (e: Exception) {
                            Log.e(
                                tag,
                                "Unable to send event data to Home Assistant",
                                e
                            )
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
            updateSensors(context, integrationUseCase, sensorDao, intent)
            if (chargingActions.contains(intent.action)) {
                // Add a 5 second delay to perform another update so charging state updates completely.
                // This is necessary as the system needs a few seconds to verify the charger.
                delay(5000L)
                updateSensors(context, integrationUseCase, sensorDao, intent)
            }
        }
    }

    private fun isSensorEnabled(id: String): Boolean {
        return sensorDao.get(id)?.enabled == true
    }

    suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationRepository,
        sensorDao: SensorDao,
        intent: Intent?
    ) {
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        val checkDeviceRegistration = integrationUseCase.getRegistration()
        if (checkDeviceRegistration.appVersion == null) {
            Log.w(tag, "Device not registered, skipping sensor update/registration")
            return
        }

        val currentHAversion = integrationUseCase.getHomeAssistantVersion()
        val supportsDisabledSensors = integrationUseCase.isHomeAssistantVersionAtLeast(2022, 6, 0)
        val coreSensorStatus: Map<String, Boolean>? = if (supportsDisabledSensors) {
            try {
                val config = integrationUseCase.getConfig().entities
                config
                    ?.filter { it.value["disabled"] != null }
                    ?.mapValues { !(it.value["disabled"] as Boolean) } // Map to sensor id -> enabled
            } catch (e: Exception) {
                Log.e(tag, "Error while getting core config to sync sensor status", e)
                null
            }
        } else {
            null
        }

        managers.forEach { manager ->

            // Since we don't have this manager injected it doesn't fulfil its injects, manually
            // inject for now I guess?
            if (manager is LocationSensorManagerBase)
                manager.integrationUseCase = integrationUseCase

            val hasSensor = manager.hasSensor(context)
            if (hasSensor) {
                try {
                    manager.requestSensorUpdate(context, intent)
                } catch (e: Exception) {
                    Log.e(tag, "Issue requesting updates for ${context.getString(manager.name)}", e)
                }
            }
            manager.getAvailableSensors(context).forEach sensorForEach@{ basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id)
                val sensor = fullSensor?.sensor ?: return@sensorForEach
                val sensorCoreEnabled = coreSensorStatus?.get(basicSensor.id)
                val sensorCoreRegistration = sensor.coreRegistration
                val sensorAppRegistration = sensor.appRegistration

                val canBeRegistered =
                    hasSensor &&
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
                        registerSensor(context, integrationUseCase, fullSensor, basicSensor)
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
                    // the app, if the sensor can be registered and on core >= 2022.6
                    try {
                        if (sensorCoreEnabled) { // App disabled, should enable
                            if (manager.checkPermission(context.applicationContext, basicSensor.id)) {
                                sensor.enabled = true
                                sensor.registered = true
                            } else {
                                // Can't enable due to missing permission(s), 'override' core and notify user
                                registerSensor(context, integrationUseCase, fullSensor, basicSensor)

                                context.getSystemService<NotificationManager>()?.let { notificationManager ->
                                    createNotificationChannel(context)
                                    val notificationId = "$sensorCoreSyncChannel-${basicSensor.id}".hashCode()
                                    val notificationIntent = getSensorSettingsIntent(context, basicSensor.id)?.let {
                                        PendingIntent.getActivity(context, notificationId, it, PendingIntent.FLAG_IMMUTABLE)
                                    }
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

                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue enabling/disabling sensor ${basicSensor.id}", e)
                    }
                } else if (
                    canBeRegistered &&
                    (sensor.enabled || supportsDisabledSensors) &&
                    (currentAppVersion != sensorAppRegistration || currentHAversion != sensorCoreRegistration)
                ) {
                    // 3. Re-register sensors with core when they can be registered and are enabled or on
                    // core >= 2022.6, and app or core version change is detected
                    try {
                        registerSensor(context, integrationUseCase, fullSensor, basicSensor)
                        sensor.registered = sensor.enabled
                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue re-registering sensor ${basicSensor.id}", e)
                    }
                }
                if (canBeRegistered && sensor.enabled && sensor.registered != null && (sensor.state != sensor.lastSentState || sensor.icon != sensor.lastSentIcon)) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration(basicSensor))
                }
            }
        }

        if (enabledRegistrations.isNotEmpty()) {
            var success = false
            try {
                success = integrationUseCase.updateSensors(enabledRegistrations.toTypedArray())
                enabledRegistrations.forEach {
                    sensorDao.updateLastSentStateAndIcon(it.uniqueId, it.state.toString(), it.icon)
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception while updating sensors.", e)
            }

            // We failed to update a sensor, we should re register next time
            if (!success) {
                enabledRegistrations.forEach {
                    val sensor = sensorDao.get(it.uniqueId)
                    if (sensor != null) {
                        sensor.registered = null
                        sensor.lastSentState = null
                        sensor.lastSentIcon = null
                        sensorDao.update(sensor)
                    }
                }
            }
        } else Log.d(tag, "Nothing to update")
    }

    private suspend fun registerSensor(
        context: Context,
        integrationUseCase: IntegrationRepository,
        fullSensor: SensorWithAttributes,
        basicSensor: SensorManager.BasicSensor
    ) {
        val reg = fullSensor.toSensorRegistration(basicSensor)
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("en"))
        reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

        integrationUseCase.registerSensor(reg)
    }

    suspend fun updateSensor(
        context: Context,
        integrationUseCase: IntegrationRepository,
        fullSensor: SensorWithAttributes?,
        sensorManager: SensorManager?,
        basicSensor: SensorManager.BasicSensor,
        sensorDao: SensorDao
    ) {
        sensorManager?.requestSensorUpdate(context)
        if (
            fullSensor != null && fullSensor.sensor.enabled &&
            fullSensor.sensor.registered == true &&
            (
                fullSensor.sensor.state != fullSensor.sensor.lastSentState ||
                    fullSensor.sensor.icon != fullSensor.sensor.lastSentIcon
                )
        ) {
            integrationUseCase.updateSensors(arrayOf(fullSensor.toSensorRegistration(basicSensor)))
            sensorDao.updateLastSentStateAndIcon(
                basicSensor.id,
                fullSensor.sensor.state,
                fullSensor.sensor.icon
            )
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
