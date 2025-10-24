package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.PassiveMonitoringCapabilities
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.database.AppDatabase
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class HealthServicesSensorManager : SensorManager {
    companion object {

        private var callbackLastUpdated = 0L
        private val userActivityState = SensorManager.BasicSensor(
            "activity_state",
            "sensor",
            commonR.string.sensor_name_activity_state,
            commonR.string.sensor_description_activity_state,
            "mdi:account",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        private val dailyFloors = SensorManager.BasicSensor(
            "daily_floors",
            "sensor",
            commonR.string.sensor_name_daily_floors,
            commonR.string.sensor_description_daily_floors,
            "mdi:stairs",
            unitOfMeasurement = "floors",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
        private val dailyDistance = SensorManager.BasicSensor(
            "daily_distance",
            "sensor",
            commonR.string.sensor_name_daily_distance,
            commonR.string.sensor_description_daily_distance,
            "mdi:map-marker-distance",
            "distance",
            unitOfMeasurement = "m",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
        private val dailyCalories = SensorManager.BasicSensor(
            "daily_calories",
            "sensor",
            commonR.string.sensor_name_daily_calories,
            commonR.string.sensor_description_daily_calories,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "cal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
        private val dailySteps = SensorManager.BasicSensor(
            "daily_steps",
            "sensor",
            commonR.string.sensor_name_daily_steps,
            commonR.string.sensor_description_daily_steps,
            "mdi:shoe-print",
            unitOfMeasurement = "steps",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
    }

    private lateinit var latestContext: Context
    private var healthClient: HealthServicesClient? = null
    private var passiveMonitoringClient: PassiveMonitoringClient? = null
    private var passiveMonitoringCapabilities: PassiveMonitoringCapabilities? = null
    private var passiveListenerConfig: PassiveListenerConfig? = null
    private var callBackRegistered = false
    private var dataTypesRegistered = emptySet<DataType<*, *>>()
    private var activityStateRegistered = false

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private val areHealthServicesSensorApisAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors#health-services"
    }

    override val name: Int
        get() = commonR.string.sensor_name_health_services

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        latestContext = context
        if (healthClient == null) {
            healthClient = HealthServices.getClient(latestContext)
        }
        if (passiveMonitoringClient == null) {
            passiveMonitoringClient = healthClient?.passiveMonitoringClient
        }
        if (passiveMonitoringCapabilities == null) {
            passiveMonitoringCapabilities = passiveMonitoringClient?.getCapabilitiesAsync()?.await()
            Timber.d("Supported capabilities: $passiveMonitoringCapabilities")
        }

        val supportedSensors = mutableListOf(userActivityState)

        if (passiveMonitoringCapabilities?.supportedDataTypesPassiveMonitoring?.contains(DataType.FLOORS_DAILY) ==
            true
        ) {
            supportedSensors += dailyFloors
        }
        if (passiveMonitoringCapabilities?.supportedDataTypesPassiveMonitoring?.contains(DataType.DISTANCE_DAILY) ==
            true
        ) {
            supportedSensors += dailyDistance
        }
        if (passiveMonitoringCapabilities?.supportedDataTypesPassiveMonitoring?.contains(DataType.CALORIES_DAILY) ==
            true
        ) {
            supportedSensors += dailyCalories
        }
        if (passiveMonitoringCapabilities?.supportedDataTypesPassiveMonitoring?.contains(DataType.STEPS_DAILY) ==
            true
        ) {
            supportedSensors += dailySteps
        }
        return supportedSensors
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return if (areHealthServicesSensorApisAvailable) {
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyArray()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return areHealthServicesSensorApisAvailable
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateHealthServices()
    }

    private suspend fun updateHealthServices() {
        val activityStateEnabled = isEnabled(latestContext, userActivityState)
        val dailyFloorEnabled = isEnabled(latestContext, dailyFloors)
        val dailyDistanceEnabled = isEnabled(latestContext, dailyDistance)
        val dailyCaloriesEnabled = isEnabled(latestContext, dailyCalories)
        val dailyStepsEnabled = isEnabled(latestContext, dailySteps)

        if (
            !activityStateEnabled &&
            !dailyFloorEnabled &&
            !dailyDistanceEnabled &&
            !dailyCaloriesEnabled &&
            !dailyStepsEnabled
        ) {
            clearHealthServicesCallBack()
            return
        }

        if (healthClient == null) healthClient = HealthServices.getClient(latestContext)
        if (passiveMonitoringClient == null) passiveMonitoringClient = healthClient?.passiveMonitoringClient

        val dataTypes = mutableSetOf<DataType<*, *>>()
        if (dailyFloorEnabled) {
            dataTypes += DataType.FLOORS_DAILY
        }
        if (dailyDistanceEnabled) {
            dataTypes += DataType.DISTANCE_DAILY
        }
        if (dailyCaloriesEnabled) {
            dataTypes += DataType.CALORIES_DAILY
        }
        if (dailyStepsEnabled) {
            dataTypes += DataType.STEPS_DAILY
        }

        passiveListenerConfig = PassiveListenerConfig.builder()
            .setShouldUserActivityInfoBeRequested(activityStateEnabled)
            .setDataTypes(dataTypes)
            .build()

        if (dataTypesRegistered != dataTypes ||
            activityStateRegistered != activityStateEnabled ||
            callbackLastUpdated + 1800000 < System.currentTimeMillis()
        ) {
            clearHealthServicesCallBack()
        }

        activityStateRegistered = activityStateEnabled
        dataTypesRegistered = dataTypes

        val passiveListenerCallback: PassiveListenerCallback = object : PassiveListenerCallback {
            override fun onUserActivityInfoReceived(info: UserActivityInfo) {
                Timber.d("User activity state: ${info.userActivityState.name}")
                ioScope.launch {
                    callbackLastUpdated = System.currentTimeMillis()
                    val forceUpdate = info.userActivityState == UserActivityState.USER_ACTIVITY_EXERCISE
                    onSensorUpdated(
                        latestContext,
                        userActivityState,
                        when (info.userActivityState) {
                            UserActivityState.USER_ACTIVITY_ASLEEP -> "asleep"
                            UserActivityState.USER_ACTIVITY_PASSIVE -> "passive"
                            UserActivityState.USER_ACTIVITY_EXERCISE -> "exercise"
                            else -> STATE_UNKNOWN
                        },
                        getActivityIcon(info),
                        mapOf(
                            "time" to info.stateChangeTime,
                            "exercise_type" to info.exerciseInfo?.exerciseType?.name,
                            "options" to listOf("asleep", "passive", "exercise"),
                        ),
                        forceUpdate = forceUpdate,
                    )
                    val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
                    val sensorData = sensorDao.get(userActivityState.id)

                    if (sensorData.any { it.state != it.lastSentState } || forceUpdate) {
                        SensorReceiver.updateAllSensors(latestContext)
                    }
                }
            }

            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                Timber.d("New data point received: ${dataPoints.dataTypes}")
                callbackLastUpdated = System.currentTimeMillis()
                val floorsDaily = dataPoints.getData(DataType.FLOORS_DAILY)
                val distanceDaily = dataPoints.getData(DataType.DISTANCE_DAILY)
                val caloriesDaily = dataPoints.getData(DataType.CALORIES_DAILY)
                val stepsDaily = dataPoints.getData(DataType.STEPS_DAILY)
                val bootInstant =
                    Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

                dataPoints.dataTypes.forEachIndexed { _, dataType ->

                    if (dataType is DeltaDataType<*, *>) {
                        val data = dataPoints.getData(dataType)
                        data.forEachIndexed { indexPoint, dataPoint ->
                            if (dataPoint is IntervalDataPoint) {
                                val endTime = dataPoint.getEndInstant(bootInstant)
                                Timber.d(
                                    "Data for ${dataType.name} index: $indexPoint with value: ${dataPoint.value} end time: ${endTime.toEpochMilli()}",
                                )
                            }
                        }
                    }
                }

                processDataPoint(floorsDaily, dailyFloors)
                processDataPoint(distanceDaily, dailyDistance)
                processDataPoint(caloriesDaily, dailyCalories)
                processDataPoint(stepsDaily, dailySteps)
            }

            override fun onPermissionLost() {
                val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
                runBlocking {
                    serverManager(latestContext).defaultServers.forEach {
                        sensorDao.setSensorsEnabled(listOf(userActivityState.id), it.id, false)
                    }
                }
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                Timber.e(throwable, "onRegistrationFailed")
                callBackRegistered = false
            }

            override fun onRegistered() {
                Timber.d(
                    "Health services callback successfully registered for the following data types: ${passiveListenerConfig!!.dataTypes} User Activity Info: ${passiveListenerConfig!!.shouldUserActivityInfoBeRequested} Health Events: ${passiveListenerConfig!!.healthEventTypes}",
                )
                callBackRegistered = true
            }
        }

        if (!callBackRegistered) {
            passiveMonitoringClient?.setPassiveListenerCallback(
                passiveListenerConfig!!,
                passiveListenerCallback,
            )
        }

        // Assume the callback is registered to avoid making multiple requests
        callBackRegistered = true
    }

    private fun clearHealthServicesCallBack() {
        passiveMonitoringClient?.clearPassiveListenerCallbackAsync()
        callBackRegistered = false
    }

    private fun getActivityIcon(info: UserActivityInfo): String {
        return when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_EXERCISE -> {
                when (info.exerciseInfo?.exerciseType) {
                    ExerciseType.ALPINE_SKIING, ExerciseType.SKIING -> "mdi:skiing"
                    ExerciseType.WEIGHTLIFTING,
                    ExerciseType.BARBELL_SHOULDER_PRESS,
                    ExerciseType.BENCH_PRESS,
                    -> "mdi:weight-lifter"
                    ExerciseType.BIKING, ExerciseType.BIKING_STATIONARY, ExerciseType.MOUNTAIN_BIKING -> "mdi:bike"
                    ExerciseType.SWIMMING_POOL, ExerciseType.SWIMMING_OPEN_WATER -> "mdi:swim"
                    ExerciseType.BASEBALL -> "mdi:baseball"
                    ExerciseType.BASKETBALL -> "mdi:basketball"
                    ExerciseType.FOOTBALL_AMERICAN -> "mdi:football"
                    ExerciseType.FOOTBALL_AUSTRALIAN -> "mdi:football-australian"
                    ExerciseType.SOCCER -> "mdi:soccer"
                    ExerciseType.SKATING, ExerciseType.INLINE_SKATING -> "mdi:skate"
                    ExerciseType.ROLLER_SKATING -> "mdi:roller-skate"
                    ExerciseType.SCUBA_DIVING -> "mdi:diving-scuba"
                    ExerciseType.SAILING -> "mdi:sail-boat"
                    ExerciseType.RUGBY -> "mdi:rugby"
                    ExerciseType.ROWING, ExerciseType.ROWING_MACHINE -> "mdi:rowing"
                    ExerciseType.RACQUETBALL -> "mdi:racquetball"
                    ExerciseType.HORSE_RIDING -> "mdi:horse-human"
                    ExerciseType.ICE_HOCKEY, ExerciseType.ROLLER_HOCKEY -> "mdi:hockey-sticks"
                    ExerciseType.GYMNASTICS -> "mdi:gymnastics"
                    ExerciseType.DANCING -> "mdi:dance-ballroom"
                    ExerciseType.CRICKET -> "mdi:cricket"
                    ExerciseType.JUMP_ROPE -> "mdi:jump-rope"
                    ExerciseType.JUMPING_JACK -> "mdi:human-handsdown"
                    ExerciseType.SNOWBOARDING -> "mdi:snowboard"
                    ExerciseType.MEDITATION -> "mdi:meditation"
                    ExerciseType.SURFING -> "mdi:surfing"
                    ExerciseType.TENNIS -> "mdi:tennis"
                    ExerciseType.TABLE_TENNIS -> "mdi:table-tennis"
                    ExerciseType.VOLLEYBALL -> "mdi:volleyball"
                    ExerciseType.HANDBALL -> "mdi:handball"
                    ExerciseType.YOGA -> "mdi:yoga"
                    ExerciseType.WATER_POLO -> "mdi:water-polo"
                    ExerciseType.STAIR_CLIMBING, ExerciseType.STAIR_CLIMBING_MACHINE -> "mdi:stairs"
                    ExerciseType.PARA_GLIDING -> "mdi:paragliding"
                    ExerciseType.GOLF -> "mdi:golf"
                    else -> "mdi:run"
                }
            }

            UserActivityState.USER_ACTIVITY_PASSIVE -> "mdi:human-handsdown"
            UserActivityState.USER_ACTIVITY_ASLEEP -> "mdi:sleep"
            else -> userActivityState.statelessIcon
        }
    }

    private fun processDataPoint(dataPoints: List<IntervalDataPoint<*>>, basicSensor: SensorManager.BasicSensor) {
        var latest = 0
        var lastIndex = 0
        val bootInstant =
            Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

        if (dataPoints.isNotEmpty()) {
            dataPoints.forEachIndexed { index, intervalDataPoint ->
                val endTime = intervalDataPoint.getEndInstant(bootInstant)
                Timber.d(
                    "${basicSensor.id} data index: $index with value: ${intervalDataPoint.value} end time: ${endTime.toEpochMilli()}",
                )
                if (endTime.toEpochMilli() > latest) {
                    latest = endTime.toEpochMilli().toInt()
                    lastIndex = index
                }
            }
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    basicSensor,
                    dataPoints[lastIndex].value,
                    basicSensor.statelessIcon,
                    mapOf(),
                )
            }
        }
    }
}
