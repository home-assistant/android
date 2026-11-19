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
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Account
import io.github.timoptr.mdiicons.generated.Baseball
import io.github.timoptr.mdiicons.generated.Basketball
import io.github.timoptr.mdiicons.generated.Bike
import io.github.timoptr.mdiicons.generated.Cricket
import io.github.timoptr.mdiicons.generated.DanceBallroom
import io.github.timoptr.mdiicons.generated.DivingScuba
import io.github.timoptr.mdiicons.generated.Fire
import io.github.timoptr.mdiicons.generated.Football
import io.github.timoptr.mdiicons.generated.FootballAustralian
import io.github.timoptr.mdiicons.generated.Golf
import io.github.timoptr.mdiicons.generated.Gymnastics
import io.github.timoptr.mdiicons.generated.Handball
import io.github.timoptr.mdiicons.generated.HockeySticks
import io.github.timoptr.mdiicons.generated.HorseHuman
import io.github.timoptr.mdiicons.generated.HumanHandsdown
import io.github.timoptr.mdiicons.generated.JumpRope
import io.github.timoptr.mdiicons.generated.MapMarkerDistance
import io.github.timoptr.mdiicons.generated.Meditation
import io.github.timoptr.mdiicons.generated.Paragliding
import io.github.timoptr.mdiicons.generated.Racquetball
import io.github.timoptr.mdiicons.generated.RollerSkate
import io.github.timoptr.mdiicons.generated.Rowing
import io.github.timoptr.mdiicons.generated.Rugby
import io.github.timoptr.mdiicons.generated.Run
import io.github.timoptr.mdiicons.generated.SailBoat
import io.github.timoptr.mdiicons.generated.ShoePrint
import io.github.timoptr.mdiicons.generated.Skate
import io.github.timoptr.mdiicons.generated.Ski
import io.github.timoptr.mdiicons.generated.Sleep
import io.github.timoptr.mdiicons.generated.Snowboard
import io.github.timoptr.mdiicons.generated.Soccer
import io.github.timoptr.mdiicons.generated.Stairs
import io.github.timoptr.mdiicons.generated.Surfing
import io.github.timoptr.mdiicons.generated.Swim
import io.github.timoptr.mdiicons.generated.TableTennis
import io.github.timoptr.mdiicons.generated.Tennis
import io.github.timoptr.mdiicons.generated.Volleyball
import io.github.timoptr.mdiicons.generated.WaterPolo
import io.github.timoptr.mdiicons.generated.WeightLifter
import io.github.timoptr.mdiicons.generated.Yoga
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@Singleton
class HealthServicesSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {

        private var callbackLastUpdated = 0L

