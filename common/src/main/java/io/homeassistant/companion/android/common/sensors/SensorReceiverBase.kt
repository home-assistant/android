package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.*
import javax.inject.Inject

abstract class SensorReceiverBase : BroadcastReceiver() {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    protected abstract val tag: String

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
            updateSensors(context, integrationUseCase)
            if (chargingActions.contains(intent.action)) {
                // Add a 5 second delay to perform another update so charging state updates completely.
                // This is necessary as the system needs a few seconds to verify the charger.
                delay(5000L)
                updateSensors(context, integrationUseCase)
            }
        }
    }

    private fun isSensorEnabled(context: Context, id: String): Boolean {
        return AppDatabase.getInstance(context).sensorDao().get(id)?.enabled == true
    }

    abstract suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationRepository
    )
}