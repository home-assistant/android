package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.database.AppDatabase
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

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    )

    protected abstract val skippableActions: Map<String, String>

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "Received intent: ${intent.action}")
        if (skippableActions.containsKey(intent.action)) {
            val sensor = skippableActions[intent.action]
            if (!isSensorEnabled(context, sensor!!)) {
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

        if (isSensorEnabled(context, LastUpdateManager.lastUpdate.id)) {
            LastUpdateManager().sendLastUpdate(context, intent.action)
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
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
            updateSensors(context, integrationUseCase, intent)
            if (chargingActions.contains(intent.action)) {
                // Add a 5 second delay to perform another update so charging state updates completely.
                // This is necessary as the system needs a few seconds to verify the charger.
                delay(5000L)
                updateSensors(context, integrationUseCase, intent)
            }
        }
    }

    protected fun isSensorEnabled(context: Context, id: String): Boolean {
        return AppDatabase.getInstance(context).sensorDao().get(id)?.enabled == true
    }

    suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationRepository,
        intent: Intent?
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        val checkDeviceRegistration = integrationUseCase.getRegistration()
        if (checkDeviceRegistration.appVersion == null) {
            Log.w(tag, "Device not registered, skipping sensor update/registration")
            return
        }

        val currentHAversion = integrationUseCase.getHomeAssistantVersion()
        val supportsDisabledSensors = integrationUseCase.isHomeAssistantVersionAtLeast(2022, 6, 0)

        managers.forEach { manager ->

            // Since we don't have this manager injected it doesn't fulfil its injects, manually
            // inject for now I guess?
            if (manager is LocationSensorManagerBase)
                manager.integrationUseCase = integrationUseCase

            val hasSensor = manager.hasSensor(context)
            try {
                manager.requestSensorUpdate(context, intent)
            } catch (e: Exception) {
                Log.e(tag, "Issue requesting updates for ${context.getString(manager.name)}", e)
            }
            manager.getAvailableSensors(context).forEach { basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id)
                val sensor = fullSensor?.sensor
                val sensorCoreRegistration = sensor?.coreRegistration
                val sensorAppRegistration = sensor?.appRegistration

                // The app should (re)register available sensors with core when possible (supported/type/icon) and:
                // - sensor isn't registered, but is enabled or on core >=2022.6
                // - sensor enabled state doesn't match registered enabled state on core >=2022.6
                // - sensor is enabled or on core >=2022.6, and app or core version change is detected
                if (
                    sensor != null &&
                    hasSensor &&
                    basicSensor.type.isNotBlank() &&
                    basicSensor.statelessIcon.isNotBlank() &&
                    (
                        (sensor.registered == null && (sensor.enabled || supportsDisabledSensors)) ||
                            (sensor.enabled != sensor.registered && supportsDisabledSensors) ||
                            (
                                (sensor.enabled || supportsDisabledSensors) &&
                                    (currentAppVersion != sensorAppRegistration || currentHAversion != sensorCoreRegistration)
                                )
                        )
                ) {
                    val reg = fullSensor.toSensorRegistration(basicSensor)
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("en"))
                    reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

                    try {
                        integrationUseCase.registerSensor(reg)
                        sensor.registered = sensor.enabled
                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue registering sensor: ${reg.uniqueId}", e)
                    }
                } else if (
                    supportsDisabledSensors &&
                    sensor != null &&
                    sensor.enabled != sensor.registered &&
                    (!hasSensor || basicSensor.type.isBlank())
                ) {
                    // Unsupported sensors or sensors without a type (= location sensors) in the database shouldn't/can't
                    // be registered but they will have a 'registered' state. Manually update when on core >=2022.6 by
                    // setting it to the enabled state to stop the app from continuing to do updates because of these sensors.
                    sensor.registered = sensor.enabled
                    sensorDao.update(sensor)
                }
                if (sensor?.enabled == true && sensor.registered != null && sensor.state != sensor.lastSentState) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration(basicSensor))
                }
            }
        }

        if (enabledRegistrations.isNotEmpty()) {
            var success = false
            try {
                success = integrationUseCase.updateSensors(enabledRegistrations.toTypedArray())
                enabledRegistrations.forEach {
                    sensorDao.updateLastSendState(it.uniqueId, it.state.toString())
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
                        sensor.lastSentState = ""
                        sensorDao.update(sensor)
                    }
                }
            }
        } else Log.d(tag, "Nothing to update")
    }
}
