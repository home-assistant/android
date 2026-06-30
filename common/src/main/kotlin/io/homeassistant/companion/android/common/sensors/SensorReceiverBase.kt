package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.SettingsDao
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class SensorReceiverBase : BroadcastReceiver() {
    companion object {
        const val ACTION_UPDATE_SENSOR = "io.homeassistant.companion.android.UPDATE_SENSOR"
        const val ACTION_UPDATE_SENSORS = "io.homeassistant.companion.android.UPDATE_SENSORS"
        const val EXTRA_SENSOR_ID = "sensorId"

        suspend fun SettingsDao.shouldDoFastUpdates(context: Context): Boolean {
            val setting = get(0)
            return when (setting?.sensorUpdateFrequency) {
                SensorUpdateFrequencySetting.FAST_ALWAYS -> true
                SensorUpdateFrequencySetting.FAST_WHILE_CHARGING -> {
                    val batteryStatusIntent =
                        ContextCompat.registerReceiver(
                            context,
                            null,
                            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                    batteryStatusIntent?.let { BatterySensorManager.getIsCharging(it) } ?: false
                }

                else -> false
            }
        }
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var sensorRepository: SensorRepository

    @Inject
    lateinit var lastUpdateManager: LastUpdateManager

    @Inject
    lateinit var settingsDao: SettingsDao

    @Inject
    lateinit var sensorUpdater: SensorUpdater

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED,
    )

    protected abstract val skippableActions: Map<String, List<String>>

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Received intent: ${intent.action}")
        ioScope.launch {
            skippableActions[intent.action]?.let { sensors ->
                val noSensorsEnabled = sensors.none {
                    isSensorEnabled(it)
                }
                if (noSensorsEnabled) {
                    Timber.d(
                        String.format(
                            "Sensor(s) %s corresponding to received event %s are disabled, skipping sensors update",
                            sensors.toString(),
                            intent.action,
                        ),
                    )
                    return@launch
                }
            }

            @Suppress("DEPRECATION")
            if (isSensorEnabled(LastUpdateManager.lastUpdate.id)) {
                lastUpdateManager.sendLastUpdate(intent.action)
                val allSettings = sensorRepository.getSettings(LastUpdateManager.lastUpdate.id)
                for (setting in allSettings) {
                    if (setting.value != "" && intent.action == setting.value) {
                        val eventData = intent.extras?.keySet()
                            ?.associate { it.toString() to intent.extras?.get(it).toString() }
                            ?.plus("intent" to intent.action.toString())
                            ?: mapOf("intent" to intent.action.toString())
                        Timber.d("Event data: $eventData")
                        sensorRepository.get(LastUpdateManager.lastUpdate.id).forEach { sensor ->
                            if (!sensor.enabled) return@forEach
                            try {
                                serverManager.integrationRepository(sensor.serverId).fireEvent(
                                    "android.intent_received",
                                    eventData as Map<String, Any>,
                                )
                                Timber.d("Event successfully sent to Home Assistant")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Unable to send event data to Home Assistant")
                            }
                        }
                    }
                }
            }

            if (intent.action == Intent.ACTION_TIME_TICK && !settingsDao.shouldDoFastUpdates(context)) {
                Timber.i("Skipping faster update because not charging/different preference")
                return@launch
            }
            if (intent.action == ACTION_UPDATE_SENSOR) {
                val sensorId = intent.getStringExtra(EXTRA_SENSOR_ID)
                if (sensorId != null) {
                    sensorUpdater.updateSensor(sensorId)
                }
            } else {
                updateSensors(intent)
                if (chargingActions.contains(intent.action)) {
                    // Add a 5 second delay to perform another update so charging state updates completely.
                    // This is necessary as the system needs a few seconds to verify the charger.
                    delay(5.seconds)
                    updateSensors(intent)
                }
            }
        }
    }

    private suspend fun updateSensors(intent: Intent?) {
        sensorUpdater.updateSensors(intent = intent)
    }

    private suspend fun isSensorEnabled(id: String): Boolean {
        return sensorRepository.get(id).any { it.enabled }
    }
}
