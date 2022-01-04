package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

abstract class SensorReceiverBase : BroadcastReceiver() {
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

        managers.forEach { manager ->

            // Since we don't have this manager injected it doesn't fulfil its injects, manually
            // inject for now I guess?
            if (manager is LocationSensorManagerBase)
                manager.integrationUseCase = integrationUseCase

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

                // Always register enabled sensors in case of available entity updates
                // when app or core version change is detected every 4 hours
                if (sensor?.enabled == true && sensor.type.isNotBlank() && sensor.icon.isNotBlank() &&
                    (currentAppVersion != sensorAppRegistration || currentHAversion != sensorCoreRegistration || !sensor.registered)
                ) {
                    val reg = fullSensor.toSensorRegistration()
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("en"))
                    reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

                    try {
                        integrationUseCase.registerSensor(reg)
                        sensor.registered = true
                        sensor.coreRegistration = currentHAversion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(tag, "Issue registering sensor: ${reg.uniqueId}", e)
                    }
                }
                if (sensor?.enabled == true && sensor.registered && sensor.state != sensor.lastSentState) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration())
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
                        sensor.registered = false
                        sensor.lastSentState = ""
                        sensorDao.update(sensor)
                    }
                }
            }
        } else Log.d(tag, "Nothing to update")
    }
}
