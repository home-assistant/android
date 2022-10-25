package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.R)
class HealthServicesSensorManager : SensorManager {
    companion object {

        private const val TAG = "HealthServices"
        private val userActivityState = SensorManager.BasicSensor(
            "activity_state",
            "sensor",
            commonR.string.sensor_name_activity_state,
            commonR.string.sensor_description_activity_state,
            "mdi:account",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    private lateinit var latestContext: Context
    private var healthClient: HealthServicesClient? = null
    private var passiveMonitoringClient: PassiveMonitoringClient? = null
    private var passiveListenerConfig: PassiveListenerConfig? = null
    private var callBackRegistered = false

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/#sensors"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_health_services

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(userActivityState)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    override fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateUserActivityState()
    }

    private fun updateUserActivityState() {
        if (!isEnabled(latestContext, userActivityState.id)) {
            passiveMonitoringClient?.clearPassiveListenerCallbackAsync()
            callBackRegistered = false
            return
        }

        if (healthClient == null) healthClient = HealthServices.getClient(latestContext)
        if (passiveMonitoringClient == null) passiveMonitoringClient = healthClient?.passiveMonitoringClient
        passiveListenerConfig = PassiveListenerConfig.builder()
            .setShouldUserActivityInfoBeRequested(isEnabled(latestContext, userActivityState.id))
            .build()

        val passiveListenerCallback: PassiveListenerCallback = object : PassiveListenerCallback {
            override fun onUserActivityInfoReceived(info: UserActivityInfo) {
                Log.d(TAG, "User activity state: ${info.userActivityState.name}")
                onSensorUpdated(
                    latestContext,
                    userActivityState,
                    when (info.userActivityState) {
                        UserActivityState.USER_ACTIVITY_ASLEEP -> "asleep"
                        UserActivityState.USER_ACTIVITY_PASSIVE -> "passive"
                        UserActivityState.USER_ACTIVITY_EXERCISE -> "exercise"
                        else -> "unknown"
                    },
                    when (info.userActivityState) {
                        UserActivityState.USER_ACTIVITY_EXERCISE -> "mdi:run"
                        UserActivityState.USER_ACTIVITY_ASLEEP -> "mdi:sleep"
                        UserActivityState.USER_ACTIVITY_PASSIVE -> "mdi:human-handsdown"
                        else -> userActivityState.statelessIcon
                    },
                    mapOf(
                        "time" to info.stateChangeTime,
                        "exercise_type" to info.exerciseInfo?.exerciseType?.name
                    )
                )

                SensorWorker.start(latestContext)
            }

            override fun onPermissionLost() {
                val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
                runBlocking {
                    sensorDao.setSensorsEnabled(listOf(userActivityState.id), false)
                }
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.e(TAG, "onRegistrationFailed: ", throwable)
                callBackRegistered = false
            }
        }

        if (!callBackRegistered) {
            passiveMonitoringClient?.setPassiveListenerCallback(
                passiveListenerConfig!!,
                passiveListenerCallback
            )
        }
        callBackRegistered = true
    }
}