        @ProvidesSensor
        internal val userActivityState = SensorManager.BasicSensor(
            "activity_state",
            "sensor",
            commonR.string.sensor_name_activity_state,
            commonR.string.sensor_description_activity_state,
            Mdi.Account,
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        internal val dailyFloors = SensorManager.BasicSensor(
            "daily_floors",
            "sensor",
            commonR.string.sensor_name_daily_floors,
            commonR.string.sensor_description_daily_floors,
            Mdi.Stairs,
            unitOfMeasurement = "floors",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )

        @ProvidesSensor
        internal val dailyDistance = SensorManager.BasicSensor(
            "daily_distance",
            "sensor",
            commonR.string.sensor_name_daily_distance,
            commonR.string.sensor_description_daily_distance,
            Mdi.MapMarkerDistance,
            "distance",
            unitOfMeasurement = "m",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )

        @ProvidesSensor
        internal val dailyCalories = SensorManager.BasicSensor(
            "daily_calories",
            "sensor",
            commonR.string.sensor_name_daily_calories,
            commonR.string.sensor_description_daily_calories,
            Mdi.Fire,
            "energy",
            unitOfMeasurement = "cal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )

        @ProvidesSensor
        internal val dailySteps = SensorManager.BasicSensor(
            "daily_steps",
            "sensor",
            commonR.string.sensor_name_daily_steps,
            commonR.string.sensor_description_daily_steps,
            Mdi.ShoePrint,
            unitOfMeasurement = "steps",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
    }

    private var healthClient: HealthServicesClient? = null
    private var passiveMonitoringClient: PassiveMonitoringClient? = null
    private var passiveMonitoringCapabilities: PassiveMonitoringCapabilities? = null
    private var passiveListenerConfig: PassiveListenerConfig? = null
    private var callBackRegistered = false
    private var dataTypesRegistered = emptySet<DataType<*, *>>()
    private var activityStateRegistered = false

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private val areHealthServicesSensorApisAvailable = SdkVersion.isAtLeast(Build.VERSION_CODES.R)

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors#health-services"
    }

    override val name: Int
        get() = commonR.string.sensor_name_health_services

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        if (healthClient == null) {
            healthClient = HealthServices.getClient(applicationContext)
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

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (areHealthServicesSensorApisAvailable) {
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyArray()
        }
    }

    override fun hasSensor(): Boolean {
        return areHealthServicesSensorApisAvailable
    }

    override suspend fun requestSensorUpdate() {
        updateHealthServices()
    }

    private suspend fun updateHealthServices() {
        val activityStateEnabled = isEnabled(userActivityState)
        val dailyFloorEnabled = isEnabled(dailyFloors)
        val dailyDistanceEnabled = isEnabled(dailyDistance)
        val dailyCaloriesEnabled = isEnabled(dailyCalories)
        val dailyStepsEnabled = isEnabled(dailySteps)

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

        if (healthClient == null) healthClient = HealthServices.getClient(applicationContext)
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
                    val sensorData = sensorRepository.get(userActivityState.id)

                    if (sensorData.any { it.state != it.lastSentState } || forceUpdate) {
                        SensorReceiver.updateAllSensors(applicationContext)
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
                val sensorRepository = sensorRepository
                runBlocking {
                    serverManager.servers().forEach {
                        sensorRepository.setSensorsEnabled(listOf(userActivityState.id), it.id, false)
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

    private fun getActivityIcon(info: UserActivityInfo): MdiIcon {
        return when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_EXERCISE -> {
                when (info.exerciseInfo?.exerciseType) {
                    ExerciseType.ALPINE_SKIING, ExerciseType.SKIING -> Mdi.Ski
                    ExerciseType.WEIGHTLIFTING,
                    ExerciseType.BARBELL_SHOULDER_PRESS,
                    ExerciseType.BENCH_PRESS,
                    -> Mdi.WeightLifter
                    ExerciseType.BIKING, ExerciseType.BIKING_STATIONARY, ExerciseType.MOUNTAIN_BIKING -> Mdi.Bike
                    ExerciseType.SWIMMING_POOL, ExerciseType.SWIMMING_OPEN_WATER -> Mdi.Swim
                    ExerciseType.BASEBALL -> Mdi.Baseball
                    ExerciseType.BASKETBALL -> Mdi.Basketball
                    ExerciseType.FOOTBALL_AMERICAN -> Mdi.Football
                    ExerciseType.FOOTBALL_AUSTRALIAN -> Mdi.FootballAustralian
                    ExerciseType.SOCCER -> Mdi.Soccer
                    ExerciseType.SKATING, ExerciseType.INLINE_SKATING -> Mdi.Skate
                    ExerciseType.ROLLER_SKATING -> Mdi.RollerSkate
                    ExerciseType.SCUBA_DIVING -> Mdi.DivingScuba
                    ExerciseType.SAILING -> Mdi.SailBoat
                    ExerciseType.RUGBY -> Mdi.Rugby
                    ExerciseType.ROWING, ExerciseType.ROWING_MACHINE -> Mdi.Rowing
                    ExerciseType.RACQUETBALL -> Mdi.Racquetball
                    ExerciseType.HORSE_RIDING -> Mdi.HorseHuman
                    ExerciseType.ICE_HOCKEY, ExerciseType.ROLLER_HOCKEY -> Mdi.HockeySticks
                    ExerciseType.GYMNASTICS -> Mdi.Gymnastics
                    ExerciseType.DANCING -> Mdi.DanceBallroom
                    ExerciseType.CRICKET -> Mdi.Cricket
                    ExerciseType.JUMP_ROPE -> Mdi.JumpRope
                    ExerciseType.JUMPING_JACK -> Mdi.HumanHandsdown
                    ExerciseType.SNOWBOARDING -> Mdi.Snowboard
                    ExerciseType.MEDITATION -> Mdi.Meditation
                    ExerciseType.SURFING -> Mdi.Surfing
                    ExerciseType.TENNIS -> Mdi.Tennis
                    ExerciseType.TABLE_TENNIS -> Mdi.TableTennis
                    ExerciseType.VOLLEYBALL -> Mdi.Volleyball
                    ExerciseType.HANDBALL -> Mdi.Handball
                    ExerciseType.YOGA -> Mdi.Yoga
                    ExerciseType.WATER_POLO -> Mdi.WaterPolo
                    ExerciseType.STAIR_CLIMBING, ExerciseType.STAIR_CLIMBING_MACHINE -> Mdi.Stairs
                    ExerciseType.PARA_GLIDING -> Mdi.Paragliding
                    ExerciseType.GOLF -> Mdi.Golf
                    else -> Mdi.Run
                }
            }

            UserActivityState.USER_ACTIVITY_PASSIVE -> Mdi.HumanHandsdown
            UserActivityState.USER_ACTIVITY_ASLEEP -> Mdi.Sleep
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
                    basicSensor,
                    dataPoints[lastIndex].value,
                    basicSensor.statelessIcon,
                    mapOf(),
                )
            }
        }
    }
}
