package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking

class HealthConnectSensorManager : SensorManager {
    companion object {
        private const val TAG = "HealthConnectSM"

        fun getPermissionResultContract(context: Context): ActivityResultContract<Set<String>, Set<String>>? =
            PermissionController.createRequestPermissionResultContract(context.packageName)

        val activeCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_active_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_active_calories_burned,
            commonR.string.sensor_description_active_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val heartRate = SensorManager.BasicSensor(
            id = "health_connect_heart_rate",
            type = "sensor",
            commonR.string.sensor_name_heart_rate,
            commonR.string.sensor_description_health_connect_heart_rate,
            "mdi:heart-pulse",
            unitOfMeasurement = "bpm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val bodyFat = SensorManager.BasicSensor(
            id = "health_connect_body_fat",
            type = "sensor",
            commonR.string.basic_sensor_name_body_fat,
            commonR.string.sensor_description_body_fat,
            "mdi:scale-bathroom",
            unitOfMeasurement = "%",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val distance = SensorManager.BasicSensor(
            id = "health_connect_distance",
            type = "sensor",
            commonR.string.basic_sensor_name_distance,
            commonR.string.sensor_description_distance,
            "mdi:map-marker-distance",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val elevationGained = SensorManager.BasicSensor(
            id = "health_connect_elevation_gained",
            type = "sensor",
            commonR.string.basic_sensor_name_elevation_gained,
            commonR.string.sensor_description_elevation_gained,
            "mdi:elevation-rise",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val floorsClimbed = SensorManager.BasicSensor(
            id = "health_connect_floors_climbed",
            type = "sensor",
            commonR.string.basic_sensor_name_floors_climbed,
            commonR.string.sensor_description_floors_climbed,
            "mdi:stairs",
            unitOfMeasurement = "floors",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val sleepDuration = SensorManager.BasicSensor(
            id = "health_connect_sleep_duration",
            type = "sensor",
            commonR.string.basic_sensor_name_sleep_duration,
            commonR.string.sensor_description_sleep_duration,
            "mdi:sleep",
            deviceClass = "duration",
            unitOfMeasurement = "min",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val steps = SensorManager.BasicSensor(
            id = "health_connect_steps",
            type = "sensor",
            commonR.string.basic_sensor_name_steps,
            commonR.string.sensor_description_steps,
            "mdi:walk",
            unitOfMeasurement = "steps",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val totalCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_total_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_total_calories_burned,
            commonR.string.sensor_description_total_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val weight = SensorManager.BasicSensor(
            id = "health_connect_weight",
            type = "sensor",
            commonR.string.basic_sensor_name_weight,
            commonR.string.sensor_description_weight,
            "mdi:scale-bathroom",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            deviceClass = "weight"
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_health_connect

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == activeCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            (sensorId == bodyFat.id) -> arrayOf(HealthPermission.getReadPermission(BodyFatRecord::class))
            (sensorId == distance.id) -> arrayOf(HealthPermission.getReadPermission(DistanceRecord::class))
            (sensorId == elevationGained.id) -> arrayOf(HealthPermission.getReadPermission(ElevationGainedRecord::class))
            (sensorId == floorsClimbed.id) -> arrayOf(HealthPermission.getReadPermission(FloorsClimbedRecord::class))
            (sensorId == heartRate.id) -> arrayOf(HealthPermission.getReadPermission(HeartRateRecord::class))
            (sensorId == sleepDuration.id) -> arrayOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
            (sensorId == steps.id) -> arrayOf(HealthPermission.getReadPermission(StepsRecord::class))
            (sensorId == totalCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            (sensorId == weight.id) -> arrayOf(HealthPermission.getReadPermission(WeightRecord::class))
            else -> arrayOf()
        }
    }

    override fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, weight)) {
            updateWeightSensor(context)
        }
        if (isEnabled(context, activeCaloriesBurned)) {
            updateActiveCaloriesBurnedSensor(context)
        }
        if (isEnabled(context, totalCaloriesBurned)) {
            updateTotalCaloriesBurnedSensor(context)
        }
        if (isEnabled(context, heartRate)) {
            updateHeartRateSensor(context)
        }
        if (isEnabled(context, bodyFat)) {
            updateBodyFatSensor(context)
        }
        if (isEnabled(context, distance)) {
            updateDistanceSensor(context)
        }
        if (isEnabled(context, elevationGained)) {
            updateElevationGainedSensor(context)
        }
        if (isEnabled(context, floorsClimbed)) {
            updateFloorsClimbedSensor(context)
        }
        if (isEnabled(context, sleepDuration)) {
            updateSleepDurationSensor(context)
        }
        if (isEnabled(context, steps)) {
            updateStepsSensor(context)
        }
    }

    private fun updateTotalCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val totalCaloriesBurnedRequest = runBlocking {
            healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                        LocalDateTime.of(LocalDate.now(), LocalTime.now())
                    )
                )
            )
        }
        totalCaloriesBurnedRequest[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { energy ->
            onSensorUpdated(
                context,
                totalCaloriesBurned,
                BigDecimal(energy.inKilocalories).setScale(2, RoundingMode.HALF_EVEN),
                totalCaloriesBurned.statelessIcon,
                attributes = mapOf(
                    "endTime" to Instant.now(),
                    "sources" to totalCaloriesBurnedRequest.dataOrigins.map { it.packageName }
                )
            )
        }
    }

    private fun updateWeightSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val weightRequest = ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(weightRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            weight,
            BigDecimal(response.records.last().weight.inGrams).setScale(2, RoundingMode.HALF_EVEN),
            weight.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateActiveCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val activeCaloriesBurnedRequest = ReadRecordsRequest(
            recordType = ActiveCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()

            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(activeCaloriesBurnedRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            activeCaloriesBurned,
            BigDecimal(response.records.last().energy.inKilocalories).setScale(2, RoundingMode.HALF_EVEN),
            activeCaloriesBurned.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateHeartRateSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val heartRateRequest = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(heartRateRequest) }
        if (response.records.isEmpty() || response.records.last().samples.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            heartRate,
            response.records.last().samples.last().beatsPerMinute,
            heartRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().samples.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateBodyFatSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bodyFatRequest = ReadRecordsRequest(
            recordType = BodyFatRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )

        val response = runBlocking { healthConnectClient.readRecords(bodyFatRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            bodyFat,
            BigDecimal(response.records.last().percentage.value).setScale(2, RoundingMode.HALF_EVEN),
            bodyFat.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateDistanceSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val distanceRequest = ReadRecordsRequest(
            recordType = DistanceRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(distanceRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            distance,
            response.records.last().distance.inMeters,
            distance.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateElevationGainedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val elevationGainedRequest = ReadRecordsRequest(
            recordType = ElevationGainedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(elevationGainedRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            elevationGained,
            response.records.last().elevation.inMeters,
            elevationGained.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateFloorsClimbedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val floorsClimbedRequest = ReadRecordsRequest(
            recordType = FloorsClimbedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(floorsClimbedRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            floorsClimbed,
            response.records.last().floors,
            floorsClimbed.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateSleepDurationSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val sleepRequest = runBlocking {
            healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                        LocalDateTime.of(LocalDate.now(), LocalTime.now())
                    )
                )
            )
        }
        sleepRequest[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { sleep ->
            onSensorUpdated(
                context,
                sleepDuration,
                sleep.toMinutes(),
                sleepDuration.statelessIcon,
                attributes = mapOf(
                    "endTime" to Instant.now(),
                    "sources" to sleepRequest.dataOrigins.map { it.packageName }
                )
            )
        }
    }

    private fun updateStepsSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val stepsRequest = runBlocking {
            healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                        LocalDateTime.of(LocalDate.now(), LocalTime.now())
                    )
                )
            )
        }
        stepsRequest[StepsRecord.COUNT_TOTAL]?.let { totalSteps ->
            onSensorUpdated(
                context,
                steps,
                totalSteps,
                steps.statelessIcon,
                attributes = mapOf(
                    "endTime" to Instant.now(),
                    "sources" to stepsRequest.dataOrigins.map { it.packageName }
                )
            )
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#health-connect-sensors"
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (hasSensor(context)) {
            listOf(
                weight, activeCaloriesBurned, totalCaloriesBurned, heartRate, bodyFat, distance,
                elevationGained, floorsClimbed, sleepDuration, steps
            )
        } else {
            emptyList()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override fun checkPermission(context: Context, sensorId: String): Boolean {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return false
        val result = runBlocking {
            healthConnectClient.permissionController.getGrantedPermissions().containsAll(requiredPermissions(sensorId).toSet())
        }
        return result
    }

    private fun getOrCreateHealthConnectClient(context: Context): HealthConnectClient? {
        return try {
            HealthConnectClient.getOrCreate(context.applicationContext)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create Health Connect client", e)
            null
        }
    }
}
