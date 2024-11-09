package io.homeassistant.companion.android.sensors

import android.content.Context
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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

        val bloodGlucose = SensorManager.BasicSensor(
            id = "health_connect_blood_glucose",
            type = "sensor",
            commonR.string.basic_sensor_name_blood_glucose,
            commonR.string.sensor_description_blood_glucose,
            "mdi:diabetes",
            unitOfMeasurement = "mg/dL",
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
            (sensorId == bloodGlucose.id) -> arrayOf(HealthPermission.getReadPermission(BloodGlucoseRecord::class))
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

    override suspend fun requestSensorUpdate(context: Context) {
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
        if (isEnabled(context, bloodGlucose)) {
            updateBloodGlucoseSensor(context)
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

    private suspend fun updateTotalCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val totalCaloriesBurnedRequest = healthConnectClient.aggregate(buildAggregationRequest(TotalCaloriesBurnedRecord.ENERGY_TOTAL))
        val energy = totalCaloriesBurnedRequest[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        onSensorUpdated(
            context,
            totalCaloriesBurned,
            BigDecimal(energy).setScale(2, RoundingMode.HALF_EVEN),
            totalCaloriesBurned.statelessIcon,
            attributes = buildAggregationAttributes(totalCaloriesBurnedRequest)
        )
    }

    private suspend fun updateWeightSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val weightRequest = buildReadRecordsRequest(WeightRecord::class) as ReadRecordsRequest<WeightRecord>
        val response = healthConnectClient.readRecords(weightRequest)
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

    private suspend fun updateActiveCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val activeCaloriesBurnedRequest = buildReadRecordsRequest(ActiveCaloriesBurnedRecord::class) as ReadRecordsRequest<ActiveCaloriesBurnedRecord>
        val response = healthConnectClient.readRecords(activeCaloriesBurnedRequest)
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

    private suspend fun updateHeartRateSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val heartRateRequest = buildReadRecordsRequest(HeartRateRecord::class) as ReadRecordsRequest<HeartRateRecord>
        val response = healthConnectClient.readRecords(heartRateRequest)
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

    private suspend fun updateBloodGlucoseSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bloodGlucoseRequest = buildReadRecordsRequest(BloodGlucoseRecord::class) as ReadRecordsRequest<BloodGlucoseRecord>
        val response = healthConnectClient.readRecords(bloodGlucoseRequest)
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            bloodGlucose,
            response.records.last().level.inMilligramsPerDeciliter,
            bloodGlucose.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "mealType" to getMealType(response.records.last().mealType),
                "relationToMeal" to getRelationToMeal(response.records.last().relationToMeal),
                "specimenSource" to getSpecimenSource(response.records.last().specimenSource),
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateBodyFatSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bodyFatRequest = buildReadRecordsRequest(BodyFatRecord::class) as ReadRecordsRequest<BodyFatRecord>
        val response = healthConnectClient.readRecords(bodyFatRequest)
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

    private suspend fun updateDistanceSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val distanceRequest = healthConnectClient.aggregate(buildAggregationRequest(DistanceRecord.DISTANCE_TOTAL))
        val distanceTotal = distanceRequest[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0
        onSensorUpdated(
            context,
            distance,
            distanceTotal,
            distance.statelessIcon,
            attributes = buildAggregationAttributes(distanceRequest)
        )
    }

    private suspend fun updateElevationGainedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val elevationGainedRequest = healthConnectClient.aggregate(buildAggregationRequest(ElevationGainedRecord.ELEVATION_GAINED_TOTAL))
        val elevationValue = elevationGainedRequest[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0
        onSensorUpdated(
            context,
            elevationGained,
            elevationValue,
            elevationGained.statelessIcon,
            attributes = buildAggregationAttributes(elevationGainedRequest)
        )
    }

    private suspend fun updateFloorsClimbedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val floorsClimbedRequest = healthConnectClient.aggregate(buildAggregationRequest(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL))
        val floors = floorsClimbedRequest[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0
        onSensorUpdated(
            context,
            floorsClimbed,
            floors,
            floorsClimbed.statelessIcon,
            attributes = buildAggregationAttributes(floorsClimbedRequest)
        )
    }

    private suspend fun updateSleepDurationSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val sleepRequest = buildReadRecordsRequest(SleepSessionRecord::class) as ReadRecordsRequest<SleepSessionRecord>
        val sleepRecords = healthConnectClient.readRecords(sleepRequest)
        if (sleepRecords.records.isEmpty()) {
            return
        }
        val lastSleepRecord = sleepRecords.records.last()
        val sleepRecordDuration = (lastSleepRecord.endTime.toEpochMilli() - lastSleepRecord.startTime.toEpochMilli())
            .toDuration(DurationUnit.MILLISECONDS)
            .inWholeMinutes
        onSensorUpdated(
            context,
            sleepDuration,
            sleepRecordDuration,
            sleepDuration.statelessIcon,
            attributes = mapOf(
                "endTime" to lastSleepRecord.endTime,
                "sources" to lastSleepRecord.metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateStepsSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val stepsRequest = healthConnectClient.aggregate(buildAggregationRequest(StepsRecord.COUNT_TOTAL))
        val totalSteps = stepsRequest[StepsRecord.COUNT_TOTAL] ?: 0
        onSensorUpdated(
            context,
            steps,
            totalSteps,
            steps.statelessIcon,
            attributes = buildAggregationAttributes(stepsRequest)
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#health-connect-sensors"
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (hasSensor(context)) {
            listOf(
                weight, activeCaloriesBurned, totalCaloriesBurned, heartRate, bodyFat, distance,
                elevationGained, floorsClimbed, sleepDuration, steps, bloodGlucose
            )
        } else {
            emptyList()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun checkPermission(context: Context, sensorId: String): Boolean {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return false
        val result = healthConnectClient.permissionController.getGrantedPermissions().containsAll(requiredPermissions(sensorId).toSet())
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

    private fun buildAggregationRequest(metric: AggregateMetric<*>): AggregateRequest {
        return AggregateRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(
                LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                LocalDateTime.of(LocalDate.now(), LocalTime.now())
            )
        )
    }

    private fun buildReadRecordsRequest(request: KClass<out Record>): ReadRecordsRequest<out Record> {
        return ReadRecordsRequest(
            recordType = request,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
    }

    private fun buildAggregationAttributes(result: AggregationResult): Map<String, Any> {
        return mapOf(
            "endTime" to Instant.now(),
            "sources" to result.dataOrigins.map { it.packageName }
        )
    }

    private fun getRelationToMeal(relation: Int): String {
        return when (relation) {
            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before_meal"
            BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "general"
            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after_meal"
            BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getSpecimenSource(source: Int): String {
        return when (source) {
            BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "capillary_blood"
            BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "interstitial_fluid"
            BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "plasma"
            BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "serum"
            BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "tears"
            BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN -> STATE_UNKNOWN
            BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "whole_blood"
            else -> STATE_UNKNOWN
        }
    }

    private fun getMealType(mealType: Int): String {
        return when (mealType) {
            MealType.MEAL_TYPE_BREAKFAST -> "breakfast"
            MealType.MEAL_TYPE_LUNCH -> "lunch"
            MealType.MEAL_TYPE_DINNER -> "dinner"
            MealType.MEAL_TYPE_SNACK -> "snack"
            MealType.MEAL_TYPE_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }
}
