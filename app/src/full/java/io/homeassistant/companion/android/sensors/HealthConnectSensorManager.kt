package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
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

        fun getPermissionIntent(): Intent? = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

        fun getPermissionResultContract(): ActivityResultContract<Set<String>, Set<String>>? =
            PermissionController.createRequestPermissionResultContract()

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

        val basalMetabolicRate = SensorManager.BasicSensor(
            id = "health_connect_basal_metabolic_rate",
            type = "sensor",
            commonR.string.basic_sensor_name_basal_metabolic_rate,
            commonR.string.sensor_description_basal_metabolic_rate,
            "mdi:fire",
            unitOfMeasurement = "kcal/day",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val bloodGlucose = SensorManager.BasicSensor(
            id = "health_connect_blood_glucose",
            type = "sensor",
            commonR.string.basic_sensor_name_blood_glucose,
            commonR.string.sensor_description_blood_glucose,
            "mdi:diabetes",
            deviceClass = "blood_glucose_concentration",
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

        val bodyTemperature = SensorManager.BasicSensor(
            id = "health_connect_body_temperature",
            type = "sensor",
            commonR.string.basic_sensor_name_body_temperature,
            commonR.string.sensor_description_body_temperature,
            "mdi:thermometer",
            deviceClass = "temperature",
            unitOfMeasurement = "°C",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val boneMass = SensorManager.BasicSensor(
            id = "health_connect_bone_mass",
            type = "sensor",
            commonR.string.basic_sensor_name_bone_mass,
            commonR.string.sensor_description_bone_mass,
            "mdi:bone",
            deviceClass = "weight",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val diastolicBloodPressure = SensorManager.BasicSensor(
            id = "health_connect_diastolic_blood_pressure",
            type = "sensor",
            commonR.string.basic_sensor_name_diastolic_blood_pressure,
            commonR.string.sensor_description_diastolic_blood_pressure,
            "mdi:heart-pulse",
            deviceClass = "pressure",
            unitOfMeasurement = "mmHg",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val distance = SensorManager.BasicSensor(
            id = "health_connect_distance",
            type = "sensor",
            commonR.string.sensor_name_daily_distance,
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
            commonR.string.basic_sensor_name_daily_elevation_gained,
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
            commonR.string.sensor_name_daily_floors,
            commonR.string.sensor_description_floors_climbed,
            "mdi:stairs",
            unitOfMeasurement = "floors",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
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

        val height = SensorManager.BasicSensor(
            id = "health_connect_height",
            type = "sensor",
            commonR.string.basic_sensor_name_height,
            commonR.string.sensor_description_height,
            "mdi:human-male-height",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val leanBodyMass = SensorManager.BasicSensor(
            id = "health_connect_lean_body_mass",
            type = "sensor",
            commonR.string.basic_sensor_name_lean_body_mass,
            commonR.string.sensor_description_lean_body_mass,
            "mdi:scale-bathroom",
            deviceClass = "weight",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val oxygenSaturation = SensorManager.BasicSensor(
            id = "health_connect_oxygen_saturation",
            type = "sensor",
            commonR.string.basic_sensor_name_oxygen_saturation,
            commonR.string.sensor_description_oxygen_saturation,
            "mdi:sleep",
            unitOfMeasurement = "%",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val respiratoryRate = SensorManager.BasicSensor(
            id = "health_connect_respiratory_rate",
            type = "sensor",
            commonR.string.basic_sensor_name_respiratory_rate,
            commonR.string.sensor_description_respiratory_rate,
            "mdi:account-voice",
            unitOfMeasurement = "bpm",
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
            commonR.string.sensor_name_daily_steps,
            commonR.string.sensor_description_steps,
            "mdi:walk",
            unitOfMeasurement = "steps",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val systolicBloodPressure = SensorManager.BasicSensor(
            id = "health_connect_systolic_blood_pressure",
            type = "sensor",
            commonR.string.basic_sensor_name_systolic_blood_pressure,
            commonR.string.sensor_description_systolic_blood_pressure,
            "mdi:heart-pulse",
            deviceClass = "pressure",
            unitOfMeasurement = "mmHg",
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

        val vo2Max = SensorManager.BasicSensor(
            id = "health_connect_vo2_max",
            type = "sensor",
            commonR.string.basic_sensor_name_vo2_max,
            commonR.string.sensor_description_vo2_max,
            "mdi:heart",
            unitOfMeasurement = "mL/kg/min",
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
        return try {
            when {
                (sensorId == activeCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
                (sensorId == basalMetabolicRate.id) -> arrayOf(HealthPermission.getReadPermission(BasalMetabolicRateRecord::class))
                (sensorId == bloodGlucose.id) -> arrayOf(HealthPermission.getReadPermission(BloodGlucoseRecord::class))
                (sensorId == bodyFat.id) -> arrayOf(HealthPermission.getReadPermission(BodyFatRecord::class))
                (sensorId == bodyTemperature.id) -> arrayOf(HealthPermission.getReadPermission(BodyTemperatureRecord::class))
                (sensorId == boneMass.id) -> arrayOf(HealthPermission.getReadPermission(BoneMassRecord::class))
                (sensorId == diastolicBloodPressure.id) -> arrayOf(HealthPermission.getReadPermission(BloodPressureRecord::class))
                (sensorId == distance.id) -> arrayOf(HealthPermission.getReadPermission(DistanceRecord::class))
                (sensorId == elevationGained.id) -> arrayOf(HealthPermission.getReadPermission(ElevationGainedRecord::class))
                (sensorId == floorsClimbed.id) -> arrayOf(HealthPermission.getReadPermission(FloorsClimbedRecord::class))
                (sensorId == heartRate.id) -> arrayOf(HealthPermission.getReadPermission(HeartRateRecord::class))
                (sensorId == height.id) -> arrayOf(HealthPermission.getReadPermission(HeightRecord::class))
                (sensorId == leanBodyMass.id) -> arrayOf(HealthPermission.getReadPermission(LeanBodyMassRecord::class))
                (sensorId == oxygenSaturation.id) -> arrayOf(HealthPermission.getReadPermission(OxygenSaturationRecord::class))
                (sensorId == respiratoryRate.id) -> arrayOf(HealthPermission.getReadPermission(RespiratoryRateRecord::class))
                (sensorId == sleepDuration.id) -> arrayOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
                (sensorId == steps.id) -> arrayOf(HealthPermission.getReadPermission(StepsRecord::class))
                (sensorId == systolicBloodPressure.id) -> arrayOf(HealthPermission.getReadPermission(BloodPressureRecord::class))
                (sensorId == totalCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
                (sensorId == vo2Max.id) -> arrayOf(HealthPermission.getReadPermission(Vo2MaxRecord::class))
                (sensorId == weight.id) -> arrayOf(HealthPermission.getReadPermission(WeightRecord::class))
                else -> arrayOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get required permissions", e)
            arrayOf()
        }
    }

    override suspend fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, activeCaloriesBurned)) {
            updateActiveCaloriesBurnedSensor(context)
        }
        if (isEnabled(context, basalMetabolicRate)) {
            updateBasalMetabolicRateSensor(context)
        }
        if (isEnabled(context, bloodGlucose)) {
            updateBloodGlucoseSensor(context)
        }
        if (isEnabled(context, bodyFat)) {
            updateBodyFatSensor(context)
        }
        if (isEnabled(context, bodyTemperature)) {
            updateBodyTemperatureSensor(context)
        }
        if (isEnabled(context, boneMass)) {
            updateBoneMassSensor(context)
        }
        if (isEnabled(context, diastolicBloodPressure)) {
            updateBloodPressureSensors(context, true)
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
        if (isEnabled(context, heartRate)) {
            updateHeartRateSensor(context)
        }
        if (isEnabled(context, height)) {
            updateHeightSensor(context)
        }
        if (isEnabled(context, leanBodyMass)) {
            updateLeanBodyMassSensor(context)
        }
        if (isEnabled(context, oxygenSaturation)) {
            updateOxygenSaturationSensor(context)
        }
        if (isEnabled(context, respiratoryRate)) {
            updateRespiratoryRateSensor(context)
        }
        if (isEnabled(context, sleepDuration)) {
            updateSleepDurationSensor(context)
        }
        if (isEnabled(context, steps)) {
            updateStepsSensor(context)
        }
        if (isEnabled(context, systolicBloodPressure)) {
            updateBloodPressureSensors(context, false)
        }
        if (isEnabled(context, totalCaloriesBurned)) {
            updateTotalCaloriesBurnedSensor(context)
        }
        if (isEnabled(context, vo2Max)) {
            updateVo2MaxSensor(context)
        }
        if (isEnabled(context, weight)) {
            updateWeightSensor(context)
        }
    }

    private suspend fun updateActiveCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val activeCaloriesBurnedRequest = buildReadRecordsRequest(ActiveCaloriesBurnedRecord::class) as ReadRecordsRequest<ActiveCaloriesBurnedRecord>
        val response = healthConnectClient.readRecordsOrNull(activeCaloriesBurnedRequest)
        if (response == null || response.records.isEmpty()) {
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

    private suspend fun updateBasalMetabolicRateSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val basalMetabolicRateRequest = buildReadRecordsRequest(BasalMetabolicRateRecord::class) as ReadRecordsRequest<BasalMetabolicRateRecord>
        val response = healthConnectClient.readRecordsOrNull(basalMetabolicRateRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            basalMetabolicRate,
            response.records.last().basalMetabolicRate.inKilocaloriesPerDay,
            basalMetabolicRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateBloodGlucoseSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bloodGlucoseRequest = buildReadRecordsRequest(BloodGlucoseRecord::class) as ReadRecordsRequest<BloodGlucoseRecord>
        val response = healthConnectClient.readRecordsOrNull(bloodGlucoseRequest)
        if (response == null || response.records.isEmpty()) {
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

    private suspend fun updateBloodPressureSensors(context: Context, isDiastolic: Boolean) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bloodPressureRequest = buildReadRecordsRequest(BloodPressureRecord::class) as ReadRecordsRequest<BloodPressureRecord>
        val response = healthConnectClient.readRecordsOrNull(bloodPressureRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            if (isDiastolic) diastolicBloodPressure else systolicBloodPressure,
            if (isDiastolic) response.records.last().diastolic.inMillimetersOfMercury else response.records.last().systolic.inMillimetersOfMercury,
            if (isDiastolic) diastolicBloodPressure.statelessIcon else systolicBloodPressure.statelessIcon,
            attributes = mapOf(
                "bodyPosition" to getBloodPressureBodyPosition(response.records.last().bodyPosition),
                "date" to response.records.last().time,
                "measurementLocation" to getBloodPressureMeasurementLocation(response.records.last().measurementLocation),
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateBodyFatSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bodyFatRequest = buildReadRecordsRequest(BodyFatRecord::class) as ReadRecordsRequest<BodyFatRecord>
        val response = healthConnectClient.readRecordsOrNull(bodyFatRequest)
        if (response == null || response.records.isEmpty()) {
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

    private suspend fun updateBodyTemperatureSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val bodyTemperatureRequest = buildReadRecordsRequest(BodyTemperatureRecord::class) as ReadRecordsRequest<BodyTemperatureRecord>
        val response = healthConnectClient.readRecordsOrNull(bodyTemperatureRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            bodyTemperature,
            response.records.last().temperature.inCelsius,
            bodyTemperature.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "measurementLocation" to getBodyTemperatureMeasurementLocation(response.records.last().measurementLocation),
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateBoneMassSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val boneMassRequest = buildReadRecordsRequest(BoneMassRecord::class) as ReadRecordsRequest<BoneMassRecord>
        val response = healthConnectClient.readRecordsOrNull(boneMassRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            boneMass,
            response.records.last().mass.inGrams,
            boneMass.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateDistanceSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val distanceRequest = healthConnectClient.aggregateOrNull(buildAggregationRequest(DistanceRecord.DISTANCE_TOTAL)) ?: return
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
        val elevationGainedRequest = healthConnectClient.aggregateOrNull(buildAggregationRequest(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)) ?: return
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
        val floorsClimbedRequest = healthConnectClient.aggregateOrNull(buildAggregationRequest(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)) ?: return
        val floors = floorsClimbedRequest[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0
        onSensorUpdated(
            context,
            floorsClimbed,
            floors,
            floorsClimbed.statelessIcon,
            attributes = buildAggregationAttributes(floorsClimbedRequest)
        )
    }

    private suspend fun updateHeartRateSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val heartRateRequest = buildReadRecordsRequest(HeartRateRecord::class) as ReadRecordsRequest<HeartRateRecord>
        val response = healthConnectClient.readRecordsOrNull(heartRateRequest)
        if (response == null || response.records.isEmpty() || response.records.last().samples.isEmpty()) {
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

    private suspend fun updateHeightSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val heightRequest = buildReadRecordsRequest(HeightRecord::class) as ReadRecordsRequest<HeightRecord>
        val response = healthConnectClient.readRecordsOrNull(heightRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            height,
            response.records.last().height.inMeters,
            height.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateLeanBodyMassSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val leanBodyMassRequest = buildReadRecordsRequest(LeanBodyMassRecord::class) as ReadRecordsRequest<LeanBodyMassRecord>
        val response = healthConnectClient.readRecordsOrNull(leanBodyMassRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            leanBodyMass,
            response.records.last().mass.inGrams,
            leanBodyMass.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateOxygenSaturationSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val oxygenSaturationRequest = buildReadRecordsRequest(OxygenSaturationRecord::class) as ReadRecordsRequest<OxygenSaturationRecord>
        val response = healthConnectClient.readRecordsOrNull(oxygenSaturationRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            oxygenSaturation,
            response.records.last().percentage.value,
            oxygenSaturation.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateRespiratoryRateSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val respiratoryRateRequest = buildReadRecordsRequest(RespiratoryRateRecord::class) as ReadRecordsRequest<RespiratoryRateRecord>
        val response = healthConnectClient.readRecordsOrNull(respiratoryRateRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            respiratoryRate,
            response.records.last().rate,
            respiratoryRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateSleepDurationSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val sleepRequest = buildReadRecordsRequest(SleepSessionRecord::class) as ReadRecordsRequest<SleepSessionRecord>
        val sleepRecords = healthConnectClient.readRecordsOrNull(sleepRequest)
        if (sleepRecords == null || sleepRecords.records.isEmpty()) {
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
        val stepsRequest = healthConnectClient.aggregateOrNull(buildAggregationRequest(StepsRecord.COUNT_TOTAL)) ?: return
        val totalSteps = stepsRequest[StepsRecord.COUNT_TOTAL] ?: 0
        onSensorUpdated(
            context,
            steps,
            totalSteps,
            steps.statelessIcon,
            attributes = buildAggregationAttributes(stepsRequest)
        )
    }

    private suspend fun updateTotalCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val totalCaloriesBurnedRequest = healthConnectClient.aggregateOrNull(buildAggregationRequest(TotalCaloriesBurnedRecord.ENERGY_TOTAL)) ?: return
        val energy = totalCaloriesBurnedRequest[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        onSensorUpdated(
            context,
            totalCaloriesBurned,
            BigDecimal(energy).setScale(2, RoundingMode.HALF_EVEN),
            totalCaloriesBurned.statelessIcon,
            attributes = buildAggregationAttributes(totalCaloriesBurnedRequest)
        )
    }

    private suspend fun updateVo2MaxSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val vo2MaxRequest = buildReadRecordsRequest(Vo2MaxRecord::class) as ReadRecordsRequest<Vo2MaxRecord>
        val response = healthConnectClient.readRecordsOrNull(vo2MaxRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            vo2Max,
            response.records.last().vo2MillilitersPerMinuteKilogram,
            vo2Max.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "measurementMethod" to getMeasurementMethod(response.records.last().measurementMethod),
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private suspend fun updateWeightSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val weightRequest = buildReadRecordsRequest(WeightRecord::class) as ReadRecordsRequest<WeightRecord>
        val response = healthConnectClient.readRecordsOrNull(weightRequest)
        if (response == null || response.records.isEmpty()) {
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

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#health-connect-sensors"
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (hasSensor(context)) {
            listOf(
                activeCaloriesBurned,
                basalMetabolicRate,
                bloodGlucose,
                bodyFat,
                bodyTemperature,
                boneMass,
                diastolicBloodPressure,
                distance,
                elevationGained,
                floorsClimbed,
                heartRate,
                height,
                leanBodyMass,
                oxygenSaturation,
                respiratoryRate,
                sleepDuration,
                steps,
                systolicBloodPressure,
                totalCaloriesBurned,
                vo2Max,
                weight
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
        return try {
            healthConnectClient.permissionController.getGrantedPermissions().containsAll(requiredPermissions(sensorId).toSet())
        } catch (e: Exception) {
            Log.e(TAG, "Unable to check permissions", e)
            true // default to true as we still need to check sensor enabled state
        }
    }

    private fun getOrCreateHealthConnectClient(context: Context): HealthConnectClient? {
        return try {
            HealthConnectClient.getOrCreate(context.applicationContext)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create Health Connect client", e)
            null
        }
    }

    private suspend fun <T : Record> HealthConnectClient.readRecordsOrNull(request: ReadRecordsRequest<T>): ReadRecordsResponse<T>? = try {
        readRecords(request)
    } catch (e: Exception) {
        Log.e(TAG, "Could not read records", e)
        null
    }

    private suspend fun HealthConnectClient.aggregateOrNull(request: AggregateRequest): AggregationResult? = try {
        aggregate(request)
    } catch (e: Exception) {
        Log.e(TAG, "Could not aggregate", e)
        null
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

    private fun getBloodPressureBodyPosition(position: Int): String {
        return when (position) {
            BloodPressureRecord.BODY_POSITION_LYING_DOWN -> "lying_down"
            BloodPressureRecord.BODY_POSITION_RECLINING -> "reclining"
            BloodPressureRecord.BODY_POSITION_SITTING_DOWN -> "sitting_down"
            BloodPressureRecord.BODY_POSITION_STANDING_UP -> "standing_up"
            BloodPressureRecord.BODY_POSITION_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getBloodPressureMeasurementLocation(location: Int): String {
        return when (location) {
            BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST -> "left_wrist"
            BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM -> "left_upper_arm"
            BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST -> "right_wrist"
            BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM -> "right_upper_arm"
            BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getMeasurementMethod(method: Int): String {
        return when (method) {
            Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST -> "cooper_test"
            Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO -> "heart_rate_ratio"
            Vo2MaxRecord.MEASUREMENT_METHOD_METABOLIC_CART -> "metabolic_cart"
            Vo2MaxRecord.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST -> "multistage_fitness_test"
            Vo2MaxRecord.MEASUREMENT_METHOD_OTHER -> "other"
            Vo2MaxRecord.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST -> "rockport_fitness_test"
            else -> STATE_UNKNOWN
        }
    }

    private fun getBodyTemperatureMeasurementLocation(location: Int): String {
        return when (location) {
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT -> "armpit"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR -> "ear"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FINGER -> "finger"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FOREHEAD -> "forehead"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH -> "mouth"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_RECTUM -> "rectum"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TEMPORAL_ARTERY -> "temporal_artery"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TOE -> "toe"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN -> STATE_UNKNOWN
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_VAGINA -> "vagina"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST -> "wrist"
            else -> STATE_UNKNOWN
        }
    }
}
